package com.x.bpm.shared.api;

import com.x.bpm.shared.application.PageResult;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Payload phân trang đặt trong ApiResponse.data; không trả Spring Data Page trực tiếp. */
public record ApiPageResponse<T>(List<T> items, int page, int size,
                                 long totalElements, long totalPages) {

    public ApiPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static <S, T> ApiPageResponse<T> from(PageResult<S> result,
                                                Function<S, T> itemMapper) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(itemMapper, "itemMapper");
        return new ApiPageResponse<>(result.items().stream().map(itemMapper).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
