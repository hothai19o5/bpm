package com.x.bpm.process.adapter.persistence;

import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.application.ProcessDefinitionQueryPort;
import com.x.bpm.process.application.ProcessDefinitionSummary;
import com.x.bpm.process.domain.ProcessCatalogScope;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Chỉ đọc danh mục nghiệp vụ; lọc cùng scope trước COUNT và phân trang. */
@Repository
@Transactional(readOnly = true)
public class ProcessDefinitionQueryAdapter implements ProcessDefinitionQueryPort {
    private final EntityManager entities;
    public ProcessDefinitionQueryAdapter(EntityManager entities) { this.entities = entities; }

    @Override
    public PageResult<ProcessDefinitionSummary> findVisible(ActorContext actor,
            ProcessCatalogScope scope, PageQuery page) {
        var sort = page.sort().split(",");
        if (!Set.of("key", "name", "updatedAt").contains(sort[0]) || page.offset() > Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Phân trang hoặc sort danh mục không hợp lệ.");
        // MVP cấp quyền catalog/start toàn cục; không suy diễn quyền start từ quyền giám sát.
        var where = scope == ProcessCatalogScope.ALL ? "" :
                " where d.status = 'ACTIVE' and exists (select v.id from ProcessVersionEntity v"
                + " where v = d.activeProcessVersion and v.processDefinition = d and v.status = 'PUBLISHED'"
                + " and v.engineDefinitionId is not null)";
        var from = " from ProcessDefinitionEntity d" + where;
        var count = entities.createQuery("select count(d)" + from, Long.class).getSingleResult();
        var items = entities.createQuery("select d" + from + " order by d." + sort[0] + " " + sort[1] + ", d.id asc",
                        ProcessDefinitionEntity.class)
                .setFirstResult((int) page.offset()).setMaxResults(page.size()).getResultList().stream()
                .map(d -> new ProcessDefinitionSummary(d.getId().toString(), d.getKey(), d.getName(), d.getRevision(), d.getUpdatedAt()))
                .toList();
        return new PageResult<>(items, page.page(), page.size(), count);
    }
}
