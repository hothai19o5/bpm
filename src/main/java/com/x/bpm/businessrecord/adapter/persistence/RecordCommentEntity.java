package com.x.bpm.businessrecord.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;

/**
 * Lưu bình luận của người dùng trên hồ sơ; không cập nhật sau khi gửi.
 */
@Getter
@Setter
@Entity
@Table(name = "record_comments",
        indexes = {@Index(name = "ix_record_comments_1", columnList = "business_record_id, created_at"),
                @Index(name = "ix_record_comments_2", columnList = "author_id")})
@Check(constraints = "length(trim(content)) > 0")
@Immutable
public class RecordCommentEntity extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_record_id", nullable = false, foreignKey = @ForeignKey(name = "fk_record_comments_business_record_id"))
    private BusinessRecordEntity businessRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_record_comments_author_id"))
    private UserEntity author;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;
}
