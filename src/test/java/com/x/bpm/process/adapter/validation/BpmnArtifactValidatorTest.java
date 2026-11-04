package com.x.bpm.process.adapter.validation;
import com.x.bpm.process.application.*;
import com.x.bpm.shared.error.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BpmnArtifactValidatorTest {
    final ProcessReferences refs=mock(ProcessReferences.class);
    final ObjectMapper json=new ObjectMapper();
    final BpmnArtifactValidator validator=new BpmnArtifactValidator(json,refs);
    static final String FORM="11111111-1111-1111-1111-111111111111";
    @BeforeEach void setup(){when(refs.publishedForm(UUID.fromString(FORM))).thenReturn(Optional.of(Map.of("properties",Map.of("amount",Map.of("x-field-type","MONEY_VND")))));when(refs.active(anyString(),any())).thenReturn(true);}
    ProcessModelInput model(Map<String,Object> step) throws Exception {
        String payload=json.writeValueAsString(Map.of("step",step,"formVersionId",FORM)).replace("&","&amp;").replace("<","&lt;");
        String xml="""
            <b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpm="https://bpm.local/schema/config/1" xmlns:d="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="defs" targetNamespace="local">
            <b:process id="purchase" isExecutable="true"><b:startEvent id="start"/><b:userTask id="review"><b:extensionElements><bpm:configuration>%s</bpm:configuration></b:extensionElements></b:userTask><b:endEvent id="end"/>
            <b:sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
            <b:sequenceFlow id="f2" sourceRef="review" targetRef="end"><b:extensionElements><bpm:configuration>{"step":{"outcome":"APPROVE"}}</bpm:configuration></b:extensionElements></b:sequenceFlow></b:process>
            <d:BPMNDiagram id="diagram"><d:BPMNPlane id="plane" bpmnElement="purchase">
            <d:BPMNShape id="s1" bpmnElement="start"><dc:Bounds x="0" y="0" width="36" height="36"/></d:BPMNShape>
            <d:BPMNShape id="s2" bpmnElement="review"><dc:Bounds x="100" y="0" width="100" height="80"/></d:BPMNShape>
            <d:BPMNShape id="s3" bpmnElement="end"><dc:Bounds x="250" y="0" width="36" height="36"/></d:BPMNShape>
            <d:BPMNEdge id="e1" bpmnElement="f1"><di:waypoint x="36" y="18"/><di:waypoint x="100" y="18"/></d:BPMNEdge>
            <d:BPMNEdge id="e2" bpmnElement="f2"><di:waypoint x="200" y="18"/><di:waypoint x="250" y="18"/></d:BPMNEdge>
            </d:BPMNPlane></d:BPMNDiagram></b:definitions>
            """.formatted(payload);
        return new ProcessModelInput(xml,Map.of("review",step,"f2",Map.of("outcome","APPROVE")),Map.of("review",Map.of("formVersionId",FORM)));
    }
    Map<String,Object> validStep(){return Map.of("assignment",Map.of("strategy","REQUESTER_MANAGER"),"actions",List.of("APPROVE"),"fieldAccess",Map.of("amount","READ_ONLY"),"deadline",Map.of("value",3,"unit","DAYS"));}
    @Test void compilesOnlyFixedExpressionsAndRejectsAuthoredExpressions() throws Exception {
        var input=model(validStep());var compiled=validator.prepareDeployment("purchase",input);
        assertThat(compiled).contains("bpmConditions.outcome(execution, 'APPROVE')").contains("conditionExpression");
        assertInvalid(new ProcessModelInput(compiled,input.stepConfig(),input.formBindings()));
    }
    @Test void acceptsValidAuthoringArtifactWithoutChangingIt() throws Exception {var input=model(validStep());assertThat(validator.validate("purchase",input)).isEqualTo(input);}
    @ParameterizedTest @MethodSource("attacks") void rejectsXmlBypassingTheDesigner(String attack) throws Exception {
        var input=model(validStep());String xml=switch(attack){
            case "task-name" -> input.bpmnXml().replace("<b:userTask id=\"review\">","<b:userTask id=\"review\" name=\"${evil.call()}\">");
            case "documentation" -> input.bpmnXml().replace("<b:userTask id=\"review\">","<b:userTask id=\"review\"><b:documentation>${evil.call()}</b:documentation>");
            case "pi" -> "<?xml-stylesheet type=\"text/xsl\" href=\"http://127.0.0.1:9/external\"?>"+input.bpmnXml();
            case "dtd" -> "<!DOCTYPE defs [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+input.bpmnXml();
            case "entity" -> "<!DOCTYPE defs [<!ENTITY x SYSTEM 'http://127.0.0.1:9/never'>]>"+input.bpmnXml();
            case "script" -> input.bpmnXml().replace("userTask","scriptTask");
            case "service" -> input.bpmnXml().replace("userTask","serviceTask");
            case "listener" -> input.bpmnXml().replace("<b:startEvent id=\"start\"/>","<b:startEvent id=\"start\"><b:executionListener/></b:startEvent>");
            case "expression" -> input.bpmnXml().replace("<b:startEvent", "<b:startEvent xmlns:f=\"http://flowable.org/bpmn\" f:class=\"evil\"");
            case "duplicate" -> input.bpmnXml().replace("id=\"end\"","id=\"start\"");
            case "reference" -> input.bpmnXml().replace("targetRef=\"end\"","targetRef=\"missing\"");
            case "parallel" -> input.bpmnXml().replace("endEvent","parallelGateway");
            case "nested" -> input.bpmnXml().replace("<b:endEvent id=\"end\"/>","<b:endEvent id=\"end\"><b:userTask id=\"hidden\"/></b:endEvent>");
            case "di" -> input.bpmnXml().replace("bpmnElement=\"review\"","bpmnElement=\"missing\"");
            case "coordinate" -> input.bpmnXml().replace("width=\"100\"","width=\"NaN\"");
            case "oversize" -> " ".repeat(2097153);
            case "key" -> input.bpmnXml().replace("id=\"purchase\"","id=\"other\"");
            default -> input.bpmnXml().replace("isExecutable=\"true\"","isExecutable=\"false\"");
        };assertInvalid(new ProcessModelInput(xml,input.stepConfig(),input.formBindings()));
    }
    static Stream<String> attacks(){return Stream.of("task-name","documentation","pi","dtd","entity","script","service","listener","expression","duplicate","reference","parallel","nested","di","coordinate","oversize","key","executable");}
    @Test void rejectsMirrorMismatchAndReturnsElementFieldDetails() throws Exception {var input=model(validStep());assertInvalid(new ProcessModelInput(input.bpmnXml(),Map.of(),input.formBindings()));}
    @Test void rejectsRetiredOrInactiveForm() throws Exception {when(refs.publishedForm(any())).thenReturn(Optional.empty());assertInvalid(model(validStep()));}
    @Test void rejectsMissingActionPath() throws Exception {var step=new HashMap<>(validStep());step.put("actions",List.of("APPROVE","REJECT"));assertInvalid(model(step));}
    @Test void rejectsUnknownFieldAndWrongPermission() throws Exception {var step=new HashMap<>(validStep());step.put("fieldAccess",Map.of("unknown","EDITABLE"));assertInvalid(model(step));}
    @Test void rejectsStrategyTargetMismatch() throws Exception {var step=new HashMap<>(validStep());step.put("assignment",Map.of("strategy","REQUESTER_MANAGER","specificUserId",FORM));assertInvalid(model(step));}
    @Test void rejectsInactiveAssignmentTarget() throws Exception {when(refs.active(anyString(),any())).thenReturn(false);var step=new HashMap<>(validStep());step.put("assignment",Map.of("strategy","SPECIFIC_USER","specificUserId",FORM));assertInvalid(model(step));}
    @Test void rejectsMalformedDeadlineWithoutInternalError() throws Exception {var step=new HashMap<>(validStep());step.put("deadline",Map.of("value",3));assertInvalid(model(step));}
    @Test void rejectsOrphanNodeAndNoExit() throws Exception {var input=model(validStep());assertInvalid(new ProcessModelInput(input.bpmnXml().replace("sourceRef=\"review\" targetRef=\"end\"","sourceRef=\"review\" targetRef=\"review\""),input.stepConfig(),input.formBindings()));}
    ProcessModelInput xorModel() throws Exception {
        var input=model(validStep());
        var condition=Map.<String,Object>of("field","amount","operator","GE","value","50000000");
        String extra="<b:exclusiveGateway id=\"xor\" default=\"fallback\"/><b:sequenceFlow id=\"high\" sourceRef=\"xor\" targetRef=\"end\"><b:extensionElements><bpm:configuration>"+json.writeValueAsString(Map.of("step",Map.of("condition",condition)))+"</bpm:configuration></b:extensionElements></b:sequenceFlow><b:sequenceFlow id=\"fallback\" sourceRef=\"xor\" targetRef=\"end\"/>";
        String di="<d:BPMNShape id=\"xor_di\" bpmnElement=\"xor\"><dc:Bounds x=\"200\" y=\"0\" width=\"50\" height=\"50\"/></d:BPMNShape>";
        for(String id:List.of("high","fallback"))di+="<d:BPMNEdge id=\""+id+"_di\" bpmnElement=\""+id+"\"><di:waypoint x=\"200\" y=\"20\"/><di:waypoint x=\"250\" y=\"20\"/></d:BPMNEdge>";
        String xml=input.bpmnXml().replace("sourceRef=\"review\" targetRef=\"end\"","sourceRef=\"review\" targetRef=\"xor\"").replace("</b:process>",extra+"</b:process>").replace("</d:BPMNPlane>",di+"</d:BPMNPlane>");
        var steps=new HashMap<>(input.stepConfig());steps.put("high",Map.of("condition",condition));steps.put("fallback",Map.of("default",true));return new ProcessModelInput(xml,steps,input.formBindings());
    }
    @Test void validatesAndCompilesTypedXorWithDefaultAndExactThreshold() throws Exception {
        var input=xorModel();assertThat(validator.validate("purchase",input)).isEqualTo(input);
        String compiled=validator.prepareDeployment("purchase",input);assertThat(compiled).contains("bpmConditions.test(execution").contains("default=\"fallback\"");
        var execution=mock(org.flowable.engine.delegate.DelegateExecution.class);
        var helper=new com.x.bpm.process.adapter.flowable.BpmConditions(json);
        String token=compiled.split("bpmConditions.test\\(execution, '")[1].split("'")[0];
        for(String amount:List.of("49999999","50000000","50000001")){when(execution.getVariable("bpmFormData")).thenReturn(Map.of("amount",amount));assertThat(helper.test(execution,token)).isEqualTo(!amount.equals("49999999"));}
    }
    @Test void rejectsAutomaticCycleEvenWhenAnExitExists() throws Exception {var input=xorModel();assertInvalid(new ProcessModelInput(input.bpmnXml().replace("id=\"fallback\" sourceRef=\"xor\" targetRef=\"end\"","id=\"fallback\" sourceRef=\"xor\" targetRef=\"xor\""),input.stepConfig(),input.formBindings()));}
    @Test void rejectsUnknownOrWronglyTypedXorField() throws Exception {
        var input=xorModel();String xml=input.bpmnXml().replace("\"field\":\"amount\"","\"field\":\"missing\"");var steps=new HashMap<>(input.stepConfig());steps.put("high",Map.of("condition",Map.of("field","missing","operator","GE","value","50000000")));assertInvalid(new ProcessModelInput(xml,steps,input.formBindings()));
    }
    void assertInvalid(ProcessModelInput input){assertThatThrownBy(()->validator.validate("purchase",input)).isInstanceOfSatisfying(ApplicationException.class,ex->{assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);assertThat(ex.details()).isNotEmpty();assertThat(ex.details().getFirst().field()).isNotBlank();});}
}
