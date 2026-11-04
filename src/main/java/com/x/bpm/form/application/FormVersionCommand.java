package com.x.bpm.form.application;

import java.util.UUID;

public record FormVersionCommand(UUID versionId, Long expectedRevision) { }
