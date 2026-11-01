package com.x.bpm.shared.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fallback JSON cho servlet error dispatch, không trả trang lỗi HTML mặc định. */
@RestController
public class ApiErrorController implements ErrorController {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);
    private final ApiResponseFactory responses;

    public ApiErrorController(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        var value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        var status = value instanceof Integer number && number >= 400 && number <= 599
                ? HttpStatusCode.valueOf(number) : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()
                && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable exception) {
            log.error("Servlet error path={}", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), exception);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).body(responses.error(
                ApiErrorMapping.codeFor(status), ApiErrorMapping.messageFor(status), List.of(), status, request));
    }
}
