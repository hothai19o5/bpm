package com.x.bpm.process.application;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
public interface ProcessReferences {
    Optional<Map<String,Object>> publishedForm(UUID id);
    boolean active(String type, UUID id);
}
