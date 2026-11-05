package com.x.bpm.businessrecord.adapter.persistence;

import com.x.bpm.businessrecord.application.*;
import com.x.bpm.form.domain.*;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.error.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional(propagation=Propagation.MANDATORY)
public class JpaAttachmentStore implements AttachmentStore,RecordAttachmentLinks {
    private final EntityManager entities;private final AttachmentStorage storage;
    public JpaAttachmentStore(EntityManager entities,AttachmentStorage storage){this.entities=entities;this.storage=storage;}
    public long count(UUID recordId){return entities.createQuery("select count(a) from RecordAttachmentEntity a where a.businessRecord.id=:record",Long.class).setParameter("record",recordId).getSingleResult();}
    public StoredAttachment link(UUID recordId,String fieldId,UUID actorId,String name,String type,long size,String sha256,String bucket,String key){
        var record=entities.find(BusinessRecordEntity.class,recordId);
        var attachment=new RecordAttachmentEntity();attachment.setBusinessRecord(record);attachment.setFieldId(fieldId);
        attachment.setUploadedBy(entities.getReference(UserEntity.class,actorId));attachment.setBucket(bucket);attachment.setObjectKey(key);attachment.setOriginalFilename(name);
        attachment.setContentType(type);attachment.setSizeBytes(size);attachment.setSha256(sha256);attachment.setStatus(RecordAttachmentEntity.Status.LINKED);attachment.setLinkedAt(Instant.now());entities.persist(attachment);
        var data=new LinkedHashMap<>(record.getFormData());var ids=new ArrayList<String>();if(data.get(fieldId) instanceof List<?> previous)previous.forEach(value->ids.add((String)value));
        ids.add(attachment.getId().toString());data.put(fieldId,ids);record.setFormData(data);entities.flush();return map(attachment);
    }
    public StoredAttachment find(UUID id){var attachment=entities.find(RecordAttachmentEntity.class,id);if(attachment==null||attachment.getStatus()!=RecordAttachmentEntity.Status.LINKED||attachment.getFieldId()==null||attachment.getSha256()==null)throw new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy tệp đã liên kết.");return map(attachment);}
    public long revision(UUID recordId){return entities.find(BusinessRecordEntity.class,recordId).getRevision();}
    public void requireLinked(UUID recordId,FormVersion form,Map<String,Object> data){
        var fields=(Map<?,?>)form.schema().get("properties");
        for(var field:fields.keySet())if(fields.get(field) instanceof Map<?,?> definition&&"ATTACHMENT".equals(definition.get("x-field-type"))&&data.get(field) instanceof List<?> ids)
            for(var id:ids){
                UUID attachmentId;try{attachmentId=UUID.fromString((String)id);}catch(IllegalArgumentException|ClassCastException invalid){throw unlinked(field.toString());}
                var attachment=entities.find(RecordAttachmentEntity.class,attachmentId);
                if(attachment==null||attachment.getStatus()!=RecordAttachmentEntity.Status.LINKED||attachment.getBusinessRecord()==null
                        ||!recordId.equals(attachment.getBusinessRecord().getId())||!field.equals(attachment.getFieldId())||attachment.getSha256()==null)throw unlinked(field.toString());
                storage.verify(attachment.getBucket(),attachment.getObjectKey(),attachment.getSizeBytes(),attachment.getSha256());
            }
    }
    public List<AttachmentInfo> visible(UUID recordId,FormFieldPolicy policy,Map<String,Object> data){
        var visible=new LinkedHashSet<UUID>();data.forEach((field,value)->{if(policy.accessTo(field)!=FormFieldAccess.HIDDEN&&value instanceof List<?> ids)
            for(var id:ids)try{UUID uuid=UUID.fromString((String)id);var attachment=entities.find(RecordAttachmentEntity.class,uuid);if(attachment!=null&&attachment.getStatus()==RecordAttachmentEntity.Status.LINKED&&recordId.equals(attachment.getBusinessRecord().getId())&&field.equals(attachment.getFieldId()))visible.add(uuid);}catch(IllegalArgumentException|ClassCastException ignored){/* Not every array is an attachment. */}});
        return visible.stream().map(id->{var entity=entities.find(RecordAttachmentEntity.class,id);return new AttachmentInfo(id,entity.getOriginalFilename());}).toList();
    }
    private StoredAttachment map(RecordAttachmentEntity entity){return new StoredAttachment(entity.getId(),entity.getBusinessRecord().getId(),entity.getFieldId(),entity.getOriginalFilename(),entity.getContentType(),entity.getSizeBytes(),entity.getSha256(),entity.getBucket(),entity.getObjectKey());}
    private static ApplicationException unlinked(String field){return new ApplicationException(ErrorCode.VALIDATION_FAILED,"Tệp chưa được liên kết hợp lệ với hồ sơ/trường.",List.of(new ValidationIssue(field,"Chỉ nhận tệp đã tải lên và liên kết đúng hồ sơ/trường.",null)));}
}
