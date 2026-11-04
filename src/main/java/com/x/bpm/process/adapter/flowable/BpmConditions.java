package com.x.bpm.process.adapter.flowable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
/** Only this fixed helper is emitted by the trusted compiler; no user EL is evaluated. */
@Component("bpmConditions")
public class BpmConditions {
    private final ObjectMapper json;
    public BpmConditions(ObjectMapper json){this.json=json;}
    public boolean outcome(DelegateExecution execution,String expected){return expected.equals(execution.getVariable("bpmOutcome"));}
    public boolean test(DelegateExecution execution,String token){
        try {
            Map<?,?> condition=json.readValue(new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8),Map.class);
            Object values=execution.getVariable("bpmFormData");if(!(values instanceof Map<?,?> data)||!data.containsKey(condition.get("field")))return false;
            Object left=data.get(condition.get("field")),right=condition.get("value");if(left==null||right==null)return false;
            int comparison=switch(String.valueOf(condition.get("type"))){
                case "INTEGER","DECIMAL","MONEY_VND" -> new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
                case "DATE" -> LocalDate.parse(left.toString()).compareTo(LocalDate.parse(right.toString()));
                case "CHECKBOX" -> {if(!(left instanceof Boolean)||!(right instanceof Boolean))yield Integer.MIN_VALUE;yield Boolean.compare((Boolean)left,(Boolean)right);}
                default -> left.toString().compareTo(right.toString());
            };
            if(comparison==Integer.MIN_VALUE)return false;
            return switch(String.valueOf(condition.get("operator"))){case "EQ"->comparison==0;case "NE"->comparison!=0;case "GT"->comparison>0;case "GE"->comparison>=0;case "LT"->comparison<0;case "LE"->comparison<=0;default->false;};
        }catch(Exception ex){return false;}
    }
}
