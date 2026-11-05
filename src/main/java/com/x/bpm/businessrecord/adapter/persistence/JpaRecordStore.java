package com.x.bpm.businessrecord.adapter.persistence;

import com.x.bpm.businessrecord.application.*;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.form.adapter.persistence.FormVersionEntity;
import com.x.bpm.form.domain.*;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.process.adapter.persistence.*;
import com.x.bpm.process.application.*;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional
public class JpaRecordStore implements RecordStore {
    private final EntityManager entities;
    private final ProcessDefinitionJpaRepository definitions;
    private final ProcessValidationPort metadata;
    public JpaRecordStore(EntityManager entities,ProcessDefinitionJpaRepository definitions,ProcessValidationPort metadata) {
        this.entities=entities;this.definitions=definitions;this.metadata=metadata;
    }
    public RecordTemplate startTemplate(UUID definitionId,UUID versionId) {
        var definition=definitions.lockById(definitionId).orElseThrow(JpaRecordStore::notStartable);
        var version=entities.find(ProcessVersionEntity.class,versionId);
        if(definition.getStatus()!=ProcessDefinitionEntity.Status.ACTIVE||definition.getActiveProcessVersion()==null
                ||!definition.getActiveProcessVersion().getId().equals(versionId)||version==null||!version.getProcessDefinition().getId().equals(definitionId)
                ||version.getStatus()!=ProcessVersionEntity.Status.PUBLISHED||version.getEngineDefinitionId()==null||!version.isRecordStartReady())throw notStartable();
        return template(version);
    }
    public RecordTemplate pinnedTemplate(BusinessRecord record) {
        var version=entities.find(ProcessVersionEntity.class,record.processVersionId());
        if(version==null||!version.getProcessDefinition().getId().equals(record.processDefinitionId())||version.getFormVersion()==null
                ||!version.getFormVersion().getId().equals(record.formVersionId()))throw new ApplicationException(ErrorCode.INTERNAL_ERROR,"Liên kết phiên bản hồ sơ không hợp lệ.");
        // No ACTIVE/current-pointer predicate: a draft keeps its exact schema after retirement.
        return template(version);
    }
    private RecordTemplate template(ProcessVersionEntity version) {
        var form=version.getFormVersion();
        if(form==null||form.getStatus()==FormVersionEntity.Status.DRAFT)throw notStartable();
        var config=metadata.submissionConfig(new ProcessModelInput(version.getBpmnXml(),version.getStepConfig(),version.getFormBindings())).filter(c->form.getId().equals(c.formVersionId())&&c.editable()).orElseThrow(JpaRecordStore::notStartable);
        var access=new LinkedHashMap<String,FormFieldAccess>();config.fieldAccess().forEach((field,value)->access.put(field,FormFieldAccess.valueOf(value)));
        var formVersion=new FormVersion(form.getId(),form.getFormDefinition().getId(),form.getVersionNumber(),FormVersion.Status.valueOf(form.getStatus().name()),
                form.getSchemaJson(),form.getUiSchemaJson(),form.getCreatedBy().getId(),form.getPublishedBy()==null?null:form.getPublishedBy().getId(),form.getPublishedAt(),form.getRevision());
        return new RecordTemplate(version.getProcessDefinition().getId(),version.getId(),version.getProcessDefinition().getName(),formVersion,new FormFieldPolicy(access),version.getEngineDefinitionId());
    }
    @Transactional(readOnly=true)
    public boolean isStartable(BusinessRecord record) {
        return entities.createQuery("select count(v) from ProcessVersionEntity v join v.processDefinition d where v.id=:version and d.id=:definition and d.activeProcessVersion=v and d.status='ACTIVE' and v.status='PUBLISHED' and v.recordStartReady=true and v.engineDefinitionId is not null",Long.class)
                .setParameter("version",record.processVersionId()).setParameter("definition",record.processDefinitionId()).getSingleResult()>0;
    }
    public Optional<BusinessRecord> find(UUID id,boolean lock) {
        return Optional.ofNullable(lock?entities.find(BusinessRecordEntity.class,id,LockModeType.PESSIMISTIC_WRITE):entities.find(BusinessRecordEntity.class,id)).map(this::record);
    }
    public BusinessRecord create(RecordTemplate template,UUID requesterId,String title,Map<String,Object> data) {
        var record=new BusinessRecordEntity();record.setBusinessKey("BPM-"+UUID.randomUUID());
        record.setProcessDefinition(entities.getReference(ProcessDefinitionEntity.class,template.processDefinitionId()));
        record.setProcessVersion(entities.getReference(ProcessVersionEntity.class,template.processVersionId()));
        record.setFormVersion(entities.getReference(FormVersionEntity.class,template.form().id()));
        record.setRequester(entities.getReference(UserEntity.class,requesterId));record.setTitle(title);record.setFormData(data);
        entities.persist(record);entities.flush();return record(record);
    }
    public BusinessRecord save(UUID id,String title,Map<String,Object> data) {
        var entity=entities.find(BusinessRecordEntity.class,id);entity.setTitle(title);entity.setFormData(data);entities.flush();return record(entity);
    }
    public void delete(UUID id){entities.remove(entities.find(BusinessRecordEntity.class,id));entities.flush();}
    @Transactional(readOnly=true)
    public PageResult<RecordSummary> mine(UUID requesterId,RecordQuery query) {
        String from=" from BusinessRecordEntity r where r.requester.id=:requester and (locate(:q,lower(r.title))>0 or locate(:q,lower(r.businessKey))>0)"+(query.status()==null?"":" and r.status=:status");
        var count=bind(entities.createQuery("select count(r)"+from,Long.class),requesterId,query).getSingleResult();
        var sort=query.page().sort().split(",");
        var rows=bind(entities.createQuery("select r"+from+" order by r."+sort[0]+" "+sort[1]+", r.id asc",BusinessRecordEntity.class),requesterId,query)
                .setFirstResult((int)query.page().offset()).setMaxResults(query.page().size()).getResultList();
        return new PageResult<>(rows.stream().map(r->new RecordSummary(r.getId(),r.getBusinessKey(),r.getTitle(),BusinessRecord.Status.valueOf(r.getStatus().name()),r.getRevision(),r.getUpdatedAt())).toList(),query.page().page(),query.page().size(),count);
    }
    private static <T> TypedQuery<T> bind(TypedQuery<T> query,UUID requesterId,RecordQuery filter){query.setParameter("requester",requesterId).setParameter("q",filter.q());if(filter.status()!=null)query.setParameter("status",BusinessRecordEntity.Status.valueOf(filter.status().name()));return query;}
    private BusinessRecord record(BusinessRecordEntity r){return new BusinessRecord(r.getId(),r.getBusinessKey(),r.getProcessDefinition().getId(),r.getProcessVersion().getId(),r.getFormVersion().getId(),r.getRequester().getId(),r.getTitle(),BusinessRecord.Status.valueOf(r.getStatus().name()),r.getFormData(),r.getRevision(),r.getUpdatedAt(),r.getEngineInstanceId());}
    private static ApplicationException notStartable(){return new ApplicationException(ErrorCode.VERSION_NOT_STARTABLE,"Phiên bản không còn được phép khởi tạo hoặc chưa cấu hình form/quyền trường tại Start Event. Tải lại danh mục hoặc liên hệ quản trị.");}
}
