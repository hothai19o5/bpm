package com.x.bpm.process.application;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.config.ProcessModuleConfiguration;
import com.x.bpm.process.domain.ProcessCatalogScope;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListProcessDefinitionsUseCaseTest {

    @ParameterizedTest
    @CsvSource({"process.monitor.all,ALL", "process.catalog.read,STARTABLE_FOR_ACTOR"})
    void queryReceivesTheAuthenticatedActorAndPermittedScope(String permission,
                                                            ProcessCatalogScope expectedScope) {
        var actor = new ActorContext("user-001", true, Set.of(permission));
        var query = PageQuery.from(null, null, null);
        var expected = new PageResult<ProcessDefinitionSummary>(List.of(), 0, 20, 0);
        var port = mock(ProcessDefinitionQueryPort.class);
        when(port.findVisible(actor, expectedScope, query)).thenReturn(expected);
        var configuration = new ProcessModuleConfiguration();
        var useCase = configuration.listProcessDefinitionsUseCase(() -> actor,
                configuration.processDefinitionReadPolicy(), port);

        assertThat(useCase.execute(query)).isSameAs(expected);
        verify(port).findVisible(actor, expectedScope, query);
    }

    @ParameterizedTest
    @MethodSource("deniedActors")
    void rejectedActorsNeverReachTheQueryPort(ActorContext actor, ErrorCode expectedCode) {
        var port = mock(ProcessDefinitionQueryPort.class);
        var configuration = new ProcessModuleConfiguration();
        var useCase = configuration.listProcessDefinitionsUseCase(() -> actor,
                configuration.processDefinitionReadPolicy(), port);

        assertThatThrownBy(() -> useCase.execute(PageQuery.from(null, null, null)))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expectedCode));
        verifyNoInteractions(port);
    }

    private static Stream<Arguments> deniedActors() {
        return Stream.of(
                Arguments.of(null, ErrorCode.UNAUTHENTICATED),
                Arguments.of(new ActorContext("inactive", false,
                        Set.of(PermissionCatalog.PROCESS_MONITOR_ALL)), ErrorCode.UNAUTHENTICATED),
                Arguments.of(new ActorContext("employee", true, Set.of()), ErrorCode.FORBIDDEN),
                Arguments.of(new ActorContext("wildcard", true, Set.of("*")), ErrorCode.FORBIDDEN));
    }

    @Test
    void authenticationFailureStopsBeforeTheQuery() {
        var actors = mock(CurrentActorProvider.class);
        var failure = new ApplicationException(ErrorCode.UNAUTHENTICATED, "Token không hợp lệ.");
        when(actors.requireCurrentActor()).thenThrow(failure);
        var port = mock(ProcessDefinitionQueryPort.class);
        var configuration = new ProcessModuleConfiguration();
        var useCase = configuration.listProcessDefinitionsUseCase(actors,
                configuration.processDefinitionReadPolicy(), port);

        assertThatThrownBy(() -> useCase.execute(PageQuery.from(null, null, null))).isSameAs(failure);
        verifyNoInteractions(port);
    }

    @Test
    void infrastructureFailuresAreNotConvertedToEmptySuccessfulPages() {
        var actor = new ActorContext("user-001", true,
                Set.of(PermissionCatalog.PROCESS_MONITOR_ALL));
        var query = PageQuery.from(null, null, null);
        var port = mock(ProcessDefinitionQueryPort.class);
        var failure = new IllegalStateException("test query failure");
        when(port.findVisible(actor, ProcessCatalogScope.ALL, query)).thenThrow(failure);
        var configuration = new ProcessModuleConfiguration();
        var useCase = configuration.listProcessDefinitionsUseCase(() -> actor,
                configuration.processDefinitionReadPolicy(), port);

        assertThatThrownBy(() -> useCase.execute(query)).isSameAs(failure);
    }
}
