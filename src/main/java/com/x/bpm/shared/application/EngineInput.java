package com.x.bpm.shared.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.Map;
import java.util.Set;

/** Minimal technical input checks shared by the process and task engine adapters. */
public final class EngineInput {
    private EngineInput() {
    }

    public static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, field + " must not be blank");
        }
        return value;
    }

    /** P1.5 supports scalar variables only; never persist arbitrary Java/engine objects. */
    public static Map<String, Object> variables(Map<String, Object> variables) {
        if (variables == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "variables must not be null");
        }
        variables.forEach((key, value) -> {
            text(key, "variable name");
            if (!(value instanceof String || value instanceof Boolean || value instanceof Integer
                    || value instanceof Long || value instanceof Double || value instanceof java.math.BigDecimal)) {
                throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Engine variables must be non-null scalar values");
            }
        });
        return Map.copyOf(variables);
    }

    public static Set<String> groups(Set<String> groupIds) {
        if (groupIds == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "groupIds must not be null");
        }
        groupIds.forEach(group -> text(group, "group ID"));
        return Set.copyOf(groupIds);
    }
}
