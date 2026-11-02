package com.x.bpm.form.adapter.persistence;

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
 * Lưu danh mục biểu mẫu và người sở hữu.
 */
@Getter
@Setter
@Entity
@Table(name = "form_definitions",
        uniqueConstraints = {@UniqueConstraint(name = "uk_form_definitions_1", columnNames = {"key"})},
        indexes = {@Index(name = "ix_form_definitions_1", columnList = "owner_user_id")})
public class FormDefinitionEntity extends MutableEntity {
    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_form_definitions_owner_user_id"))
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.ACTIVE;

    public enum Status { ACTIVE, INACTIVE }
}
