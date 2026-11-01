package com.x.bpm.shared.application;

import java.util.List;
import java.util.Objects;

/** Kết quả use case; không để Spring Data Page đi qua ranh giới application. */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        Objects.requireNonNull(items, "items must not be null");

        if (items.size() > size) {
            throw new IllegalStateException("items is too large");
        }
        if (page < 0) {
            throw new IllegalStateException("page is negative");
        }
        if (size < 1) {
            throw new IllegalStateException("size is negative");
        }
        if (totalElements < 0) {
            throw new IllegalStateException("totalElements is negative");
        }
        items = List.copyOf(items);
    }

    public long totalPages() {
        if (totalElements == 0) {
            return 0;
        }
        return (totalElements - 1) / size + 1;
    }

    public boolean hasNext() {
        return ((long) page + 1) * size < totalElements;
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
