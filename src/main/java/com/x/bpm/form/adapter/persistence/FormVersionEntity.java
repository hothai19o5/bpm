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
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import com.x.bpm.form.domain.JsonDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lưu phiên bản biểu mẫu, cấu trúc dữ liệu và cấu hình giao diện.
 */
@Getter
@Setter
@Entity
@Table(name = "form_versions",
        uniqueConstraints = {@UniqueConstraint(name = "uk_form_versions_1", columnNames = {"form_definition_id", "version_number"})},
        indexes = {@Index(name = "ix_form_versions_1", columnList = "created_by"),
                @Index(name = "ix_form_versions_2", columnList = "published_by")})
@Check(constraints = "version_number > 0 and (status = 'DRAFT' or (published_at is not null and published_by is not null))")
public class FormVersionEntity extends MutableEntity {
    @Transient
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private PublishedSnapshot publishedSnapshot;

    @PostLoad
    @PostPersist
    @PostUpdate
    void snapshotPublished() {
        publishedSnapshot = status == Status.DRAFT ? null : new PublishedSnapshot(
                JsonDocument.snapshot(schemaJson), JsonDocument.snapshot(uiSchemaJson),
                formDefinition.getId(), versionNumber, createdBy.getId(),
                publishedBy == null ? null : publishedBy.getId(), publishedAt);
    }

    @PreUpdate
    void requirePublishedImmutable() {
        if (publishedSnapshot != null && (status == Status.DRAFT
                || !publishedSnapshot.equals(new PublishedSnapshot(schemaJson, uiSchemaJson,
                        formDefinition.getId(), versionNumber, createdBy.getId(),
                        publishedBy == null ? null : publishedBy.getId(), publishedAt)))) {
            throw new IllegalStateException("Published form version content and publication metadata are immutable");
        }
    }

    private record PublishedSnapshot(Map<String, Object> schema, Map<String, Object> uiSchema,
            UUID definitionId, int versionNumber, UUID createdBy, UUID publishedBy, Instant publishedAt) { }
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_definition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_form_versions_form_definition_id"))
    private FormDefinitionEntity formDefinition;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.DRAFT;

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> schemaJson = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> uiSchemaJson = new LinkedHashMap<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_form_versions_created_by"))
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "published_by", nullable = true, foreignKey = @ForeignKey(name = "fk_form_versions_published_by"))
    private UserEntity publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;
}
