package com.x.bpm.process.adapter.flowable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BpmConditionsTest {
    @Test void readsSafeJsonScalarVariableWithoutLosingMoneyPrecision() {
        var json=new ObjectMapper();var execution=mock(DelegateExecution.class);var conditions=new BpmConditions(json);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsString(Map.of("field","amount","operator","GT","type","MONEY_VND","value","9007199254740992")).getBytes(StandardCharsets.UTF_8));
        when(execution.getVariable("bpmFormData")).thenReturn(json.writeValueAsString(Map.of("amount","9007199254740993")));
        assertThat(conditions.test(execution,token)).isTrue();
        when(execution.getVariable("bpmFormData")).thenReturn("not-json");assertThat(conditions.test(execution,token)).isFalse();
    }
    @Test void comparesMoneyAndDecimalsExactlyAtLargeBoundaries(){
        var execution=mock(DelegateExecution.class);when(execution.getVariable("bpmFormData")).thenReturn(Map.of("amount","9007199254740993.25"));
        var json=new ObjectMapper();var conditions=new BpmConditions(json);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsString(Map.of("field","amount","operator","GT","type","DECIMAL","value","9007199254740993.24")).getBytes(StandardCharsets.UTF_8));
        assertThat(conditions.test(execution,token)).isTrue();assertThat(conditions.test(execution,"broken")).isFalse();
        when(execution.getVariable("bpmOutcome")).thenReturn("APPROVE");assertThat(conditions.outcome(execution,"APPROVE")).isTrue();assertThat(conditions.outcome(execution,"REJECT")).isFalse();
    }
}
