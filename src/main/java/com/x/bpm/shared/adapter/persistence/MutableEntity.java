package com.x.bpm.shared.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Bổ sung thời điểm cập nhật và số revision để phát hiện chỉnh sửa đồng thời.
 * Revision dùng cho khóa lạc quan, độc lập với phiên bản nghiệp vụ và phiên bản bảo mật.
 */
@Getter
@MappedSuperclass
public abstract class MutableEntity extends CreatedEntity {
    @Version
    @Column(nullable = false)
    private long revision;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
