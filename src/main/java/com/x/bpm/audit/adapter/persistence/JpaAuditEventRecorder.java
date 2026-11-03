package com.x.bpm.audit.adapter.persistence;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.shared.adapter.web.CorrelationIdFilter;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter chỉ ghi sự kiện thành công khi transaction nghiệp vụ còn hiệu lực. */
@Component
public class JpaAuditEventRecorder implements AuditEventRecorder {
    private static final String SOURCE = "ADMIN_API";
    private static final String RESULT = "SUCCESS";

    private final AuditEventJpaRepository events;
    private final EntityManager entityManager;
    private final CurrentActorProvider currentActors;

    public JpaAuditEventRecorder(AuditEventJpaRepository events, EntityManager entityManager,
            CurrentActorProvider currentActors) {
        this.events = events;
        this.entityManager = entityManager;
        this.currentActors = currentActors;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, String entityId,
            Map<String, Object> diff) {
        var event = new AuditEventEntity();
        var actorId = UUID.fromString(currentActors.requireCurrentActor().userId());
        event.setActor(entityManager.getReference(UserEntity.class, actorId));
        event.setSource(SOURCE);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        event.setResult(RESULT);
        event.setDiff(new LinkedHashMap<>(diff));
        events.save(event);
    }
}
