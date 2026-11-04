package com.x.bpm.process.application;
import java.util.UUID;
public record SaveProcessDraft(UUID versionId, Long expectedRevision, ProcessModelInput model) { }
