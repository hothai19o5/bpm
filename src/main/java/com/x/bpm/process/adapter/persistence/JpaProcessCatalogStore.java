package com.x.bpm.process.adapter.persistence;
import com.x.bpm.process.application.*;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.application.PageResult;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional(readOnly=true)
public class JpaProcessCatalogStore implements ProcessCatalogStore {
    private final EntityManager entities;
    private final ProcessStore store;
    public JpaProcessCatalogStore(EntityManager entities,ProcessStore store){this.entities=entities;this.store=store;}
    private static final String SEARCH="(locate(:q,lower(d.name))>0 or locate(:q,lower(d.key))>0)";
    public PageResult<ProcessDefinition> management(ProcessCatalogFilter f) {
        String from=" from ProcessDefinitionEntity d where "+SEARCH+(f.versionStatus()==null?"":" and exists (select v.id from ProcessVersionEntity v where v.processDefinition=d and v.status=:status)");
        var count=bind(entities.createQuery("select count(d)"+from,Long.class),f).getSingleResult();
        var rows=bind(entities.createQuery("select d"+from+order(f),ProcessDefinitionEntity.class),f).setFirstResult((int)f.page().offset()).setMaxResults(f.page().size()).getResultList();
        return new PageResult<>(rows.stream().map(d->store.definition(d.getId()).orElseThrow()).toList(),f.page().page(),f.page().size(),count);
    }
    public PageResult<ProcessStartOption> startable(ProcessCatalogFilter f) {
        String from=" from ProcessDefinitionEntity d join d.activeProcessVersion v where "+SEARCH
                +" and d.status='ACTIVE' and v.status='PUBLISHED' and v.processDefinition=d and v.engineDefinitionId is not null";
        // Caller cannot widen startability using the management status filter.
        var count=entities.createQuery("select count(d)"+from,Long.class).setParameter("q",f.q()).getSingleResult();
        var rows=entities.createQuery("select d"+from+order(f),ProcessDefinitionEntity.class).setParameter("q",f.q()).setFirstResult((int)f.page().offset()).setMaxResults(f.page().size()).getResultList();
        return new PageResult<>(rows.stream().map(d->new ProcessStartOption(d.getId(),d.getKey(),d.getName(),d.getActiveProcessVersion().getId(),d.getActiveProcessVersion().getVersionNumber())).toList(),f.page().page(),f.page().size(),count);
    }
    private static String order(ProcessCatalogFilter f){var parts=f.page().sort().split(",");return " order by d."+parts[0]+" "+parts[1]+", d.id asc";}
    private static <T> TypedQuery<T> bind(TypedQuery<T> query,ProcessCatalogFilter f){query.setParameter("q",f.q());if(f.versionStatus()!=null)query.setParameter("status",ProcessVersionEntity.Status.valueOf(f.versionStatus().name()));return query;}
}
