package com.x.bpm.process.application;
import com.x.bpm.authorization.domain.*;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.*;
import java.util.*;

public final class ProcessCatalogUseCase {
    private final ProcessCatalogStore store;
    private final CurrentActorProvider actors;
    public ProcessCatalogUseCase(ProcessCatalogStore store,CurrentActorProvider actors){this.store=store;this.actors=actors;}
    public PageResult<ProcessDefinition> management(ProcessCatalogFilter filter) {
        var actor=actors.requireCurrentActor();var policy=new AuthorizationPolicy();
        policy.requireGranted(policy.requirePermission(actor,actor.hasPermission(PermissionCatalog.PROCESS_PUBLISH)?PermissionCatalog.PROCESS_PUBLISH:PermissionCatalog.PROCESS_DESIGN));
        return store.management(validate(filter));
    }
    public PageResult<ProcessStartOption> startable(ProcessCatalogFilter filter) {
        var actor=actors.requireCurrentActor();var policy=new AuthorizationPolicy();
        policy.requireGranted(policy.requirePermission(actor,actor.hasPermission(PermissionCatalog.PROCESS_START)?PermissionCatalog.PROCESS_START:PermissionCatalog.PROCESS_CATALOG_READ));
        return store.startable(validate(filter));
    }
    private static ProcessCatalogFilter validate(ProcessCatalogFilter f) {
        String q=f.q()==null?"":f.q().strip().toLowerCase(Locale.ROOT);
        var parts=f.page().sort().split(",");
        if(q.length()>200||parts.length!=2||!Set.of("key","name","updatedAt").contains(parts[0])||f.page().offset()>Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Bộ lọc/phân trang quy trình không hợp lệ.");
        return new ProcessCatalogFilter(q,f.versionStatus(),f.page());
    }
}
