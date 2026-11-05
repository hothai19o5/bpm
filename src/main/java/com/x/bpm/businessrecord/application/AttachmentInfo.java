package com.x.bpm.businessrecord.application;

import java.util.UUID;

/** Public metadata; never exposes bucket, object key or an unprotected URL. */
public record AttachmentInfo(UUID id,String name) { }
