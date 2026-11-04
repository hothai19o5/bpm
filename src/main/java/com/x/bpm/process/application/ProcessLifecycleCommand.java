package com.x.bpm.process.application;

import java.util.UUID;

/** Target fields are accepted only by DUPLICATE; other operations use version/revision. */
public record ProcessLifecycleCommand(UUID versionId, Long expectedRevision, String key, String name,
                                      String description, UUID ownerUserId) { }
