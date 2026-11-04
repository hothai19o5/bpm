package com.x.bpm.form.api;

import com.x.bpm.form.application.CreateForm;
import com.x.bpm.form.application.FormArtifact;
import com.x.bpm.form.application.FormDefinitionUseCase;
import com.x.bpm.form.application.SaveFormDraft;
import com.x.bpm.form.application.FormValidationResult;
import com.x.bpm.form.application.FormVersionCommand;
import com.x.bpm.form.application.FormVersionOperation;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/form-definitions")
public class FormDefinitionController {
    private final FormDefinitionUseCase forms;
    private final ApiResponseFactory responses;

    public FormDefinitionController(FormDefinitionUseCase forms, ApiResponseFactory responses) {
        this.forms = forms;
        this.responses = responses;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('form.design')")
    public ApiResponse<FormArtifact> create(@RequestBody CreateForm body, HttpServletRequest request) {
        return responses.success(forms.create(body), request);
    }

    @GetMapping("/{definitionId}/versions/{versionId}")
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<FormArtifact> version(@PathVariable UUID definitionId,
            @PathVariable UUID versionId, HttpServletRequest request) {
        return responses.success(forms.version(definitionId, versionId), request);
    }

    @PutMapping("/{definitionId}/draft")
    @PreAuthorize("hasAuthority('form.design')")
    public ApiResponse<FormArtifact> saveDraft(@PathVariable UUID definitionId,
            @RequestBody SaveFormDraft body, HttpServletRequest request) {
        return responses.success(forms.saveDraft(definitionId, body), request);
    }

    @PostMapping("/{definitionId}/validate")
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<FormValidationResult> validate(@PathVariable UUID definitionId,
            @RequestBody FormVersionCommand body, HttpServletRequest request) {
        return responses.success(forms.validate(definitionId, body), request);
    }

    @PostMapping("/{definitionId}/publish")
    @PreAuthorize("hasAuthority('form.publish')")
    public ApiResponse<FormArtifact> publish(@PathVariable UUID definitionId, @RequestBody FormVersionCommand body,
            @RequestHeader("Idempotency-Key") UUID requestKey, HttpServletRequest request) {
        return responses.success(forms.mutateVersion(definitionId, body, requestKey, FormVersionOperation.PUBLISH), request);
    }

    @PostMapping("/{definitionId}/clone")
    @PreAuthorize("hasAuthority('form.design')")
    public ApiResponse<FormArtifact> cloneDraft(@PathVariable UUID definitionId, @RequestBody FormVersionCommand body,
            @RequestHeader("Idempotency-Key") UUID requestKey, HttpServletRequest request) {
        return responses.success(forms.mutateVersion(definitionId, body, requestKey, FormVersionOperation.CLONE), request);
    }
}
