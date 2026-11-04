package com.x.bpm.form.api;

import com.x.bpm.form.application.*;
import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/form-definitions")
public class FormCatalogController {
    private final FormCatalogUseCase catalog;
    private final FormPreviewUseCase previews;
    private final ApiResponseFactory responses;

    public FormCatalogController(FormCatalogUseCase catalog, FormPreviewUseCase previews, ApiResponseFactory responses) {
        this.catalog = catalog;
        this.previews = previews;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<PageResult<FormDefinition>> list(@RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) FormDefinition.Status status,
            @RequestParam(required = false) FormVersion.Status versionStatus,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort, HttpServletRequest request) {
        return responses.success(catalog.list(new FormCatalogQuery(q, status, versionStatus, new PageQuery(page, size, sort))), request);
    }

    @GetMapping("/{definitionId}")
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<FormDefinition> detail(@PathVariable UUID definitionId, HttpServletRequest request) {
        return responses.success(catalog.definition(definitionId), request);
    }

    @GetMapping("/{definitionId}/versions")
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<PageResult<FormVersionSummary>> history(@PathVariable UUID definitionId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "versionNumber,desc") String sort, HttpServletRequest request) {
        return responses.success(catalog.history(definitionId, new PageQuery(page, size, sort)), request);
    }

    @GetMapping("/binding-options")
    @PreAuthorize("hasAuthority('process.design')")
    public ApiResponse<PageResult<FormBindingOption>> bindings(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort, HttpServletRequest request) {
        return responses.success(catalog.bindingOptions(new FormCatalogQuery(q, null, null, new PageQuery(page, size, sort))), request);
    }

    @GetMapping("/binding-options/{versionId}")
    @PreAuthorize("hasAuthority('process.design')")
    public ApiResponse<FormArtifact> binding(@PathVariable UUID versionId, HttpServletRequest request) {
        return responses.success(catalog.bindingVersion(versionId), request);
    }

    @GetMapping("/{definitionId}/versions/{versionId}/preview")
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<FormPreviewUseCase.Preview> preview(@PathVariable UUID definitionId, @PathVariable UUID versionId,
            @RequestParam FormPreviewUseCase.Context context, HttpServletRequest request) {
        return responses.success(previews.read(definitionId, versionId, context), request);
    }

    @PostMapping("/{definitionId}/versions/{versionId}/preview")
    @PreAuthorize("hasAnyAuthority('form.design', 'form.publish')")
    public ApiResponse<FormPreviewUseCase.Preview> check(@PathVariable UUID definitionId, @PathVariable UUID versionId,
            @RequestBody FormPreviewUseCase.Command command, HttpServletRequest request) {
        return responses.success(previews.check(definitionId, versionId, command), request);
    }
}
