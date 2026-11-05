package com.x.bpm.businessrecord.application;

import java.util.UUID;

/** Internal storage reference; not an API response. */
public record StoredAttachment(UUID id,UUID recordId,String fieldId,String name,String contentType,
        long size,String sha256,String bucket,String objectKey) { }
