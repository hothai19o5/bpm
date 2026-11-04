package com.x.bpm.form.application;

/** Replay không phát sinh thêm audit hoặc version. */
public record FormMutationResult(FormArtifact artifact, boolean replayed) { }
