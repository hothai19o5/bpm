package com.x.bpm.form.application;

import com.x.bpm.form.domain.*;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fixture quản trị tạm P3.6. Không nhận policy/stored data từ caller, không ghi DB. */
public final class FormPreviewUseCase {
    public enum Context { REQUESTER, ACCOUNTING }
    public record Preview(Context context, String mode, FormDataView view, List<FormAction> actions) {}
    public record Command(Context context, FormDataPatch patch, FormAction action, String reason) {}
    private static final Set<String> REQUEST = Set.of("itemName", "quantity", "amount", "reason", "proposalAttachments");
    private static final Set<String> BUDGET = Set.of("budgetCode", "budgetConfirmed");
    private static final Map<String, String> TYPES = Map.of("itemName", "SHORT_TEXT", "quantity", "INTEGER",
            "amount", "MONEY_VND", "reason", "LONG_TEXT", "proposalAttachments", "ATTACHMENT",
            "budgetCode", "SHORT_TEXT", "budgetConfirmed", "CHECKBOX", "completionNote", "LONG_TEXT",
            "receivedDate", "DATE", "receiptAttachments", "ATTACHMENT");
    private final FormDefinitionUseCase forms;
    private final FormDataService data;

    public FormPreviewUseCase(FormDefinitionUseCase forms, FormDataService data) {
        this.forms = forms;
        this.data = data;
    }

    public Preview read(UUID definitionId, UUID versionId, Context context) {
        var version = forms.version(definitionId, versionId).version();
        requireContext(context);
        return new Preview(context, context == Context.REQUESTER ? "draft" : "task",
                data.read(version, Map.of(), policy(version, context)), actions(context));
    }

    public Preview check(UUID definitionId, UUID versionId, Command command) {
        var version = forms.version(definitionId, versionId).version();
        if (command == null || command.patch() == null || command.action() == null) throw invalid();
        requireContext(command.context());
        if (!actions(command.context()).contains(command.action()))
            throw new ApplicationException(ErrorCode.FORBIDDEN, "Hành động không được phép trong ngữ cảnh xem thử.");
        var policy = policy(version, command.context());
        var merged = data.applyPatch(version, Map.of(), command.patch(), policy, command.action(), command.reason());
        return new Preview(command.context(), command.context() == Context.REQUESTER ? "draft" : "task",
                data.read(version, merged, policy), actions(command.context()));
    }

    @SuppressWarnings("unchecked")
    private FormFieldPolicy policy(FormVersion version, Context context) {
        new FormSchemaContract().validate(version.schema(), version.uiSchema());
        var access = new LinkedHashMap<String, FormFieldAccess>();
        var fields = (Map<String, Map<String, Object>>) (Map<?, ?>) version.schema().get("properties");
        fields.forEach((id, field) -> {
            if (!TYPES.containsKey(id) || !TYPES.get(id).equals(field.get("x-field-type"))) return;
            if (REQUEST.contains(id)) access.put(id, context == Context.REQUESTER ? FormFieldAccess.EDITABLE : FormFieldAccess.READ_ONLY);
            if (!REQUEST.contains(id)) access.put(id, BUDGET.contains(id) && context == Context.ACCOUNTING
                    ? FormFieldAccess.EDITABLE : FormFieldAccess.READ_ONLY);
        });
        // Mọi field không có trong fixture bị HIDDEN theo mặc định.
        return new FormFieldPolicy(access);
    }

    private List<FormAction> actions(Context context) {
        return context == Context.REQUESTER ? List.of(FormAction.SAVE_DRAFT, FormAction.SUBMIT)
                : List.of(FormAction.APPROVE, FormAction.REJECT, FormAction.REQUEST_CHANGES);
    }

    private void requireContext(Context context) { if (context == null) throw invalid(); }
    private ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_REQUEST, "Thiếu ngữ cảnh xem thử hoặc patch."); }
}
