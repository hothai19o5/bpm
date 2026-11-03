package com.x.bpm.authorization.domain;

import java.util.Objects;

public record AuthorizationDecision(boolean granted, Reason reason) {
    public AuthorizationDecision {
        Objects.requireNonNull(reason, "reason");
        if (granted && reason != Reason.GRANTED) {
            throw new IllegalArgumentException("Granted decision requires GRANTED reason");
        }
        if (!granted && reason == Reason.GRANTED) {
            throw new IllegalArgumentException("Denied decision requires denial reason");
        }
    }

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(true, Reason.GRANTED);
    }

    public static AuthorizationDecision deny(Reason reason) {
        return new AuthorizationDecision(false, reason);
    }

    public enum Reason {
        GRANTED,
        INACTIVE_ACTOR,
        MISSING_PERMISSION,
        OUTSIDE_RESOURCE_SCOPE,
        NOT_CANDIDATE,
        NOT_ASSIGNEE,
        SELF_APPROVAL,
        NOT_DRAFT
    }
}
