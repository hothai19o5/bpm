package com.x.bpm.form.adapter.persistence;

import com.x.bpm.form.application.FormVersionOperation;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.adapter.persistence.CreatedEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Biên nhận chống lặp cùng transaction; không lưu token hoặc dữ liệu form. */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "form_version_operations", uniqueConstraints =
        @UniqueConstraint(name = "uk_form_version_operations_actor_key", columnNames = {"actor_id", "request_key"}))
public class FormVersionOperationEntity extends CreatedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private UserEntity actor;
    @Column(name = "request_key", nullable = false)
    private UUID requestKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FormVersionOperation operation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_definition_id", nullable = false)
    private FormDefinitionEntity formDefinition;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_version_id", nullable = false)
    private FormVersionEntity sourceVersion;
    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_version_id", nullable = false)
    private FormVersionEntity targetVersion;
}
