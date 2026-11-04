package com.x.bpm.process.api;

import com.x.bpm.process.application.ListProcessDefinitionsUseCase;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.api.ApiPageResponse;
import com.x.bpm.shared.api.ApiResponse;
import com.x.bpm.shared.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục tối thiểu theo scope; không chứa XML/metadata quản trị. */
@RestController
@RequestMapping("/api/v1/process-definitions")
public class ProcessDefinitionController {

    private final ListProcessDefinitionsUseCase listProcessDefinitions;
    private final ApiResponseFactory responses;

    public ProcessDefinitionController(ListProcessDefinitionsUseCase listProcessDefinitions,
                                       ApiResponseFactory responses) {
        this.listProcessDefinitions = listProcessDefinitions;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<ApiPageResponse<ProcessDefinitionResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, HttpServletRequest request) {
        var pageQuery = PageQuery.from(page, size, sort);
        var result = listProcessDefinitions.execute(pageQuery);
        var pageResponse = ApiPageResponse.from(result, ProcessDefinitionResponse::from);
        return responses.success(pageResponse, request);
    }
}
