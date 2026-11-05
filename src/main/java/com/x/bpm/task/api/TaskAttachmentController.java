package com.x.bpm.task.api;

import com.x.bpm.shared.api.*;
import com.x.bpm.task.application.TaskAttachmentUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/tasks") @PreAuthorize("hasAuthority('task.complete')")
public class TaskAttachmentController {
    private final TaskAttachmentUseCase useCase;private final ApiResponseFactory responses;
    public TaskAttachmentController(TaskAttachmentUseCase useCase,ApiResponseFactory responses){this.useCase=useCase;this.responses=responses;}
    @PostMapping(value="/{taskId}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TaskAttachmentUseCase.UploadedAttachment> upload(@PathVariable String taskId,@RequestParam String fieldId,@RequestParam Long expectedRevision,
            @RequestPart("file") MultipartFile file,HttpServletRequest request)throws IOException{return responses.success(useCase.upload(taskId,fieldId,expectedRevision,file.getOriginalFilename(),file.getBytes()),request);}
    @GetMapping("/{taskId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable String taskId,@PathVariable UUID attachmentId){
        var file=useCase.download(taskId,attachmentId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
}
