package com.x.bpm.process.adapter.persistence;

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
import lombok.Getter;
import lombok.Setter;

/**
 * Lưu danh mục quy trình và phiên bản áp dụng cho hồ sơ mới.
 */
@Getter
@Setter
@Entity
@Table(name = "process_definitions",
        uniqueConstraints = {@UniqueConstraint(name = "uk_process_definitions_1", columnNames = {"key"})},
        indexes = {@Index(name = "ix_process_definitions_1", columnList = "owner_user_id"),
                @Index(name = "ix_process_definitions_2", columnList = "active_process_version_id")})
public class ProcessDefinitionEntity extends MutableEntity {
    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_process_definitions_owner_user_id"))
    private UserEntity owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "active_process_version_id", nullable = true, foreignKey = @ForeignKey(name = "fk_process_definitions_active_process_version_id"))
    private ProcessVersionEntity activeProcessVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, INACTIVE }
}
