package com.x.bpm.businessrecord.application;

import java.util.UUID;

public interface AttachmentStore {
    long count(UUID recordId);
    StoredAttachment link(UUID recordId,String fieldId,UUID actorId,String name,String contentType,
            long size,String sha256,String bucket,String objectKey);
    StoredAttachment find(UUID id);
    long revision(UUID recordId);
}
