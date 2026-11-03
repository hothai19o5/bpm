package com.x.bpm.authorization.domain;

import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.Objects;

/** Policy Java thuần; deny-by-default và kiểm tra permission cùng quan hệ resource. */
public final class AuthorizationPolicy {
    public AuthorizationDecision requirePermission(ActorContext actor, String permission) {
        var actorDecision = actorDecision(actor);
        if (actorDecision != null) {
            return actorDecision;
        }
        return actor.hasPermission(permission) ? AuthorizationDecision.allow()
                : AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PERMISSION);
    }

    public AuthorizationDecision canReadRecord(ActorContext actor, ResourceAccessContext resource) {
        var actorDecision = actorDecision(actor);
        if (actorDecision != null) {
            return actorDecision;
        }
        Objects.requireNonNull(resource, "resource");
        if (actor.hasPermission(PermissionCatalog.RECORD_READ_ALL)) {
            return AuthorizationDecision.allow();
        }
        if (actor.userId().equals(resource.requesterId())
                && actor.hasPermission(PermissionCatalog.RECORD_READ_OWN)) {
            return AuthorizationDecision.allow();
        }
        if (resource.participantUserIds().contains(actor.userId())
                && actor.hasPermission(PermissionCatalog.RECORD_READ_PARTICIPATED)) {
            return AuthorizationDecision.allow();
        }
        return AuthorizationDecision.deny(AuthorizationDecision.Reason.OUTSIDE_RESOURCE_SCOPE);
    }

    public AuthorizationDecision canMonitorProcess(ActorContext actor,
            ResourceAccessContext resource) {
        var actorDecision = actorDecision(actor);
        if (actorDecision != null) {
            return actorDecision;
        }
        Objects.requireNonNull(resource, "resource");
        if (actor.hasPermission(PermissionCatalog.PROCESS_MONITOR_ALL)) {
            return AuthorizationDecision.allow();
        }
        if (actor.userId().equals(resource.processOwnerId())
                && actor.hasPermission(PermissionCatalog.PROCESS_MONITOR_OWNED)) {
            return AuthorizationDecision.allow();
        }
        return AuthorizationDecision.deny(AuthorizationDecision.Reason.OUTSIDE_RESOURCE_SCOPE);
    }

    public AuthorizationDecision canClaimTask(ActorContext actor, ResourceAccessContext resource) {
        var permission = requirePermission(actor, PermissionCatalog.TASK_CLAIM);
        if (!permission.granted()) {
            return permission;
        }
        if (resource.assigneeId() != null || !resource.candidateUserIds().contains(actor.userId())) {
            return AuthorizationDecision.deny(AuthorizationDecision.Reason.NOT_CANDIDATE);
        }
        return AuthorizationDecision.allow();
    }

    public AuthorizationDecision canCompleteTask(ActorContext actor,
            ResourceAccessContext resource) {
        var permission = requirePermission(actor, PermissionCatalog.TASK_COMPLETE);
        if (!permission.granted()) {
            return permission;
        }
        if (!actor.userId().equals(resource.assigneeId())) {
            return AuthorizationDecision.deny(AuthorizationDecision.Reason.NOT_ASSIGNEE);
        }
        if (actor.userId().equals(resource.requesterId())) {
            return AuthorizationDecision.deny(AuthorizationDecision.Reason.SELF_APPROVAL);
        }
        return AuthorizationDecision.allow();
    }

    public AuthorizationDecision canUpdateDraft(ActorContext actor,
            ResourceAccessContext resource) {
        var permission = requirePermission(actor, PermissionCatalog.RECORD_UPDATE_DRAFT);
        if (!permission.granted()) {
            return permission;
        }
        if (!resource.draft()) {
            return AuthorizationDecision.deny(AuthorizationDecision.Reason.NOT_DRAFT);
        }
        return actor.userId().equals(resource.requesterId()) ? AuthorizationDecision.allow()
                : AuthorizationDecision.deny(AuthorizationDecision.Reason.OUTSIDE_RESOURCE_SCOPE);
    }

    public AuthorizationDecision canReassignTask(ActorContext actor) {
        return requirePermission(actor, PermissionCatalog.TASK_REASSIGN);
    }

    public void requireGranted(AuthorizationDecision decision) {
        if (!decision.granted()) {
            throw new ApplicationException(ErrorCode.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.");
        }
    }

    private static AuthorizationDecision actorDecision(ActorContext actor) {
        return actor == null || !actor.active()
                ? AuthorizationDecision.deny(AuthorizationDecision.Reason.INACTIVE_ACTOR) : null;
    }
}
