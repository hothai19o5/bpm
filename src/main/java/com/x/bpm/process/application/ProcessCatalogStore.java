package com.x.bpm.process.application;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.application.PageResult;
public interface ProcessCatalogStore {
    PageResult<ProcessDefinition> management(ProcessCatalogFilter filter);
    PageResult<ProcessStartOption> startable(ProcessCatalogFilter filter);
}
