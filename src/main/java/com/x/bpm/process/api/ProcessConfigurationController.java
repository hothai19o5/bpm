package com.x.bpm.process.api;

import com.x.bpm.process.application.*;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.api.*;
import com.x.bpm.shared.application.PageQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/process-definitions")
@PreAuthorize("hasAnyAuthority('process.design', 'process.publish')")
public class ProcessConfigurationController {
    private final ProcessDefinitionUseCase processes;
    private final ApiResponseFactory responses;

    public ProcessConfigurationController(ProcessDefinitionUseCase processes, ApiResponseFactory responses) {
        this.processes = processes;
        this.responses = responses;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('process.design')")
    public ApiResponse<ProcessArtifact> create(@RequestBody CreateProcess body, HttpServletRequest request) {
        return responses.success(processes.create(body), request);
    }

    @GetMapping("/{definitionId}")
    public ApiResponse<ProcessDefinition> definition(@PathVariable UUID definitionId, HttpServletRequest request) {
        return responses.success(processes.definition(definitionId), request);
    }

    @GetMapping("/{definitionId}/versions")
    public ApiResponse<ApiPageResponse<ProcessVersionSummary>> history(@PathVariable UUID definitionId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "versionNumber,desc") String sort, HttpServletRequest request) {
        return responses.success(ApiPageResponse.from(processes.history(definitionId, PageQuery.from(page, size, sort)), v -> v), request);
    }

    @GetMapping("/{definitionId}/versions/{versionId}")
    public ApiResponse<ProcessArtifact> version(@PathVariable UUID definitionId, @PathVariable UUID versionId,
            HttpServletRequest request) {
        return responses.success(processes.version(definitionId, versionId), request);
    }
}
