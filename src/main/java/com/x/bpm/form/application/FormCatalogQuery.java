package com.x.bpm.form.application;

import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.Locale;

public record FormCatalogQuery(String q, FormDefinition.Status status, FormVersion.Status versionStatus, PageQuery page) {
    public FormCatalogQuery {
        q = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        if (q.length() > 200 || page == null || page.offset() > Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Tìm kiếm hoặc phân trang không hợp lệ.");
        if (!java.util.Set.of("name,asc", "name,desc", "key,asc", "key,desc").contains(page.sort()))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Chỉ sắp xếp theo name hoặc key.");
    }
}
