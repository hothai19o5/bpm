package com.x.bpm.process.adapter.persistence;

import com.x.bpm.form.adapter.persistence.FormVersionEntity;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lưu phiên bản quy trình, cấu hình BPMN, biểu mẫu và liên kết tới engine.
 */
@Getter
@Setter
@Entity
@Table(name = "process_versions",
        uniqueConstraints = {@UniqueConstraint(name = "uk_process_versions_1", columnNames = {"process_definition_id", "version_number"}),
                @UniqueConstraint(name = "uk_process_versions_2", columnNames = {"engine_definition_id"})},
        indexes = {@Index(name = "ix_process_versions_1", columnList = "form_version_id"),
                @Index(name = "ix_process_versions_2", columnList = "created_by"),
                @Index(name = "ix_process_versions_3", columnList = "published_by")})
@Check(constraints = "version_number > 0 and (engine_version is null or engine_version > 0) and (status = 'DRAFT' or (bpmn_xml is not null and form_version_id is not null and engine_deployment_id is not null and engine_definition_id is not null and engine_version is not null and artifact_checksum is not null and published_at is not null and published_by is not null))")
public class ProcessVersionEntity extends MutableEntity {
    @Transient @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private PublishedSnapshot publishedSnapshot;
    @Transient @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private Status snapshotStatus;

    @PostLoad @PostPersist @PostUpdate
    void snapshotPublished() {
        snapshotStatus=status;
        publishedSnapshot=status==Status.DRAFT?null:publication();
    }
    @PreUpdate
    void requirePublishedImmutable() {
        if(publishedSnapshot!=null&&(status==Status.DRAFT||(snapshotStatus==Status.RETIRED&&status!=Status.RETIRED)
                ||!publishedSnapshot.equals(publication())))
            throw new IllegalStateException("Published process artifact and engine mapping are immutable");
    }
    private PublishedSnapshot publication() {
        return new PublishedSnapshot(processDefinition.getId(),versionNumber,bpmnXml,deploymentXml,
                com.x.bpm.form.domain.JsonDocument.snapshot(stepConfig),com.x.bpm.form.domain.JsonDocument.snapshot(formBindings),
                formVersion==null?null:formVersion.getId(),engineDeploymentId,engineDefinitionId,engineVersion,artifactChecksum,
                createdBy.getId(),publishedBy==null?null:publishedBy.getId(),publishedAt,recordStartReady);
    }
    private record PublishedSnapshot(java.util.UUID definitionId,int versionNumber,String bpmnXml,String deploymentXml,
            Map<String,Object> stepConfig,Map<String,Object> formBindings,java.util.UUID formVersionId,String engineDeploymentId,
            String engineDefinitionId,Integer engineVersion,String checksum,java.util.UUID createdBy,java.util.UUID publishedBy,
            Instant publishedAt,boolean recordStartReady) { }
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_definition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_process_versions_process_definition_id"))
    private ProcessDefinitionEntity processDefinition;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.DRAFT;

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    @Column(name = "bpmn_xml", nullable = true, columnDefinition = "text")
    private String bpmnXml;

    /** Exact validated bytes deployed to Flowable; authoring XML remains round-trip safe. */
    @Column(name = "deployment_xml", columnDefinition = "text")
    private String deploymentXml;

    @Column(name = "record_start_ready", nullable = false, columnDefinition = "boolean default false")
    private boolean recordStartReady;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> stepConfig = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_bindings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> formBindings = new LinkedHashMap<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "form_version_id", nullable = true, foreignKey = @ForeignKey(name = "fk_process_versions_form_version_id"))
    private FormVersionEntity formVersion;

    @Column(name = "engine_deployment_id", nullable = true, length = 255)
    private String engineDeploymentId;

    @Column(name = "engine_definition_id", nullable = true, length = 255)
    private String engineDefinitionId;

    @Column(name = "engine_version")
    private Integer engineVersion;

    @Column(name = "artifact_checksum", nullable = true, length = 64)
    private String artifactChecksum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_process_versions_created_by"))
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "published_by", nullable = true, foreignKey = @ForeignKey(name = "fk_process_versions_published_by"))
    private UserEntity publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;
}
