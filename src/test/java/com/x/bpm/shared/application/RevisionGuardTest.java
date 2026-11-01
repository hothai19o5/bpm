package com.x.bpm.shared.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RevisionGuardTest {

    @Test
    void matchingRevisionSucceeds() {
        assertThatCode(() -> RevisionGuard.requireMatch(4, 4)).doesNotThrowAnyException();
    }

    @Test
    void outdatedClientRevisionIsAConflict() {
        assertThatThrownBy(() -> RevisionGuard.requireMatch(4, 5))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.REVISION_CONFLICT));
    }

    @Test
    void negativeExpectedRevisionIsInvalidInput() {
        assertThatThrownBy(() -> RevisionGuard.requireMatch(-1, 4))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void negativeStoredRevisionRemainsAnInternalFailure() {
        assertThatThrownBy(() -> RevisionGuard.requireMatch(4, -1))
                .isInstanceOf(IllegalStateException.class);
    }
}
