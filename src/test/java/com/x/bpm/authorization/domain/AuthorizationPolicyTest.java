package com.x.bpm.authorization.domain;

import com.x.bpm.identity.domain.ActorContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    void employeeReadsOnlyOwnOrParticipatedRecords() {
        var employee = actor("employee", PermissionCatalog.RECORD_READ_OWN,
                PermissionCatalog.RECORD_READ_PARTICIPATED);
        assertThat(policy.canReadRecord(employee, resource("employee", null, Set.of())).granted())
                .isTrue();
        assertThat(policy.canReadRecord(employee,
                resource("other", null, Set.of("employee"))).granted()).isTrue();
        assertThat(policy.canReadRecord(employee, resource("other", null, Set.of())).granted())
                .isFalse();
    }

    @Test
    void adminReadAllDoesNotGrantTaskCompletion() {
        var admin = actor("admin", PermissionCatalog.RECORD_READ_ALL,
                PermissionCatalog.PROCESS_MONITOR_ALL, PermissionCatalog.TASK_REASSIGN);
        var resource = resource("employee", "approver", Set.of());
        assertThat(policy.canReadRecord(admin, resource).granted()).isTrue();
        assertThat(policy.canCompleteTask(admin, resource).granted()).isFalse();
        assertThat(policy.canReassignTask(admin).granted()).isTrue();
    }

    @Test
    void assignedApproverStillCannotApproveOwnRequest() {
        var approver = actor("same-user", PermissionCatalog.TASK_COMPLETE);
        var resource = resource("same-user", "same-user", Set.of());
        assertThat(policy.canCompleteTask(approver, resource).reason())
                .isEqualTo(AuthorizationDecision.Reason.SELF_APPROVAL);
    }

    @Test
    void auditorPermissionDoesNotGrantOperationalActions() {
        var auditor = actor("auditor", PermissionCatalog.AUDIT_READ);
        assertThat(policy.canClaimTask(auditor,
                resource("employee", null, Set.of("auditor"))).granted()).isFalse();
        assertThat(policy.canCompleteTask(auditor,
                resource("employee", "auditor", Set.of())).granted()).isFalse();
    }

    private ActorContext actor(String id, String... permissions) {
        return new ActorContext(id, true, Set.of(permissions));
    }

    private ResourceAccessContext resource(String requester, String assignee,
            Set<String> participants) {
        return new ResourceAccessContext(requester, null, assignee, Set.of(), participants, false);
    }
}
