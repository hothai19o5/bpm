package com.x.bpm.process.api;
import com.x.bpm.process.application.*;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.api.*;
import com.x.bpm.shared.application.PageQuery;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/process-definitions")
public class ProcessCatalogController {
    private final ProcessCatalogUseCase catalog;
    private final ApiResponseFactory responses;
    public ProcessCatalogController(ProcessCatalogUseCase catalog,ApiResponseFactory responses){this.catalog=catalog;this.responses=responses;}
    @GetMapping("/management") @PreAuthorize("hasAnyAuthority('process.design','process.publish')")
    public ApiResponse<ApiPageResponse<ProcessDefinition>> management(@RequestParam(required=false) String q,@RequestParam(required=false) ProcessVersionSummary.Status versionStatus,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@RequestParam(defaultValue="updatedAt,desc") String sort,HttpServletRequest request) {
        return responses.success(ApiPageResponse.from(catalog.management(new ProcessCatalogFilter(q,versionStatus,PageQuery.from(page,size,sort))),v->v),request);
    }
    @GetMapping("/startable") @PreAuthorize("hasAnyAuthority('process.start','process.catalog.read')")
    public ApiResponse<ApiPageResponse<ProcessStartOption>> startable(@RequestParam(required=false) String q,@RequestParam(required=false) Integer page,
            @RequestParam(required=false) Integer size,@RequestParam(defaultValue="name,asc") String sort,HttpServletRequest request) {
        return responses.success(ApiPageResponse.from(catalog.startable(new ProcessCatalogFilter(q,null,PageQuery.from(page,size,sort))),v->v),request);
    }
}
