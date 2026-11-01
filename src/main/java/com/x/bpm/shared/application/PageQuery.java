package com.x.bpm.shared.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/** Đầu vào phân trang Java thuần; sort chỉ là tên trường API, không phải SQL. */
public record PageQuery(int page, int size, String sort) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "updatedAt,desc";

    private static final Pattern SORT_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*),(asc|desc)", Pattern.CASE_INSENSITIVE);

    public PageQuery {
        if (page < 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "page phải lớn hơn hoặc bằng 0.");
        }
        if (size < 1 || size > 100) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "size phải nằm trong khoảng 1 đến 100.");
        }

        if (sort == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "sort không được null.");
        }
        var matcher = SORT_PATTERN.matcher(sort.strip());
        if (!matcher.matches()) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,
                    "sort phải có dạng field,asc hoặc field,desc.");
        }
        // Kiểm tra allowlist tên trường cụ thể thuộc query adapter của từng use case.
        sort = matcher.group(1) + "," + matcher.group(2).toLowerCase(Locale.ROOT);
    }

    public static PageQuery from(Integer page, Integer size, String sort) {
        int resolvedPage = (page != null) ? page : DEFAULT_PAGE;
        int resolvedSize = (size != null) ? size : DEFAULT_SIZE;
        String resolvedSort = (sort != null) ? sort : DEFAULT_SORT;

        return new PageQuery(resolvedPage, resolvedSize, resolvedSort);
    }

    public long offset() {
        return (long) page * (long) size;
    }
}
