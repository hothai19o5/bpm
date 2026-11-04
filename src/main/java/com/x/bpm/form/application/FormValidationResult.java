package com.x.bpm.form.application;

import java.util.UUID;

public record FormValidationResult(UUID versionId, long revision, boolean valid) { }
