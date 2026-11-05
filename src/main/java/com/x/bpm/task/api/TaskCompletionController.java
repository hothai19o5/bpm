package com.x.bpm.task.api;

import com.x.bpm.shared.api.*;
import com.x.bpm.task.application.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tasks")
public class TaskCompletionController {
    private final CompleteTaskUseCase useCase;private final ApiResponseFactory responses;
    public TaskCompletionController(CompleteTaskUseCase useCase,ApiResponseFactory responses){this.useCase=useCase;this.responses=responses;}
    @PostMapping("/{taskId}/complete") @PreAuthorize("hasAuthority('task.complete')")
    public ApiResponse<TaskCompletionResult> complete(@PathVariable String taskId,@RequestBody CompleteTask body,
            @RequestHeader("Idempotency-Key") UUID requestKey,HttpServletRequest request){return responses.success(useCase.complete(taskId,body,requestKey),request);}
}
