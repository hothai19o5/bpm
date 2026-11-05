package com.x.bpm.task.assignment.api;

import com.x.bpm.shared.api.*;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.task.assignment.application.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tasks/assignment-exceptions")
public class AssignmentExceptionsController {
    private final AssignmentExceptionsUseCase useCase;
    private final ApiResponseFactory responses;
    public AssignmentExceptionsController(AssignmentExceptionsUseCase useCase, ApiResponseFactory responses) { this.useCase = useCase; this.responses = responses; }
    @GetMapping @PreAuthorize("hasAuthority('task.reassign')")
    public ApiResponse<ApiPageResponse<AssignmentExceptionView>> list(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request) {
        return responses.success(ApiPageResponse.from(useCase.list(PageQuery.from(page, size, "createdAt,desc")), v -> v), request);
    }
}
