package com.x.bpm.process.adapter.persistence;

import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.application.ProcessDefinitionQueryPort;
import com.x.bpm.process.application.ProcessDefinitionSummary;
import com.x.bpm.process.domain.ProcessCatalogScope;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;

/**
 * Khung adapter cho danh mục của ứng dụng; CHƯA là bean và CHƯA có repository.
 * Schema/danh mục/quyền được nối ở P1.4/P2/P4; không thêm DB giả tại P1.3.
 */
public class ProcessDefinitionQueryAdapter implements ProcessDefinitionQueryPort {

    @Override
    public PageResult<ProcessDefinitionSummary> findVisible(ActorContext actor,
            ProcessCatalogScope scope, PageQuery pageQuery) {
        // TODO: Khi có model: inject repository/query component bằng constructor.
        // TODO: Map sort allowlist key/name/updatedAt sang thuộc tính lưu trữ;
        // từ chối trường lạ, thêm id làm tie-breaker cho thứ tự ổn định.
        // TODO: Lọc scope + actor ở DB trước COUNT và LIMIT/OFFSET.
        // TODO: Map kết quả sang ProcessDefinitionSummary và PageResult.
        // TODO: Không đọc bảng ACT_* cho danh mục, không gọi Flowable ở adapter này.
        // TODO: Giữ chưa kích hoạt khi schema/quyền chưa có; không return List.of().
        throw new UnsupportedOperationException("TODO: ProcessDefinitionQueryAdapter.findVisible");
    }
}
