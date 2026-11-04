package com.x.bpm.form.api;

import com.x.bpm.form.application.CreateForm;
import com.x.bpm.form.application.FormArtifact;
import com.x.bpm.form.application.FormDefinitionUseCase;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/form-definitions")
@PreAuthorize("hasAuthority('form.design')")
public class FormDefinitionController {
    private final FormDefinitionUseCase forms;
    private final ApiResponseFactory responses;

    public FormDefinitionController(FormDefinitionUseCase forms, ApiResponseFactory responses) {
        this.forms = forms;
        this.responses = responses;
    }

    @PostMapping
    public ApiResponse<FormArtifact> create(@RequestBody CreateForm body, HttpServletRequest request) {
        return responses.success(forms.create(body), request);
    }

    @GetMapping("/{definitionId}/versions/{versionId}")
    public ApiResponse<FormArtifact> version(@PathVariable UUID definitionId,
            @PathVariable UUID versionId, HttpServletRequest request) {
        return responses.success(forms.version(definitionId, versionId), request);
    }
}
