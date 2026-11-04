package com.x.bpm.form.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Ngữ cảnh tin cậy từ step config/backend, không bind từ request body. */
public record FormFieldPolicy(Map<String, FormFieldAccess> access,
        Map<FormAction, Set<String>> requiredByAction) {
    public FormFieldPolicy {
        access = Map.copyOf(access);
        var required = new EnumMap<FormAction, Set<String>>(FormAction.class);
        requiredByAction.forEach((action, fields) -> required.put(action, Set.copyOf(fields)));
        requiredByAction = Collections.unmodifiableMap(required);
    }

    public FormFieldPolicy(Map<String, FormFieldAccess> access) {
        this(access, Map.of());
    }

    public FormFieldAccess accessTo(String fieldId) {
        return access.getOrDefault(fieldId, FormFieldAccess.HIDDEN);
    }
}
