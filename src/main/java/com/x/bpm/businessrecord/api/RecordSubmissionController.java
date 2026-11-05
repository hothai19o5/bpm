package com.x.bpm.businessrecord.api;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.shared.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/records")
public class RecordSubmissionController {
    private final SubmitRecordUseCase submit;
    private final ApiResponseFactory responses;
    public RecordSubmissionController(SubmitRecordUseCase submit,ApiResponseFactory responses){this.submit=submit;this.responses=responses;}
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('record.create') and hasAuthority('process.start')")
    public ApiResponse<RecordView> submit(@PathVariable UUID id,@RequestBody SubmitRecord body,@RequestHeader("Idempotency-Key") UUID key,HttpServletRequest request){return responses.success(submit.submit(id,body,key),request);}
}
