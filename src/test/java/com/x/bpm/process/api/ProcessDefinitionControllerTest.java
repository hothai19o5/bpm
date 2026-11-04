package com.x.bpm.process.api;

import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.process.application.ProcessDefinitionQueryPort;
import com.x.bpm.process.application.ProcessDefinitionSummary;
import com.x.bpm.process.config.ProcessModuleConfiguration;
import com.x.bpm.process.domain.ProcessCatalogScope;
import com.x.bpm.shared.api.ApiResponseFactory;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests the catalog controller mapping independently of HTTP/persistence. */
class ProcessDefinitionControllerTest {
    private final Instant now = Instant.parse("2026-11-02T03:00:00Z");
    private final ApiResponseFactory responses = new ApiResponseFactory(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void mapsThePermittedPageIntoPublicDtosAndResponseMetadata() {
        var actor = new ActorContext("user-001", true,
                Set.of(PermissionCatalog.PROCESS_CATALOG_READ));
        var summary = new ProcessDefinitionSummary("process-001", "purchase", "Đề nghị mua sắm",
                3, Instant.parse("2026-11-01T04:00:00Z"));
        var query = new PageQuery(0, 20, "updatedAt,desc");
        var port = mock(ProcessDefinitionQueryPort.class);
        when(port.findVisible(actor, ProcessCatalogScope.STARTABLE_FOR_ACTOR, query))
                .thenReturn(new PageResult<>(List.of(summary), 0, 20, 21));
        var configuration = new ProcessModuleConfiguration();
        var useCase = configuration.listProcessDefinitionsUseCase(() -> actor,
                configuration.processDefinitionReadPolicy(), port);
        var controller = new ProcessDefinitionController(useCase, responses);
        var request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "process-list-001");

        var response = controller.list(null, null, null, request);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.timestamp()).isEqualTo(now);
        assertThat(response.correlationId()).isEqualTo("process-list-001");
        assertThat(response.data().page()).isZero();
        assertThat(response.data().size()).isEqualTo(20);
        assertThat(response.data().totalElements()).isEqualTo(21);
        assertThat(response.data().totalPages()).isEqualTo(2);
        assertThat(response.data().items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("process-001");
            assertThat(item.key()).isEqualTo("purchase");
            assertThat(item.name()).isEqualTo("Đề nghị mua sắm");
            assertThat(item.revision()).isEqualTo(3);
            assertThat(item.updatedAt()).isEqualTo(summary.updatedAt());
        });
        verify(port).findVisible(actor, ProcessCatalogScope.STARTABLE_FOR_ACTOR, query);
    }

    @Test
    void invalidPaginationStopsBeforeAuthenticationAndDataAccess() {
        var actors = mock(CurrentActorProvider.class);
        var port = mock(ProcessDefinitionQueryPort.class);
        var configuration = new ProcessModuleConfiguration();
        var useCase = configuration.listProcessDefinitionsUseCase(actors,
                configuration.processDefinitionReadPolicy(), port);
        var controller = new ProcessDefinitionController(useCase, responses);

        assertThatThrownBy(() -> controller.list(-1, 20, null, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(actors, port);
    }
}
