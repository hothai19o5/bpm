package com.x.bpm.form.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.x.bpm.form.domain.FormAction;
import com.x.bpm.form.domain.FormFieldAccess;
import com.x.bpm.form.domain.FormFieldPolicy;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class FormDataServiceTest {
    private final FormDataService service = new FormDataService();
    private FormVersion version;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void fixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) {
            var fixture = new ObjectMapper().readValue(stream, Map.class);
            version = new FormVersion(UUID.randomUUID(), UUID.randomUUID(), 1, FormVersion.Status.DRAFT,
                    (Map<String, Object>) fixture.get("schema"), (Map<String, Object>) fixture.get("uiSchema"),
                    UUID.randomUUID(), null, null, 0);
        }
    }

    @Test
    void projectsHiddenFieldsOutOfDataSchemaLayoutAndAccess() {
        var policy = new FormFieldPolicy(Map.of("amount", FormFieldAccess.EDITABLE,
                "itemName", FormFieldAccess.READ_ONLY));
        var stored = Map.<String, Object>of("amount", "50000000", "itemName", "Laptop",
                "budgetCode", "confidential", "unknown", "legacy-secret");
        var view = service.read(version, stored, policy);
        assertThat(view.data()).containsExactlyInAnyOrderEntriesOf(Map.of("amount", "50000000", "itemName", "Laptop"));
        assertThat(view.schema().get("required")).isEqualTo(List.of("amount"));
        assertThat(view.schema().toString()).doesNotContain("budgetCode", "receivedDate");
        assertThat(view.uiSchema().toString()).doesNotContain("budget", "fulfillment", "receivedDate");
        assertThat(view.fieldAccess()).hasSize(2);
        assertThatThrownBy(() -> view.data().put("amount", "1")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.read(version, stored, new FormFieldPolicy(Map.of())).data()).isEmpty();
        assertThat(stored).containsEntry("budgetCode", "confidential");
    }

    @ParameterizedTest
    @ValueSource(strings = {"budgetCode", "completionNote", "receivedDate", "unknown"})
    void blocksWritesAndDeletesOutsideExplicitEditableFieldsEvenWithSameValue(String field) {
        var policy = requester();
        var stored = Map.<String, Object>of(field, "unchanged");
        assertError(ErrorCode.FORBIDDEN, () -> service.applyPatch(version, stored,
                new FormDataPatch(Map.of(field, "unchanged")), policy, FormAction.SAVE_DRAFT, null));
        assertError(ErrorCode.FORBIDDEN, () -> service.applyPatch(version, stored,
                new FormDataPatch(Map.of(), Set.of(field)), policy, FormAction.REJECT, "Reason"));
    }

    @Test
    void mergesBeforeValidationAndPreservesEarlierStepAndHiddenData() {
        var current = new LinkedHashMap<String, Object>(requestData());
        current.put("budgetCode", "BUD-01");
        current.put("budgetConfirmed", false);
        var result = service.applyPatch(version, current, new FormDataPatch(Map.of("reason", "Updated")),
                requester(), FormAction.SUBMIT, null);
        assertThat(result).containsEntry("budgetCode", "BUD-01").containsEntry("budgetConfirmed", false)
                .containsEntry("amount", "50000000").containsEntry("reason", "Updated");
        assertThat(current).containsEntry("reason", "Equipment");
        assertThatThrownBy(() -> result.put("amount", "1")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(FormAction.class)
    void appliesRequiredAndTypeChecksAccordingToAction(FormAction action) {
        Runnable missing = () -> service.applyPatch(version, Map.of(), new FormDataPatch(Map.of()),
                requester(), action, "Reason");
        if (action.requiresCompleteFields()) assertError(ErrorCode.VALIDATION_FAILED, missing);
        else missing.run();
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.applyPatch(version, requestData(),
                new FormDataPatch(Map.of("amount", 50000000)), requester(), action, "Reason"));
    }

    @ParameterizedTest
    @EnumSource(value = FormAction.class, names = {"REJECT", "REQUEST_CHANGES"})
    void requiresActionReasonWithoutRequiringIncompleteBusinessFields(FormAction action) {
        for (String reason : new String[] {null, "", "  ", "a".repeat(4001)}) {
            assertThatThrownBy(() -> service.applyPatch(version, Map.of(), new FormDataPatch(Map.of()),
                    requester(), action, reason)).isInstanceOfSatisfying(ApplicationException.class,
                            ex -> assertThat(ex.details()).anyMatch(issue -> "actionReason".equals(issue.field())));
        }
        assertThat(service.applyPatch(version, Map.of(), new FormDataPatch(Map.of()),
                requester(), action, "Need clarification")).isEmpty();
    }

    @Test
    void supportsExplicitRemovalButStillChecksRequiredAfterMerge() {
        var data = new HashMap<>(requestData());
        data.put("proposalAttachments", List.of("20000000-0000-0000-0000-000000000001"));
        var patch = new FormDataPatch(Map.of(), Set.of("proposalAttachments"));
        assertThat(service.applyPatch(version, data, patch, requester(), FormAction.SUBMIT, null))
                .doesNotContainKey("proposalAttachments");
        var requiredRemoval = new FormDataPatch(Map.of(), Set.of("amount"));
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.applyPatch(version, data,
                requiredRemoval, requester(), FormAction.SUBMIT, null));
        assertThat(service.applyPatch(version, data, requiredRemoval, requester(), FormAction.SAVE_DRAFT, null))
                .doesNotContainKey("amount");
        assertThat(data).containsKey("amount");
        assertError(ErrorCode.INVALID_REQUEST, () -> new FormDataPatch(Map.of("amount", "1"), Set.of("amount")));
    }

    @Test
    void enforcesAdditionalRequiredFieldsFromServerActionContext() {
        var policy = new FormFieldPolicy(requester().access(), Map.of(FormAction.SUBMIT, Set.of("proposalAttachments")));
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.applyPatch(version, requestData(),
                new FormDataPatch(Map.of()), policy, FormAction.SUBMIT, null));
        service.applyPatch(version, requestData(), new FormDataPatch(Map.of()), policy, FormAction.SAVE_DRAFT, null);
        service.applyPatch(version, requestData(), new FormDataPatch(Map.of("proposalAttachments",
                List.of("20000000-0000-0000-0000-000000000001"))), policy, FormAction.SUBMIT, null);
    }

    @Test
    void missingRequiredReadonlyFieldsAreNotRequiredForTheCurrentStep() {
        var policy = new FormFieldPolicy(Map.of("budgetCode", FormFieldAccess.EDITABLE,
                "budgetConfirmed", FormFieldAccess.EDITABLE, "amount", FormFieldAccess.READ_ONLY));
        var result = service.applyPatch(version, Map.of(), new FormDataPatch(Map.of("budgetCode", "BUD-01",
                "budgetConfirmed", false)), policy, FormAction.COMPLETE, null);
        assertThat(result).containsEntry("budgetConfirmed", false);
    }

    @Test
    void processesRequesterThenAccountingWithoutLosingDataAndProjectsApprovalAsReadonly() {
        var draft = service.applyPatch(version, Map.of(), new FormDataPatch(requestData()),
                requester(), FormAction.SUBMIT, null);
        var accounting = new FormFieldPolicy(Map.of("budgetCode", FormFieldAccess.EDITABLE,
                "budgetConfirmed", FormFieldAccess.EDITABLE, "amount", FormFieldAccess.READ_ONLY));
        var checked = service.applyPatch(version, draft, new FormDataPatch(Map.of("budgetCode", "BUD-01",
                "budgetConfirmed", true)), accounting, FormAction.COMPLETE, null);
        assertThat(checked).containsAllEntriesOf(requestData()).containsEntry("budgetConfirmed", true);
        var reviewer = new HashMap<String, FormFieldAccess>();
        checked.keySet().forEach(id -> reviewer.put(id, FormFieldAccess.READ_ONLY));
        var policy = new FormFieldPolicy(reviewer);
        assertThat(service.applyPatch(version, checked, new FormDataPatch(Map.of()), policy,
                FormAction.APPROVE, null)).isEqualTo(checked);
        assertThat(service.read(version, checked, policy).schema().get("required")).isEqualTo(List.of());
        assertError(ErrorCode.FORBIDDEN, () -> service.applyPatch(version, checked,
                new FormDataPatch(Map.of("amount", "1")), policy, FormAction.APPROVE, null));
    }

    @Test
    void sanitizesErrorsForHiddenOrUnknownStoredDataAndRejectsInvalidPolicy() {
        assertThatThrownBy(() -> service.applyPatch(version, Map.of("completionNote", 42),
                new FormDataPatch(Map.of()), requester(), FormAction.SAVE_DRAFT, null))
                .isInstanceOfSatisfying(ApplicationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.details()).allSatisfy(issue -> assertThat(issue.field()).isNull());
                    assertThat(ex.details().toString()).doesNotContain("completionNote", "42");
                });
        var unknown = new FormFieldPolicy(Map.of("deletedField", FormFieldAccess.EDITABLE));
        assertError(ErrorCode.INTERNAL_ERROR, () -> service.read(version, Map.of(), unknown));
        var impossible = new FormFieldPolicy(requester().access(), Map.of(FormAction.SUBMIT, Set.of("budgetCode")));
        assertError(ErrorCode.INTERNAL_ERROR, () -> service.applyPatch(version, Map.of(),
                new FormDataPatch(Map.of()), impossible, FormAction.SAVE_DRAFT, null));
    }

    private FormFieldPolicy requester() {
        return new FormFieldPolicy(Map.of("itemName", FormFieldAccess.EDITABLE, "quantity", FormFieldAccess.EDITABLE,
                "amount", FormFieldAccess.EDITABLE, "reason", FormFieldAccess.EDITABLE,
                "proposalAttachments", FormFieldAccess.EDITABLE, "budgetCode", FormFieldAccess.READ_ONLY,
                "completionNote", FormFieldAccess.HIDDEN));
    }

    private Map<String, Object> requestData() {
        return Map.of("itemName", "Laptop", "quantity", 2, "amount", "50000000", "reason", "Equipment");
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApplicationException.class,
                ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
