package com.x.bpm.process.api;
import com.x.bpm.process.application.*;
import com.x.bpm.shared.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/process-definitions")
public class ProcessLifecycleController {
    private final ProcessLifecycleUseCase lifecycle;
    private final ApiResponseFactory responses;
    public ProcessLifecycleController(ProcessLifecycleUseCase lifecycle,ApiResponseFactory responses){this.lifecycle=lifecycle;this.responses=responses;}
    @PostMapping("/{id}/clone") @PreAuthorize("hasAuthority('process.design')")
    public ApiResponse<ProcessArtifact> cloneVersion(@PathVariable UUID id,@RequestBody ProcessLifecycleCommand body,@RequestHeader("Idempotency-Key") UUID key,HttpServletRequest request){return responses.success(lifecycle.mutate(id,body,key,ProcessLifecycleOperation.CLONE),request);}
    @PostMapping("/{id}/duplicate") @PreAuthorize("hasAuthority('process.design')")
    public ApiResponse<ProcessArtifact> duplicate(@PathVariable UUID id,@RequestBody ProcessLifecycleCommand body,@RequestHeader("Idempotency-Key") UUID key,HttpServletRequest request){return responses.success(lifecycle.mutate(id,body,key,ProcessLifecycleOperation.DUPLICATE),request);}
    @PostMapping("/{id}/retire") @PreAuthorize("hasAuthority('process.publish')")
    public ApiResponse<ProcessArtifact> retire(@PathVariable UUID id,@RequestBody ProcessLifecycleCommand body,@RequestHeader("Idempotency-Key") UUID key,HttpServletRequest request){return responses.success(lifecycle.mutate(id,body,key,ProcessLifecycleOperation.RETIRE),request);}
    @PostMapping("/{id}/activate") @PreAuthorize("hasAuthority('process.publish')")
    public ApiResponse<ProcessArtifact> activate(@PathVariable UUID id,@RequestBody ProcessLifecycleCommand body,@RequestHeader("Idempotency-Key") UUID key,HttpServletRequest request){return responses.success(lifecycle.mutate(id,body,key,ProcessLifecycleOperation.ACTIVATE),request);}
}
