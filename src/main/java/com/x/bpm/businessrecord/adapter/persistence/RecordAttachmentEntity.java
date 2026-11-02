package com.x.bpm.businessrecord.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lưu thông tin tệp đính kèm và trạng thái liên kết tệp với hồ sơ.
 */
@Getter
@Setter
@Entity
@Table(name = "record_attachments",
        uniqueConstraints = {@UniqueConstraint(name = "uk_record_attachments_1", columnNames = {"bucket", "object_key"})},
        indexes = {@Index(name = "ix_record_attachments_1", columnList = "business_record_id"),
                @Index(name = "ix_record_attachments_2", columnList = "uploaded_by")})
@Check(constraints = "size_bytes > 0 and size_bytes <= 20971520 and ((status = 'TEMPORARY' and business_record_id is null and linked_at is null) or (status = 'LINKED' and business_record_id is not null and linked_at is not null))")
public class RecordAttachmentEntity extends MutableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "business_record_id", nullable = true, foreignKey = @ForeignKey(name = "fk_record_attachments_business_record_id"))
    private BusinessRecordEntity businessRecord;

    @Column(name = "bucket", nullable = false, length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false, foreignKey = @ForeignKey(name = "fk_record_attachments_uploaded_by"))
    private UserEntity uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.TEMPORARY;

    public enum Status { TEMPORARY, LINKED }

    @Column(name = "linked_at")
    private Instant linkedAt;
}
