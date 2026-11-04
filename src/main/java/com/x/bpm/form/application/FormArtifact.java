package com.x.bpm.form.application;

import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.form.domain.FormVersion;

/** DTO chung cho API và các module dùng form; không chứa entity/proxy JPA. */
public record FormArtifact(FormDefinition definition, FormVersion version) { }
