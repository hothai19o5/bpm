package com.x.bpm.support;

import com.x.bpm.process.application.ProcessModelInput;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Test authoring fixture only: same safe config/validator/compiler as the designer. */
public final class RuntimeModelFixture {
    private RuntimeModelFixture() { }
    private record Node(String id,String type,Map<String,Object> step,String defaultFlow) { }
    private record Flow(String id,String source,String target,Map<String,Object> step) { }
    public static ProcessModelInput purchase(String key,String formId,Map<String,Object> managerAssignment,
            Map<String,Object> accountingAssignment,Map<String,Object> directorAssignment,Map<String,Object> purchasingAssignment) throws Exception {
        var fields=Map.<String,Object>of("itemName","READ_ONLY","quantity","READ_ONLY","amount","READ_ONLY","reason","READ_ONLY","proposalAttachments","READ_ONLY");
        var editable=new LinkedHashMap<String,Object>();fields.keySet().forEach(field->editable.put(field,"EDITABLE"));
        var accounting=new LinkedHashMap<>(fields);accounting.put("budgetCode","EDITABLE");accounting.put("budgetConfirmed","EDITABLE");
        var purchasing=new LinkedHashMap<>(fields);purchasing.put("completionNote","EDITABLE");purchasing.put("receivedDate","EDITABLE");purchasing.put("receiptAttachments","EDITABLE");
        var actions=List.of("APPROVE","REJECT","REQUEST_CHANGES");
        var nodes=List.of(new Node("start","startEvent",Map.of("fieldAccess",editable),null),
                new Node("manager","userTask",Map.of("assignment",managerAssignment,"actions",actions,"fieldAccess",fields),null),
                new Node("accounting","userTask",Map.of("assignment",accountingAssignment,"actions",actions,"fieldAccess",accounting),null),
                new Node("budget","exclusiveGateway",Map.of(),"budget_retry"),new Node("threshold","exclusiveGateway",Map.of(),"low_amount"),
                new Node("director","userTask",Map.of("assignment",directorAssignment,"actions",actions,"fieldAccess",fields),null),
                new Node("purchasing","userTask",Map.of("assignment",purchasingAssignment,"actions",List.of("COMPLETE"),"fieldAccess",purchasing),null),
                new Node("correction","userTask",Map.of("assignment",Map.of("strategy","REQUESTER_MANAGER"),"actions",List.of("RESUBMIT"),"fieldAccess",editable,"resetFieldsOnResubmit",List.of("budgetCode","budgetConfirmed")),null),
                new Node("completed","endEvent",Map.of(),null),new Node("rejected","endEvent",Map.of(),null));
        var flows=new ArrayList<Flow>();flows.add(new Flow("submit","start","manager",Map.of()));
        for(var pair:List.of(List.of("manager","accounting"),List.of("accounting","budget"),List.of("director","purchasing"))){
            String source=pair.get(0);flows.add(new Flow(source+"_approve",source,pair.get(1),Map.of("outcome","APPROVE")));
            flows.add(new Flow(source+"_reject",source,"rejected",Map.of("outcome","REJECT")));
            flows.add(new Flow(source+"_changes",source,"correction",Map.of("outcome","REQUEST_CHANGES")));
        }
        flows.add(new Flow("resubmit","correction","manager",Map.of("outcome","RESUBMIT")));
        flows.add(new Flow("budget_ok","budget","threshold",Map.of("condition",Map.of("field","budgetConfirmed","operator","EQ","value",true))));
        flows.add(new Flow("budget_retry","budget","accounting",Map.of("default",true)));
        flows.add(new Flow("high_amount","threshold","director",Map.of("condition",Map.of("field","amount","operator","GE","value","50000000"))));
        flows.add(new Flow("low_amount","threshold","purchasing",Map.of("default",true)));
        flows.add(new Flow("purchase_complete","purchasing","completed",Map.of("outcome","COMPLETE")));
        var json=new ObjectMapper();var steps=new LinkedHashMap<String,Object>();var bindings=new LinkedHashMap<String,Object>();
        var xml=new StringBuilder("<b:definitions xmlns:b=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:bpm=\"https://bpm.local/schema/config/1\" xmlns:d=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" id=\"defs\" targetNamespace=\"local\"><b:process id=\"").append(key).append("\" isExecutable=\"true\">");
        for(var node:nodes){xml.append("<b:").append(node.type()).append(" id=\"").append(node.id()).append("\" name=\"").append(node.id()).append("\"");if(node.defaultFlow()!=null)xml.append(" default=\"").append(node.defaultFlow()).append("\"");xml.append(">");
            if(!node.step().isEmpty()){steps.put(node.id(),node.step());bindings.put(node.id(),Map.of("formVersionId",formId));xml.append(extension(json,Map.of("step",node.step(),"formVersionId",formId)));}
            xml.append("</b:").append(node.type()).append(">");}
        for(var flow:flows){xml.append("<b:sequenceFlow id=\"").append(flow.id()).append("\" sourceRef=\"").append(flow.source()).append("\" targetRef=\"").append(flow.target()).append("\">");
            if(!flow.step().isEmpty()){steps.put(flow.id(),flow.step());if(!flow.step().containsKey("default"))xml.append(extension(json,Map.of("step",flow.step())));}
            xml.append("</b:sequenceFlow>");}
        xml.append("</b:process><d:BPMNDiagram id=\"diagram\"><d:BPMNPlane id=\"plane\" bpmnElement=\"").append(key).append("\">");
        int position=0;for(var node:nodes){xml.append("<d:BPMNShape id=\"s_").append(node.id()).append("\" bpmnElement=\"").append(node.id()).append("\"><dc:Bounds x=\"").append(position++*140).append("\" y=\"100\" width=\"80\" height=\"80\"/></d:BPMNShape>");}
        for(var flow:flows)xml.append("<d:BPMNEdge id=\"e_").append(flow.id()).append("\" bpmnElement=\"").append(flow.id()).append("\"><di:waypoint x=\"80\" y=\"140\"/><di:waypoint x=\"140\" y=\"140\"/></d:BPMNEdge>");
        xml.append("</d:BPMNPlane></d:BPMNDiagram></b:definitions>");return new ProcessModelInput(xml.toString(),steps,bindings);
    }
    private static String extension(ObjectMapper json,Object payload)throws Exception{return "<b:extensionElements><bpm:configuration>"+json.writeValueAsString(payload).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</bpm:configuration></b:extensionElements>";}
}
