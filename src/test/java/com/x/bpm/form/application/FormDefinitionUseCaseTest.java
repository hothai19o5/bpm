package com.x.bpm.form.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.shared.error.ApplicationException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FormDefinitionUseCaseTest {
    @Test
    void deniesReadAndCreateBeforeAccessingStorage() {
        var store = mock(FormStore.class);
        var transactions = mock(FormTransactionRunner.class);
        var useCase = new FormDefinitionUseCase(store, transactions,
                () -> new ActorContext(UUID.randomUUID().toString(), true, Set.of()),
                new AuthorizationPolicy(), (a, b, c, d) -> { });
        assertThatThrownBy(() -> useCase.create(null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> useCase.version(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ApplicationException.class);
        verifyNoInteractions(store, transactions);
    }
}
