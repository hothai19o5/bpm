package com.x.bpm.process.application;

import java.util.UUID;

/** Publishes only the persisted revision; no client artifact may bypass draft validation. */
public record PublishProcess(UUID versionId, Long expectedRevision) { }
