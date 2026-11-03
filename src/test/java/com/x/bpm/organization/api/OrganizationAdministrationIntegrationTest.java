package com.x.bpm.organization.api;

import com.x.bpm.identity.api.AuthController;
import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.organization.domain.OrganizationStatus;
import com.x.bpm.support.IsolatedPostgresTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@IsolatedPostgresTest
@AutoConfigureMockMvc
class OrganizationAdministrationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort passwordHashes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void authorizedAdministratorCreatesOrganizationAndManagedUser() throws Exception {
        var password = "correct horse battery staple";
        var admin = accounts.create(new NewUserAccount("p22-admin-" + UUID.randomUUID(),
                "P2.2 Admin", passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        grantPermissions(admin.id(), "identity.users.manage", "organization.manage");
        var accessToken = login(admin.username(), password);

        var departmentResult = mvc.perform(post("/api/v1/admin/departments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new OrganizationAdminController.DepartmentWriteRequest(
                                        "P22-" + UUID.randomUUID(), "P2.2 Department", null,
                                        null, OrganizationStatus.ACTIVE, 0))))
                .andExpect(status().isOk()).andReturn();
        var departmentId = objectMapper.readTree(
                departmentResult.getResponse().getContentAsString())
                .get("data").get("id").asString();

        var positionResult = mvc.perform(post("/api/v1/admin/positions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new OrganizationAdminController.PositionWriteRequest(
                                        "POS-" + UUID.randomUUID(), "Specialist", null,
                                        OrganizationStatus.ACTIVE, 0))))
                .andExpect(status().isOk()).andReturn();
        var positionId = objectMapper.readTree(positionResult.getResponse().getContentAsString())
                .get("data").get("id").asString();

        mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"managed.employee","displayName":"Managed Employee",
                                "temporaryPassword":"temporary horse password","status":"ACTIVE",
                                "departmentId":"%s","positionId":"%s","roleIds":[]}
                                """.formatted(departmentId, positionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("managed.employee"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.membership.departmentId").value(departmentId));

        var auditActions = jdbc.queryForList("""
                select action from audit_events
                where actor_id = ? and action in ('DEPARTMENT_CREATED', 'POSITION_CREATED', 'USER_CREATED')
                order by action
                """, String.class, admin.id());
        assertThat(auditActions).containsExactly(
                "DEPARTMENT_CREATED", "POSITION_CREATED", "USER_CREATED");
    }

    @Test
    void authenticatedUserWithoutManagementPermissionIsForbidden() throws Exception {
        var password = "correct horse battery staple";
        var employee = accounts.create(new NewUserAccount("p22-user-" + UUID.randomUUID(),
                "P2.2 Employee", passwordHashes.hash(password), AccountStatus.ACTIVE, false));
        var accessToken = login(employee.username(), password);

        mvc.perform(get("/api/v1/admin/departments")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
    }

    private void grantPermissions(UUID userId, String... permissionCodes) {
        var roleId = UUID.randomUUID();
        jdbc.update("""
                insert into roles (id, created_at, updated_at, revision, code, name, status)
                values (?, current_timestamp, current_timestamp, 0, ?, ?, 'ACTIVE')
                """, roleId, "p22-admin-" + roleId, "P2.2 Admin");
        jdbc.update("""
                insert into user_roles (id, created_at, updated_at, revision, user_id, role_id)
                values (?, current_timestamp, current_timestamp, 0, ?, ?)
                """, UUID.randomUUID(), userId, roleId);
        for (var code : permissionCodes) {
            var permissionId = jdbc.queryForObject(
                    "select id from permissions where code = ?", UUID.class, code);
            jdbc.update("""
                    insert into role_permissions (id, created_at, updated_at, revision, role_id, permission_id)
                    values (?, current_timestamp, current_timestamp, 0, ?, ?)
                    """, UUID.randomUUID(), roleId, permissionId);
        }
    }
}
