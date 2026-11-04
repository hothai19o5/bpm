package com.x.bpm.form.application;

import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.UUID;

public final class FormCatalogUseCase {
    private final FormCatalogStore store;
    private final CurrentActorProvider actors;
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    public FormCatalogUseCase(FormCatalogStore store, CurrentActorProvider actors) {
        this.store = store;
        this.actors = actors;
    }

    public PageResult<FormDefinition> list(FormCatalogQuery query) {
        requireReader();
        return store.list(query);
    }

    public FormDefinition definition(UUID id) {
        requireReader();
        return store.definition(id).orElseThrow(FormCatalogUseCase::missing);
    }

    public PageResult<FormVersionSummary> history(UUID id, PageQuery page) {
        definition(id);
        if (!java.util.Set.of("versionNumber,asc", "versionNumber,desc").contains(page.sort())
                || page.offset() > Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Phân trang lịch sử không hợp lệ.");
        return store.history(id, page);
    }

    public PageResult<FormBindingOption> bindingOptions(FormCatalogQuery query) {
        requireBinding();
        return store.bindingOptions(query);
    }

    public FormArtifact bindingVersion(UUID versionId) {
        requireBinding();
        return store.bindingVersion(versionId).orElseThrow(FormCatalogUseCase::missing);
    }

    private void requireReader() {
        var actor = actors.requireCurrentActor();
        if (!actor.hasPermission(PermissionCatalog.FORM_PUBLISH))
            policy.requireGranted(policy.requirePermission(actor, PermissionCatalog.FORM_DESIGN));
    }

    private void requireBinding() {
        policy.requireGranted(policy.requirePermission(actors.requireCurrentActor(), PermissionCatalog.PROCESS_DESIGN));
    }

    private static ApplicationException missing() {
        return new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy biểu mẫu hoặc phiên bản được phép chọn.");
    }
}
