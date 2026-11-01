package com.x.bpm.shared.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationContractsTest {

    @Test
    void defaultsAndExplicitValuesProduceZeroBasedOffsets() {
        assertThat(PageQuery.from(null, null, null))
                .isEqualTo(new PageQuery(0, 20, "updatedAt,desc"));
        assertThat(PageQuery.from(2, 20, "updatedAt,desc").offset()).isEqualTo(40L);
        assertThat(new PageQuery(Integer.MAX_VALUE, 100, "name,asc").offset())
                .isEqualTo(214_748_364_700L);
        assertThat(PageQuery.from(0, 20, " name,DESC ").sort()).isEqualTo("name,desc");
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void invalidInputHasStableClientErrorCode(int page, int size, String sort) {
        assertThatThrownBy(() -> new PageQuery(page, size, sort))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of(-1, 20, "name,asc"),
                Arguments.of(0, 0, "name,asc"),
                Arguments.of(0, 101, "name,asc"),
                Arguments.of(0, 20, (String) null),
                Arguments.of(0, 20, ""),
                Arguments.of(0, 20, " "),
                Arguments.of(0, 20, "name"),
                Arguments.of(0, 20, "name,"),
                Arguments.of(0, 20, "name,asc,"),
                Arguments.of(0, 20, "name,invalid"),
                Arguments.of(0, 20, "1name,desc"),
                Arguments.of(0, 20, "name,asc\nother"));
    }

    @Test
    void factoryDoesNotReplaceInvalidExplicitValuesWithDefaults() {
        assertThatThrownBy(() -> PageQuery.from(null, 0, null))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> PageQuery.from(null, null, ""))
                .isInstanceOf(ApplicationException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,20,0", "1,20,1", "20,20,1", "21,20,2",
            "9223372036854775807,1,9223372036854775807"})
    void totalPagesRoundsUpWithoutOverflow(long total, int size, long expected) {
        var result = new PageResult<String>(List.of(), 0, size, total);
        assertThat(result.totalPages()).isEqualTo(expected);
    }

    @Test
    void navigationHandlesLastEmptyAndMaximumIndexPages() {
        var first = new PageResult<String>(List.of(), 0, 20, 21);
        var last = new PageResult<String>(List.of(), 1, 20, 21);
        var empty = new PageResult<String>(List.of(), 0, 20, 0);
        var outsideRange = new PageResult<String>(List.of(), Integer.MAX_VALUE, 100, 1);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();
        assertThat(last.hasNext()).isFalse();
        assertThat(last.hasPrevious()).isTrue();
        assertThat(empty.hasNext()).isFalse();
        assertThat(outsideRange.hasNext()).isFalse();
    }

    @Test
    void resultOwnsAnImmutableSnapshotOfItsItems() {
        var source = new ArrayList<>(List.of("process-001"));
        var result = new PageResult<>(source, 0, 20, 1);
        source.clear();
        assertThat(result.items()).containsExactly("process-001");
        assertThatThrownBy(() -> result.items().add("process-002"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of("one", "two"), 0, 1, 2))
                .isInstanceOf(IllegalStateException.class);
    }
}
