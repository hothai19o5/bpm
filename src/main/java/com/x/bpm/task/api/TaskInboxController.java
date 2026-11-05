package com.x.bpm.task.api;

import com.x.bpm.shared.api.*;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.task.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tasks")
public class TaskInboxController {
    private final TaskInboxUseCase useCase;private final ApiResponseFactory responses;
    public TaskInboxController(TaskInboxUseCase useCase,ApiResponseFactory responses){this.useCase=useCase;this.responses=responses;}
    @GetMapping @PreAuthorize("hasAuthority('task.complete')")
    public ApiResponse<ApiPageResponse<TaskSummary>> list(@RequestParam(defaultValue="ASSIGNED") TaskInboxUseCase.Box box,@RequestParam(required=false) String q,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,HttpServletRequest request){return responses.success(ApiPageResponse.from(useCase.list(box,q,PageQuery.from(page,size,"createdAt,desc")),v->v),request);}
    @GetMapping("/{taskId}") @PreAuthorize("hasAuthority('task.complete')")
    public ApiResponse<TaskDetail> detail(@PathVariable String taskId,HttpServletRequest request){return responses.success(useCase.detail(taskId),request);}
    @PostMapping("/{taskId}/claim") @PreAuthorize("hasAuthority('task.claim') and hasAuthority('task.complete')")
    public ApiResponse<TaskDetail> claim(@PathVariable String taskId,HttpServletRequest request){return responses.success(useCase.claim(taskId),request);}
}
