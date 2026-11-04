package com.x.bpm.form.adapter.persistence;

import com.x.bpm.form.application.*;
import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaFormCatalogStore implements FormCatalogStore {
    private final EntityManager entities;
    private final FormStore forms;
    private static final String SEARCH = " (locate(:q, lower(f.name)) > 0 or locate(:q, lower(f.key)) > 0) ";
    private static final String SUMMARY = "v.id, v.versionNumber, v.status, v.createdBy.id, p.id, v.publishedAt, v.revision";

    public JpaFormCatalogStore(EntityManager entities, FormStore forms) {
        this.entities = entities;
        this.forms = forms;
    }

    @Override
    public PageResult<FormDefinition> list(FormCatalogQuery query) {
        var where = " where " + SEARCH
                + (query.status() == null ? "" : " and f.status = :status")
                + (query.versionStatus() == null ? "" : " and exists (select v.id from FormVersionEntity v where v.formDefinition = f and v.status = :versionStatus)");
        var count = bind(entities.createQuery("select count(f) from FormDefinitionEntity f" + where, Long.class), query).getSingleResult();
        var rows = bind(entities.createQuery("select f from FormDefinitionEntity f" + where + order(query.page()), FormDefinitionEntity.class), query);
        return result(limit(rows, query.page()).getResultList().stream().map(this::definition).toList(), query.page(), count);
    }

    @Override
    public Optional<FormDefinition> definition(UUID id) {
        return Optional.ofNullable(entities.find(FormDefinitionEntity.class, id)).map(this::definition);
    }

    @Override
    public PageResult<FormVersionSummary> history(UUID id, PageQuery page) {
        var from = " from FormVersionEntity v left join v.publishedBy p where v.formDefinition.id = :id";
        var count = entities.createQuery("select count(v)" + from, Long.class).setParameter("id", id).getSingleResult();
        var rows = entities.createQuery("select " + SUMMARY + from + " order by v.versionNumber "
                + (page.sort().endsWith(",asc") ? "asc" : "desc"), Object[].class).setParameter("id", id);
        return result(limit(rows, page).getResultList().stream().map(this::summary).toList(), page, count);
    }

    @Override
    public PageResult<FormBindingOption> bindingOptions(FormCatalogQuery query) {
        // Scope/status predicates precede count and pagination; caller cannot override these.
        var from = " from FormVersionEntity v join v.formDefinition f left join v.publishedBy p where " + SEARCH
                + " and f.status = :active and v.status = :published";
        var countQuery = entities.createQuery("select count(v)" + from, Long.class);
        bindingParameters(countQuery, query);
        var rows = entities.createQuery("select f, " + SUMMARY + from + order(query.page()) + ", v.versionNumber desc", Object[].class);
        bindingParameters(rows, query);
        var items = limit(rows, query.page()).getResultList().stream().map(row -> new FormBindingOption(
                definition((FormDefinitionEntity) row[0]), summary(java.util.Arrays.copyOfRange(row, 1, row.length)))).toList();
        return result(items, query.page(), countQuery.getSingleResult());
    }

    @Override
    public Optional<FormArtifact> bindingVersion(UUID versionId) {
        return entities.createQuery("select v.formDefinition.id from FormVersionEntity v where v.id = :id"
                + " and v.status = :published and v.formDefinition.status = :active", UUID.class)
                .setParameter("id", versionId).setParameter("published", FormVersionEntity.Status.PUBLISHED)
                .setParameter("active", FormDefinitionEntity.Status.ACTIVE).getResultStream().findFirst()
                .flatMap(id -> forms.findVersion(id, versionId));
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> target, FormCatalogQuery query) {
        target.setParameter("q", query.q());
        if (query.status() != null) target.setParameter("status", FormDefinitionEntity.Status.valueOf(query.status().name()));
        if (query.versionStatus() != null) target.setParameter("versionStatus", FormVersionEntity.Status.valueOf(query.versionStatus().name()));
        return target;
    }

    private void bindingParameters(TypedQuery<?> target, FormCatalogQuery query) {
        target.setParameter("q", query.q()).setParameter("active", FormDefinitionEntity.Status.ACTIVE)
                .setParameter("published", FormVersionEntity.Status.PUBLISHED);
    }

    private String order(PageQuery page) {
        var parts = page.sort().split(",");
        return " order by f." + parts[0] + " " + parts[1] + ", f.id asc";
    }

    private <T> TypedQuery<T> limit(TypedQuery<T> query, PageQuery page) {
        return query.setFirstResult((int) page.offset()).setMaxResults(page.size());
    }

    private <T> PageResult<T> result(List<T> rows, PageQuery page, long count) {
        return new PageResult<>(rows, page.page(), page.size(), count);
    }

    private FormDefinition definition(FormDefinitionEntity f) {
        return new FormDefinition(f.getId(), f.getKey(), f.getName(), f.getDescription(), f.getOwner().getId(),
                FormDefinition.Status.valueOf(f.getStatus().name()), f.getRevision());
    }

    private FormVersionSummary summary(Object[] row) {
        return new FormVersionSummary((UUID) row[0], (Integer) row[1], FormVersion.Status.valueOf(row[2].toString()),
                (UUID) row[3], (UUID) row[4], (Instant) row[5], (Long) row[6]);
    }
}
