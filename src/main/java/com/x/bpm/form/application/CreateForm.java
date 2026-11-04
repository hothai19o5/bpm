package com.x.bpm.form.application;

import java.util.Map;

public record CreateForm(String key, String name, String description,
        Map<String, Object> schema, Map<String, Object> uiSchema) { }
