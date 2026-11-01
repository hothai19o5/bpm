package com.x.bpm.process.application;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.domain.ProcessCatalogScope;
import com.x.bpm.process.domain.ProcessDefinitionReadPolicy;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;

/** Use case phối hợp người gọi, policy và port; không chứa chi tiết HTTP/SQL. */
public class ListProcessDefinitionsUseCase {

    private final CurrentActorProvider currentActorProvider;
    private final ProcessDefinitionReadPolicy readPolicy;
    private final ProcessDefinitionQueryPort queryPort;

    public ListProcessDefinitionsUseCase(CurrentActorProvider currentActorProvider,
            ProcessDefinitionReadPolicy readPolicy, ProcessDefinitionQueryPort queryPort) {
        this.currentActorProvider = currentActorProvider;
        this.readPolicy = readPolicy;
        this.queryPort = queryPort;
    }

    public PageResult<ProcessDefinitionSummary> execute(PageQuery pageQuery) {
        // Lấy actor từ currentActorProvider, không nhận actor từ controller.
        ActorContext actor = currentActorProvider.requireCurrentActor();
        // readPolicy.resolveScope(actor).
        ProcessCatalogScope scope = readPolicy.resolveScope(actor);

        // 3. Truy vấn danh sách định nghĩa quy trình thuộc scope cho phép
        // 4. Trả kết quả trực tiếp, để ngoại lệ lan truyền nếu xảy ra lỗi hạ tầng/quyền hạn
        return queryPort.findVisible(actor, scope, pageQuery);
    }
}
