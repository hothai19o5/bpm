package com.x.bpm.process.api;

import com.x.bpm.process.application.ListProcessDefinitionsUseCase;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.api.ApiPageResponse;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller mỏng đã có implementation; CHƯA có route/bean thực tế.
 * TODO: Sau khi đủ dependency: @RestController + @RequestMapping("/api/v1/process-definitions").
 */
public class ProcessDefinitionController {

    private final ListProcessDefinitionsUseCase listProcessDefinitions;
    private final ApiResponseFactory responses;

    public ProcessDefinitionController(ListProcessDefinitionsUseCase listProcessDefinitions,
                                       ApiResponseFactory responses) {
        this.listProcessDefinitions = listProcessDefinitions;
        this.responses = responses;
    }

    // TODO: @GetMapping; mỗi tham số dùng @RequestParam(name = "...", required = false).
    // Để Integer nullable nhằm phân biệt thiếu tham số với số 0.
    public ApiResponse<ApiPageResponse<ProcessDefinitionResponse>> list(Integer page, Integer size,
            String sort, HttpServletRequest request) {
        var pageQuery = PageQuery.from(page, size, sort);
        var result = listProcessDefinitions.execute(pageQuery);
        var pageResponse = ApiPageResponse.from(result, ProcessDefinitionResponse::from);
        return responses.success(pageResponse, request);
    }
}
