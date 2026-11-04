package com.x.bpm.process.application;

import com.x.bpm.process.domain.ProcessDefinition;

public record ProcessArtifact(ProcessDefinition definition, ProcessVersionConfiguration version) { }
