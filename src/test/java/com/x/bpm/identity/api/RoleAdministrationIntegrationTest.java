package com.x.bpm.identity.api;

import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
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

@IsolatedPostgresTest
@AutoConfigureMockMvc
class RoleAdministrationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort hashes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void roleManagerListsCatalogAndCreatesRoleFromKnownPermissions() throws Exception {
        var password = "correct horse battery staple";
        var admin = accounts.create(new NewUserAccount("role-admin-" + UUID.randomUUID(),
                "Role Admin", hashes.hash(password), AccountStatus.ACTIVE, false));
        grantPermission(admin.id(), PermissionCatalog.IDENTITY_ROLES_MANAGE);
        var token = login(admin.username(), password);

        var permissionsResult = mvc.perform(get("/api/v1/admin/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var permissions = objectMapper.readTree(
                permissionsResult.getResponse().getContentAsString()).get("data");
        String permissionId = null;
        for (var permission : permissions) {
            if (PermissionCatalog.PROCESS_CATALOG_READ.equals(
                    permission.get("code").asString())) {
                permissionId = permission.get("id").asString();
                break;
            }
        }
        if (permissionId == null) {
            throw new AssertionError("process.catalog.read permission was not initialized");
        }

        mvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"viewer-%s","name":"Viewer","active":true,
                                "permissionIds":["%s"],"expectedRevision":0}
                                """.formatted(UUID.randomUUID(), permissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0].code")
                        .value(PermissionCatalog.PROCESS_CATALOG_READ));
    }

    @Test
    void authenticatedUserWithoutRoleManagementPermissionIsForbidden() throws Exception {
        var password = "correct horse battery staple";
        var employee = accounts.create(new NewUserAccount("role-user-" + UUID.randomUUID(),
                "Role User", hashes.hash(password), AccountStatus.ACTIVE, false));
        var token = login(employee.username(), password);
        mvc.perform(get("/api/v1/admin/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
    }

    private void grantPermission(UUID userId, String permissionCode) {
        var roleId = UUID.randomUUID();
        var permissionId = jdbc.queryForObject("select id from permissions where code = ?",
                UUID.class, permissionCode);
        jdbc.update("""
                insert into roles (id, created_at, updated_at, revision, code, name, status)
                values (?, current_timestamp, current_timestamp, 0, ?, ?, 'ACTIVE')
                """, roleId, "role-admin-" + roleId, "Role Admin");
        jdbc.update("""
                insert into user_roles (id, created_at, updated_at, revision, user_id, role_id)
                values (?, current_timestamp, current_timestamp, 0, ?, ?)
                """, UUID.randomUUID(), userId, roleId);
        jdbc.update("""
                insert into role_permissions (id, created_at, updated_at, revision, role_id, permission_id)
                values (?, current_timestamp, current_timestamp, 0, ?, ?)
                """, UUID.randomUUID(), roleId, permissionId);
    }
}
