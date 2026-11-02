package com.x.bpm;

import com.x.bpm.businessrecord.adapter.persistence.BusinessRecordEntity;
import com.x.bpm.businessrecord.adapter.persistence.RecordAttachmentEntity;
import com.x.bpm.businessrecord.adapter.persistence.RecordCommentEntity;
import com.x.bpm.form.adapter.persistence.FormDefinitionEntity;
import com.x.bpm.form.adapter.persistence.FormVersionEntity;
import com.x.bpm.identity.adapter.persistence.LoginGrantEntity;
import com.x.bpm.identity.adapter.persistence.RefreshCredentialEntity;
import com.x.bpm.identity.adapter.persistence.RoleEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.identity.adapter.persistence.UserRoleEntity;
import com.x.bpm.organization.adapter.persistence.DepartmentEntity;
import com.x.bpm.organization.adapter.persistence.DepartmentMemberEntity;
import com.x.bpm.process.adapter.persistence.ProcessDefinitionEntity;
import com.x.bpm.process.adapter.persistence.ProcessVersionEntity;
import com.x.bpm.support.IsolatedPostgresTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses the existing isolated PostgreSQL harness; never executes against dev. */
@IsolatedPostgresTest
@Transactional
class BusinessModelPersistenceTest {
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsBusinessTablesAlongsideEngineTables() {
        var businessTables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'business' and table_type = 'BASE TABLE'
                """, String.class);
        assertThat(businessTables).contains("users", "roles", "permissions", "user_roles",
                "role_permissions", "login_grants", "refresh_credentials", "departments",
                "department_members", "positions", "form_definitions", "form_versions",
                "process_definitions", "process_versions", "business_records",
                "record_attachments", "record_comments", "record_access_grants",
                "task_action_logs", "audit_events", "notifications")
                .doesNotContain("act_ru_task");

        var flowableTables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'flowable' and table_type = 'BASE TABLE'
                """, String.class);
        assertThat(flowableTables).contains("act_ru_task")
                .doesNotContain("users", "business_records");
    }

    @Test
    void roundTripsPinnedVersionsAndNestedJsonAsPostgresJsonb() {
        var record = draftRecord();
        record.setFormData(Map.of("amount", 50000000,
                "details", Map.of("purpose", "Thiết bị văn phòng")));
        entityManager.flush();
        var id = record.getId();
        var processVersionId = record.getProcessVersion().getId();
        var formVersionId = record.getFormVersion().getId();
        entityManager.clear();

        var stored = entityManager.find(BusinessRecordEntity.class, id);
        assertThat(stored.getProcessVersion().getId()).isEqualTo(processVersionId);
        assertThat(stored.getFormVersion().getId()).isEqualTo(formVersionId);
        assertThat(stored.getFormData()).containsEntry("amount", 50000000)
                .containsEntry("details", Map.of("purpose", "Thiết bị văn phòng"));
        assertThat(stored.getEngineInstanceId()).isNull();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("""
                select pg_typeof(form_data)::text from business_records where id = ?
                """, String.class, id)).isEqualTo("jsonb");
    }

    @Test
    void rejectsCaseInsensitiveDuplicateUsernames() {
        var first = user("  Employee  ");
        entityManager.flush();
        assertThat(first.getUsername()).isEqualTo("Employee");
        assertThat(first.getNormalizedUsername()).isEqualTo("employee");
        assertThat(first.isMustChangePassword()).isTrue();
        user("EMPLOYEE");
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsMissingForeignKeyTargets() {
        var role = new RoleEntity();
        role.setCode("EMPLOYEE");
        role.setName("Nhân viên");
        entityManager.persist(role);
        var membership = new UserRoleEntity();
        membership.setRole(role);
        membership.setUser(entityManager.getReference(UserEntity.class, UUID.randomUUID()));
        entityManager.persist(membership);
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsDuplicateVersionNumbersWithinOneDefinition() {
        var first = draftRecord().getFormVersion();
        entityManager.flush();
        var duplicate = new FormVersionEntity();
        duplicate.setFormDefinition(first.getFormDefinition());
        duplicate.setVersionNumber(first.getVersionNumber());
        duplicate.setCreatedBy(first.getCreatedBy());
        entityManager.persist(duplicate);
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void enforcesOnePrimaryDepartmentPerUserInMvp() {
        var actor = user("member");
        var department = new DepartmentEntity();
        department.setCode("IT");
        department.setName("Công nghệ thông tin");
        entityManager.persist(department);
        var first = new DepartmentMemberEntity();
        first.setUser(actor);
        first.setDepartment(department);
        entityManager.persist(first);
        entityManager.flush();

        var second = new DepartmentMemberEntity();
        second.setUser(actor);
        second.setDepartment(department);
        entityManager.persist(second);
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsStaleRevisionsWithoutChangingSecurityVersion() {
        var stale = user("revision-user");
        entityManager.flush();
        entityManager.detach(stale);
        var current = entityManager.find(UserEntity.class, stale.getId());
        current.setDisplayName("Updated display name");
        entityManager.flush();
        assertThat(current.getRevision()).isGreaterThan(stale.getRevision());
        assertThat(current.getSecurityVersion()).isEqualTo(stale.getSecurityVersion());
        entityManager.clear();
        stale.setDisplayName("Stale write");
        assertThatThrownBy(() -> {
            entityManager.merge(stale);
            entityManager.flush();
        }).isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void storesRefreshHashesAndRejectsRawRefreshTokens() {
        var grant = new LoginGrantEntity();
        grant.setUser(user("refresh-user"));
        grant.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        entityManager.persist(grant);
        var credential = new RefreshCredentialEntity();
        credential.setGrant(grant);
        credential.setTokenHash("a".repeat(64));
        credential.setExpiresAt(grant.getExpiresAt());
        entityManager.persist(credential);
        entityManager.flush();
        assertThat(jdbc.queryForObject("select token_hash from refresh_credentials where id = ?",
                String.class, credential.getId())).isEqualTo("a".repeat(64));

        var raw = new RefreshCredentialEntity();
        raw.setGrant(grant);
        raw.setTokenHash("raw-refresh-token-must-not-be-stored");
        raw.setExpiresAt(grant.getExpiresAt());
        entityManager.persist(raw);
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsSubmittedRecordsWithoutAnEngineInstance() {
        var record = draftRecord();
        entityManager.flush();
        record.setStatus(BusinessRecordEntity.Status.IN_PROGRESS);
        record.setSubmittedAt(Instant.now());
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void rejectsAttachmentsLargerThanTwentyMiB() {
        var attachment = new RecordAttachmentEntity();
        attachment.setBucket("bpm-private");
        attachment.setObjectKey(UUID.randomUUID().toString());
        attachment.setOriginalFilename("invoice.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(20971521L);
        attachment.setUploadedBy(user("uploader"));
        entityManager.persist(attachment);
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void doesNotUpdateExistingCommentsThroughHibernate() {
        var record = draftRecord();
        var comment = new RecordCommentEntity();
        comment.setBusinessRecord(record);
        comment.setAuthor(record.getRequester());
        comment.setContent("Original comment");
        entityManager.persist(comment);
        entityManager.flush();
        comment.setContent("Attempted edit");
        entityManager.flush();
        var id = comment.getId();
        entityManager.clear();
        assertThat(entityManager.find(RecordCommentEntity.class, id).getContent())
                .isEqualTo("Original comment");
    }

    private UserEntity user(String username) {
        var user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName("Test employee");
        // Inert persistence fixture; auth use cases must use PasswordEncoder.
        user.setPasswordHash("not-a-login-credential");
        entityManager.persist(user);
        return user;
    }

    private BusinessRecordEntity draftRecord() {
        var owner = user("owner");
        var form = new FormDefinitionEntity();
        form.setKey("purchase");
        form.setName("Đề xuất mua sắm");
        form.setOwner(owner);
        entityManager.persist(form);
        var formVersion = new FormVersionEntity();
        formVersion.setFormDefinition(form);
        formVersion.setVersionNumber(1);
        formVersion.setCreatedBy(owner);
        entityManager.persist(formVersion);

        var process = new ProcessDefinitionEntity();
        process.setKey("purchase");
        process.setName("Mua sắm");
        process.setOwner(owner);
        entityManager.persist(process);
        var processVersion = new ProcessVersionEntity();
        processVersion.setProcessDefinition(process);
        processVersion.setVersionNumber(1);
        processVersion.setCreatedBy(owner);
        processVersion.setFormVersion(formVersion);
        entityManager.persist(processVersion);

        var record = new BusinessRecordEntity();
        record.setBusinessKey("PUR-0001");
        record.setProcessDefinition(process);
        record.setProcessVersion(processVersion);
        record.setFormVersion(formVersion);
        record.setRequester(owner);
        record.setTitle("Đề xuất thiết bị");
        entityManager.persist(record);
        return record;
    }
}
