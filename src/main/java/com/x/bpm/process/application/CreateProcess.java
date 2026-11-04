package com.x.bpm.process.application;

import java.util.UUID;

/** Tạo danh mục và draft rỗng v1; owner mặc định là actor đã xác thực. */
public record CreateProcess(String key, String name, String description, UUID ownerUserId) { }
