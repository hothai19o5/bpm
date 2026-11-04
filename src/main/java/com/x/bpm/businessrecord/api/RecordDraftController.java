package com.x.bpm.businessrecord.api;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import com.x.bpm.shared.api.*;
import com.x.bpm.shared.application.PageQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/records")
public class RecordDraftController {
    private final RecordDraftUseCase records;
    private final ApiResponseFactory responses;
    public RecordDraftController(RecordDraftUseCase records,ApiResponseFactory responses){this.records=records;this.responses=responses;}
    @GetMapping("/new") @PreAuthorize("hasAuthority('record.create') and hasAuthority('process.start')")
    public ApiResponse<RecordDraftTemplateView> template(@RequestParam UUID processDefinitionId,@RequestParam UUID processVersionId,HttpServletRequest request){return responses.success(records.template(processDefinitionId,processVersionId),request);}
    @PostMapping @PreAuthorize("hasAuthority('record.create') and hasAuthority('process.start')")
    public ApiResponse<RecordView> create(@RequestBody CreateRecordDraft body,HttpServletRequest request){return responses.success(records.create(body),request);}
    @GetMapping("/mine") @PreAuthorize("hasAuthority('record.read.own')")
    public ApiResponse<ApiPageResponse<RecordSummary>> mine(@RequestParam(required=false) String q,@RequestParam(required=false) BusinessRecord.Status status,@RequestParam(required=false) Integer page,
            @RequestParam(required=false) Integer size,@RequestParam(defaultValue="updatedAt,desc") String sort,HttpServletRequest request){return responses.success(ApiPageResponse.from(records.mine(new RecordQuery(q,status,PageQuery.from(page,size,sort))),v->v),request);}
    @GetMapping("/{id}")
    public ApiResponse<RecordView> read(@PathVariable UUID id,HttpServletRequest request){return responses.success(records.read(id),request);}
    @PutMapping("/{id}/draft") @PreAuthorize("hasAuthority('record.update.draft')")
    public ApiResponse<RecordView> save(@PathVariable UUID id,@RequestBody SaveRecordDraft body,HttpServletRequest request){return responses.success(records.save(id,body),request);}
    @DeleteMapping("/{id}/draft") @PreAuthorize("hasAuthority('record.delete.draft')")
    public ApiResponse<Void> delete(@PathVariable UUID id,@RequestParam Long expectedRevision,HttpServletRequest request){records.delete(id,expectedRevision);return responses.success(null,request);}
}
