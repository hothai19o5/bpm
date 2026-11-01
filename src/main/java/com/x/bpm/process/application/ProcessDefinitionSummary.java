package com.x.bpm.process.application;

import java.time.Instant;

/** Dữ liệu đọc tối thiểu từ danh mục ứng dụng; revision này không phải version BPMN. */
public record ProcessDefinitionSummary(String id, String key, String name,
                                        long revision, Instant updatedAt) {
    // TODO: Khi nối persistence, map application ID; không lộ engine definition ID/XML.
}
