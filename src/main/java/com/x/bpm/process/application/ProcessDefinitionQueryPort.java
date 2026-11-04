package com.x.bpm.process.application;

import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.domain.ProcessCatalogScope;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;

/** Cổng thuộc bên dùng (application); không phụ thuộc Spring Data/JPA/Flowable. */
public interface ProcessDefinitionQueryPort {

    // Lọc theo scope + actor trước COUNT và phân trang.
    // STARTABLE_FOR_ACTOR chỉ gồm quy trình có version được start và đúng quyền.
    // items và totalElements phải cùng điều kiện; không lọc sau khi đã lấy trang.
    PageResult<ProcessDefinitionSummary> findVisible(ActorContext actor,
            ProcessCatalogScope scope, PageQuery pageQuery);
}
