package com.x.bpm.process.adapter.validation;

import com.x.bpm.process.application.*;
import com.x.bpm.shared.error.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Closed authoring grammar. No engine call can occur from validation. */
@Component
public class BpmnArtifactValidator implements ProcessValidationPort {
    static final String MODEL="http://www.omg.org/spec/BPMN/20100524/MODEL";
    static final String DI="http://www.omg.org/spec/BPMN/20100524/DI";
    static final String DC="http://www.omg.org/spec/DD/20100524/DC";
    static final String WAYPOINT="http://www.omg.org/spec/DD/20100524/DI";
    static final String BPM="https://bpm.local/schema/config/1";
    static final Set<String> NODES=Set.of("startEvent","userTask","exclusiveGateway","endEvent");
    static final Set<String> ACTIONS=Set.of("APPROVE","REJECT","REQUEST_CHANGES","RESUBMIT","COMPLETE");
    private final ObjectMapper payloadJson=tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper json;
    private final ProcessReferences refs;
    public BpmnArtifactValidator(ObjectMapper json,ProcessReferences refs) {this.json=json;this.refs=refs;}
    private record Node(String id,String type,Element xml) { }
    private record Flow(String id,String source,String target) { }
    private static void fail(String field,String id,String message) {throw new ApplicationException(ErrorCode.VALIDATION_FAILED,"Cấu hình quy trình không hợp lệ.",List.of(new ValidationIssue(field,message,id)));}
    private static String id(Element element) {return element.getAttribute("id");}
    private static List<Element> children(Element element) {
        var result=new ArrayList<Element>();for(var child=element.getFirstChild();child!=null;child=child.getNextSibling()) if(child instanceof Element e)result.add(e);return result;
    }
    private static void keys(Map<String,Object> map,Set<String> allowed,String field,String id) {for(String key:map.keySet())if(!allowed.contains(key))fail(field,id,"Thuộc tính không được hỗ trợ: "+key);}
    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value,String field,String id) {if(!(value instanceof Map<?,?>))fail(field,id,"Cần JSON object.");return (Map<String,Object>)value;}
    private static String string(Object value,String field,String id) {if(!(value instanceof String s)||s.isBlank()||s.length()>4000)fail(field,id,"Cần chuỗi hợp lệ.");return (String)value;}
    private static UUID uuid(Object value,String field,String id) {try{String text=string(value,field,id);if(!text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException();return UUID.fromString(text);}catch(IllegalArgumentException ex){fail(field,id,"UUID không hợp lệ.");return null;}}
    private Document parse(String xml) {
        if(xml==null||xml.isBlank()||xml.getBytes(StandardCharsets.UTF_8).length>2097152)fail("bpmnXml",null,"XML rỗng hoặc vượt 2 MB.");
        try {
            var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();builder.setErrorHandler(new DefaultHandler());builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External resources forbidden");});
            return builder.parse(new InputSource(new StringReader(xml)));
        }catch(Exception ex){fail("bpmnXml",null,"XML sai cú pháp hoặc chứa DTD/tài nguyên ngoài.");return null;}
    }
    private static Set<String> attributes(String ns,String tag) {
        if(MODEL.equals(ns))return switch(tag) {
            case "definitions" -> Set.of("id","targetNamespace","exporter","exporterVersion");
            case "process" -> Set.of("id","name","isExecutable");
            case "startEvent","endEvent","userTask" -> Set.of("id","name");
            case "exclusiveGateway" -> Set.of("id","name","default","gatewayDirection");
            case "sequenceFlow" -> Set.of("id","name","sourceRef","targetRef");
            case "incoming","outgoing","extensionElements","documentation" -> Set.of();default -> null;};
        if(BPM.equals(ns)&&tag.equals("configuration"))return Set.of();
        if(DI.equals(ns))return switch(tag) {case "BPMNDiagram" -> Set.of("id","name");case "BPMNPlane","BPMNEdge" -> Set.of("id","bpmnElement");case "BPMNShape" -> Set.of("id","bpmnElement","isMarkerVisible");case "BPMNLabel" -> Set.of();default -> null;};
        if(DC.equals(ns)&&tag.equals("Bounds"))return Set.of("x","y","width","height");
        if(WAYPOINT.equals(ns)&&tag.equals("waypoint"))return Set.of("x","y");return null;
    }
    public ProcessModelInput validate(String key,ProcessModelInput input) { return inspect(key,input,true); }
    public ProcessModelInput validateDraft(String key,ProcessModelInput input) { return inspect(key,input,false); }
    public ProcessModelInput copyToKey(String sourceKey,String targetKey,ProcessModelInput input) {
        var model=validateDraft(sourceKey,input);var document=parse(model.bpmnXml());
        ((Element)document.getElementsByTagNameNS(MODEL,"process").item(0)).setAttribute("id",targetKey);
        var planes=document.getElementsByTagNameNS(DI,"BPMNPlane");
        for(int i=0;i<planes.getLength();i++)((Element)planes.item(i)).setAttribute("bpmnElement",targetKey);
        try {
            var factory=javax.xml.transform.TransformerFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            var output=new java.io.StringWriter();factory.newTransformer().transform(new javax.xml.transform.dom.DOMSource(document),new javax.xml.transform.stream.StreamResult(output));
            return validateDraft(targetKey,new ProcessModelInput(output.toString(),model.stepConfig(),model.formBindings()));
        }catch(ApplicationException ex){throw ex;}catch(Exception ex){fail("bpmnXml",null,"Không nhân bản được XML an toàn.");return null;}
    }
    private ProcessModelInput inspect(String key,ProcessModelInput input,boolean complete) {
        if(input==null)fail("model",null,"Thiếu artifact.");
        var doc=parse(input.bpmnXml());var root=doc.getDocumentElement();
        if(!MODEL.equals(root.getNamespaceURI())||!"definitions".equals(root.getLocalName()))fail("bpmnXml",null,"Cần BPMN definitions.");
        for(var child=doc.getFirstChild();child!=null;child=child.getNextSibling())if(child.getNodeType()==org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE)fail("bpmnXml",null,"Processing instruction không được hỗ trợ.");
        var all=doc.getElementsByTagName("*");if(all.getLength()>10000)fail("bpmnXml",null,"Vượt giới hạn phần tử XML.");
        var ids=new HashSet<String>();
        for(int i=0;i<all.getLength();i++) {
            var e=(Element)all.item(i);var allowed=attributes(e.getNamespaceURI(),e.getLocalName());
            if(allowed==null)fail("bpmnXml",id(e),"Phần tử/extension ngoài tập MVP.");
            String parent=e.getParentNode() instanceof Element pe?pe.getLocalName():"";
            boolean position=switch(e.getLocalName()) {
                case "definitions" -> e==root;
                case "process","BPMNDiagram" -> parent.equals("definitions");
                case "startEvent","userTask","exclusiveGateway","endEvent","sequenceFlow" -> parent.equals("process");
                case "extensionElements" -> Set.of("userTask","exclusiveGateway","sequenceFlow").contains(parent);
                case "configuration" -> parent.equals("extensionElements");
                case "incoming","outgoing" -> NODES.contains(parent);
                case "documentation" -> NODES.contains(parent)||Set.of("sequenceFlow","process").contains(parent);
                case "BPMNPlane" -> parent.equals("BPMNDiagram");
                case "BPMNShape","BPMNEdge" -> parent.equals("BPMNPlane");
                case "BPMNLabel" -> Set.of("BPMNShape","BPMNEdge").contains(parent);
                case "Bounds" -> Set.of("BPMNShape","BPMNLabel").contains(parent);
                case "waypoint" -> parent.equals("BPMNEdge"); default -> false;
            };if(!position)fail("bpmnXml",id(e),"Phần tử sai vị trí trong BPMN.");
            int depth=0;for(var ancestor=e.getParentNode();ancestor!=null;ancestor=ancestor.getParentNode())depth++;if(depth>32)fail("bpmnXml",id(e),"XML lồng quá sâu.");
            for(var child=e.getFirstChild();child!=null;child=child.getNextSibling())if(child.getNodeType()==org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE)fail("bpmnXml",id(e),"Processing instruction không được hỗ trợ.");
            if(MODEL.equals(e.getNamespaceURI())&&e.getLocalName().equals("documentation")&&(e.getTextContent().contains("${")||e.getTextContent().contains("#{")))fail("documentation",id(e),"Không cho phép expression trong documentation.");
            var attrs=e.getAttributes();for(int a=0;a<attrs.getLength();a++){var attr=attrs.item(a);if(XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI()))continue;
                if(attr.getNodeValue().contains("${")||attr.getNodeValue().contains("#{"))fail("bpmnXml",id(e),"Không cho phép expression trong thuộc tính XML.");
                if(attr.getNamespaceURI()!=null||!allowed.contains(attr.getNodeName()))fail("bpmnXml",id(e),"Thuộc tính ngoài allowlist: "+attr.getNodeName());}
            if(e.hasAttribute("id")&&(!id(e).matches("[A-Za-z_][A-Za-z0-9_.-]{0,254}")||!ids.add(id(e))))fail("id",id(e),"ID sai hoặc bị trùng.");
            if(DC.equals(e.getNamespaceURI())||WAYPOINT.equals(e.getNamespaceURI())) for(int a=0;a<attrs.getLength();a++){var attr=attrs.item(a);if(attr.getNamespaceURI()!=null)continue;try{double n=Double.parseDouble(attr.getNodeValue());if(!Double.isFinite(n)||Math.abs(n)>1000000||((attr.getNodeName().equals("width")||attr.getNodeName().equals("height"))&&n<=0))throw new IllegalArgumentException();}catch(Exception ex){fail("di",id(e),"Tọa độ/kích thước DI không hợp lệ.");}}
        }
        var processes=doc.getElementsByTagNameNS(MODEL,"process");if(processes.getLength()!=1)fail("process",null,"Cần đúng một process.");
        var process=(Element)processes.item(0);if(process.getParentNode()!=root||!id(process).equals(key)||!"true".equals(process.getAttribute("isExecutable")))fail("process",id(process),"Process ID phải khớp key và executable=true.");
        var nodes=new LinkedHashMap<String,Node>();var flows=new LinkedHashMap<String,Flow>();
        for(var e:children(process)) {
            if(!MODEL.equals(e.getNamespaceURI()))fail("process",id(e),"Phần tử sai vị trí.");
            if(NODES.contains(e.getLocalName())) {if(id(e).isEmpty())fail("id",null,"Node thiếu ID.");nodes.put(id(e),new Node(id(e),e.getLocalName(),e));}
            else if(e.getLocalName().equals("sequenceFlow")){if(id(e).isEmpty())fail("id",null,"Flow thiếu ID.");flows.put(id(e),new Flow(id(e),e.getAttribute("sourceRef"),e.getAttribute("targetRef")));}
            else if(!e.getLocalName().equals("documentation"))fail("process",id(e),"Phần tử ngoài tập process MVP.");
        }
        if(nodes.size()+flows.size()>500)fail("process",key,"Vượt 500 node/flow.");
        for(var flow:flows.values())if(!nodes.containsKey(flow.source)||!nodes.containsKey(flow.target))fail("sourceRef/targetRef",flow.id,"Flow tham chiếu node không tồn tại.");
        var configuredOwners=new HashSet<String>();var steps=new LinkedHashMap<String,Object>();var bindings=new LinkedHashMap<String,Object>();
        for(int i=0;i<all.getLength();i++) {
            var e=(Element)all.item(i);if(!BPM.equals(e.getNamespaceURI()))continue;
            if(!(e.getParentNode() instanceof Element ext)||!MODEL.equals(ext.getNamespaceURI())||!ext.getLocalName().equals("extensionElements")||!(ext.getParentNode() instanceof Element owner)||!Set.of("userTask","exclusiveGateway","sequenceFlow").contains(owner.getLocalName())||children(ext).size()!=1||!children(e).isEmpty())fail("extension",null,"Extension sai vị trí hoặc bị trùng.");
            var owner=(Element)e.getParentNode().getParentNode();String ownerId=id(owner);
            if(!configuredOwners.add(ownerId))fail("extension",ownerId,"Cấu hình bị trùng.");
            Map<String,Object> payload;try{payload=payloadJson.readValue(e.getTextContent(),Map.class);}catch(Exception ex){fail("extension",ownerId,"JSON metadata không hợp lệ.");return null;}
            keys(payload,Set.of("step","formVersionId"),"extension",ownerId);var step=object(payload.get("step"),"step",ownerId);
            Set<String> stepKeys=owner.getLocalName().equals("userTask")?Set.of("description","assignment","actions","fieldAccess","deadline"):owner.getLocalName().equals("sequenceFlow")?Set.of("description","condition","outcome"):Set.of("description");
            keys(step,stepKeys,"step",ownerId);if(step.containsKey("description")&&(!(step.get("description") instanceof String text)||text.length()>4000))fail("description",ownerId,"Mô tả không hợp lệ.");for(var entry:Map.of("assignment",Set.of("strategy","specificUserId","departmentId","roleId"),"deadline",Set.of("value","unit"),"condition",Set.of("field","operator","value")).entrySet())if(step.containsKey(entry.getKey()))keys(object(step.get(entry.getKey()),entry.getKey(),ownerId),entry.getValue(),entry.getKey(),ownerId);
            for(String nested:List.of("assignment","deadline","condition"))if(step.containsKey(nested)){
                var value=object(step.get(nested),nested,ownerId);
                for(var property:value.entrySet()){
                    boolean scalar=property.getKey().equals("value");Object data=property.getValue();
                    if(scalar ? (nested.equals("deadline") ? !(data instanceof Number) : !(data instanceof String||data instanceof Number||data instanceof Boolean)) : !(data instanceof String))fail(nested+"."+property.getKey(),ownerId,"Kiểu dữ liệu cấu hình không hợp lệ.");
                }
            }
            if(step.containsKey("actions")&&(!(step.get("actions") instanceof List<?> list)||list.stream().anyMatch(a->!(a instanceof String)||!ACTIONS.contains(a))))fail("actions",ownerId,"Action không hợp lệ.");
            if(step.containsKey("fieldAccess")){var access=object(step.get("fieldAccess"),"fieldAccess",ownerId);for(var accessValue:access.values())if(!Set.of("HIDDEN","READ_ONLY","EDITABLE").contains(Objects.toString(accessValue,"")))fail("fieldAccess",ownerId,"Quyền trường không hợp lệ.");}
            if(!step.isEmpty())steps.put(ownerId,new LinkedHashMap<>(step));
            if(payload.containsKey("formVersionId")){if(!owner.getLocalName().equals("userTask"))fail("formVersionId",ownerId,"Chỉ UserTask được gắn form.");bindings.put(ownerId,Map.of("formVersionId",uuid(payload.get("formVersionId"),"formVersionId",ownerId).toString()));}
        }
        for(var node:nodes.values())if(node.xml.hasAttribute("default")) {
            String flowId=node.xml.getAttribute("default");if(!node.type.equals("exclusiveGateway")||!flows.containsKey(flowId)||!flows.get(flowId).source.equals(node.id))fail("default",node.id,"Default phải là flow đi ra từ XOR.");
            var step=new LinkedHashMap<>(object(steps.getOrDefault(flowId,Map.of()),"step",flowId));step.put("default",true);steps.put(flowId,step);
        }
        if(input.stepConfig()==null||input.formBindings()==null||!json.valueToTree(steps).equals(json.valueToTree(input.stepConfig()))||!json.valueToTree(bindings).equals(json.valueToTree(input.formBindings())))fail("metadata",null,"Projection metadata không khớp XML.");
        var planes=doc.getElementsByTagNameNS(DI,"BPMNPlane");
        if(planes.getLength()!=1||!((Element)planes.item(0)).getAttribute("bpmnElement").equals(key))fail("di",key,"Cần một DI plane đúng process.");
        var shapes=new HashSet<String>();var edges=new HashSet<String>();
        for(int i=0;i<all.getLength();i++){var e=(Element)all.item(i);if(DI.equals(e.getNamespaceURI())&&Set.of("BPMNShape","BPMNEdge").contains(e.getLocalName())){
            String ref=e.getAttribute("bpmnElement");boolean shape=e.getLocalName().equals("BPMNShape");
            if(!(shape?nodes:flows).containsKey(ref)||!(shape?shapes:edges).add(ref))fail("di",ref,"DI tham chiếu sai hoặc bị trùng.");
            if(shape&&e.hasAttribute("isMarkerVisible")&&(!nodes.get(ref).type.equals("exclusiveGateway")||!Set.of("true","false").contains(e.getAttribute("isMarkerVisible"))))fail("di",ref,"Marker XOR cần giá trị boolean.");
            if(shape&&children(e).stream().filter(c->DC.equals(c.getNamespaceURI())&&c.getLocalName().equals("Bounds")).count()!=1)fail("di",ref,"Shape cần một Bounds.");
            if(!shape&&children(e).stream().filter(c->WAYPOINT.equals(c.getNamespaceURI())&&c.getLocalName().equals("waypoint")).count()<2)fail("di",ref,"Edge cần ít nhất hai waypoint.");
        }}
        if(!shapes.equals(nodes.keySet())||!edges.equals(flows.keySet()))fail("di",key,"Thiếu DI node/flow.");
        if(complete){topology(nodes,flows);semantics(nodes,flows,steps,bindings);}
        return new ProcessModelInput(input.bpmnXml(),steps,bindings);
    }
    /** Compile only fixed internal expressions, then inspect the exact serialized XML. */
    public String prepareDeployment(String key, ProcessModelInput input) {
        var model=validate(key,input);var document=parse(model.bpmnXml());
        var expected=new LinkedHashMap<String,String>();
        var elements=document.getElementsByTagNameNS(MODEL,"sequenceFlow");
        for(int i=0;i<elements.getLength();i++) {
            var element=(Element)elements.item(i);var step=object(model.stepConfig().getOrDefault(id(element),Map.of()),"step",id(element));
            String expression=null;
            if(step.containsKey("outcome"))expression="${bpmConditions.outcome(execution, '"+step.get("outcome")+"')}";
            else if(step.containsKey("condition")) {
                var condition=new LinkedHashMap<>(object(step.get("condition"),"condition",id(element)));
                var binding=object(model.formBindings().values().iterator().next(),"formBindings",null);
                var schema=refs.publishedForm(uuid(binding.get("formVersionId"),"formVersionId",null)).orElseThrow();
                var field=object(object(schema.get("properties"),"schema",null).get(condition.get("field")),"schema",null);
                condition.put("type",field.get("x-field-type"));
                var token=Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsString(condition).getBytes(StandardCharsets.UTF_8));
                expression="${bpmConditions.test(execution, '"+token+"')}";
            }
            if(expression!=null){var child=document.createElementNS(MODEL,"conditionExpression");child.setTextContent(expression);element.appendChild(child);expected.put(id(element),expression);}
        }
        try {
            var factory=javax.xml.transform.TransformerFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            var output=new java.io.StringWriter();factory.newTransformer().transform(new javax.xml.transform.dom.DOMSource(document),new javax.xml.transform.stream.StreamResult(output));
            String xml=output.toString();var checked=parse(xml);var expressions=checked.getElementsByTagNameNS(MODEL,"conditionExpression");
            if(expressions.getLength()!=expected.size())fail("deployment",null,"Sai số biểu thức triển khai.");
            for(int i=0;i<expressions.getLength();i++){var e=(Element)expressions.item(i);if(!(e.getParentNode() instanceof Element parent)||!Objects.equals(expected.get(id(parent)),e.getTextContent()))fail("deployment",null,"Biểu thức triển khai không thuộc compiler.");}
            return xml;
        } catch(ApplicationException ex){throw ex;}catch(Exception ex){fail("deployment",null,"Không tạo được artifact triển khai an toàn.");return null;}
    }
    private static List<Flow> outgoing(String id,Map<String,Flow> flows){return flows.values().stream().filter(f->f.source.equals(id)).toList();}
    private static Set<String> reach(String start,Map<String,Flow> flows,boolean reverse,boolean skipTasks,Map<String,Node> nodes) {
        var visited=new HashSet<String>();var queue=new ArrayDeque<String>();queue.add(start);while(!queue.isEmpty()){String id=queue.remove();if(!visited.add(id))continue;if(skipTasks&&nodes.get(id).type.equals("userTask"))continue;
            for(var flow:flows.values())if((reverse?flow.target:flow.source).equals(id))queue.add(reverse?flow.source:flow.target);}
        return visited;
    }
    private static void topology(Map<String,Node> nodes,Map<String,Flow> flows){
        var starts=nodes.values().stream().filter(n->n.type.equals("startEvent")).toList();var ends=nodes.values().stream().filter(n->n.type.equals("endEvent")).toList();
        if(starts.size()!=1||ends.isEmpty())fail("process",null,"Cần đúng một start và ít nhất một end.");
        String start=starts.getFirst().id;var reachable=reach(start,flows,false,false,nodes);var exits=new HashSet<String>();for(var end:ends)exits.addAll(reach(end.id,flows,true,false,nodes));
        for(var n:nodes.values()){
            var out=outgoing(n.id,flows);long incoming=flows.values().stream().filter(f->f.target.equals(n.id)).count();
            if(!reachable.contains(n.id)||!exits.contains(n.id))fail("path",n.id,"Node phải tới được từ start và có đường ra end.");
            if(n.type.equals("startEvent")&&(incoming!=0||out.size()!=1))fail("path",n.id,"Start cần một flow ra và không có flow vào.");
            if(n.type.equals("endEvent")&&(!out.isEmpty()||incoming==0))fail("path",n.id,"End không có flow ra.");
            if(n.type.equals("exclusiveGateway")&&(incoming==0||out.isEmpty()))fail("path",n.id,"Gateway thiếu kết nối.");
            // Any cycle in the graph with wait-state nodes removed is an automatic infinite-loop risk.
            if(!n.type.equals("userTask"))for(var f:out)if(reach(f.target,flows,false,true,nodes).contains(n.id))fail("cycle",n.id,"Vòng lặp phải đi qua UserTask và có đường thoát.");
            var declaredIn=children(n.xml).stream().filter(e->e.getLocalName().equals("incoming")).map(Element::getTextContent).toList();
            var declaredOut=children(n.xml).stream().filter(e->e.getLocalName().equals("outgoing")).map(Element::getTextContent).toList();
            for(String id:declaredIn)if(!flows.containsKey(id)||!flows.get(id).target.equals(n.id))fail("incoming",n.id,"Incoming tham chiếu sai.");
            for(String id:declaredOut)if(!flows.containsKey(id)||!flows.get(id).source.equals(n.id))fail("outgoing",n.id,"Outgoing tham chiếu sai.");
        }
    }
    private void semantics(Map<String,Node> nodes,Map<String,Flow> flows,Map<String,Object> steps,Map<String,Object> bindings) {
        var schemas=new LinkedHashMap<String,Map<String,Object>>();
        for(var node:nodes.values())if(node.type.equals("userTask")) {
            var step=object(steps.getOrDefault(node.id,Map.of()),"step",node.id);
            if(step.containsKey("description")&&(!(step.get("description") instanceof String text)||text.length()>4000))fail("description",node.id,"Mô tả quá dài hoặc sai kiểu.");
            var assignment=object(step.get("assignment"),"assignment",node.id);keys(assignment,Set.of("strategy","specificUserId","departmentId","roleId"),"assignment",node.id);
            String strategy=string(assignment.get("strategy"),"assignment.strategy",node.id);
            Set<String> targets=switch(strategy){case "SPECIFIC_USER"->Set.of("specificUserId");case "DEPARTMENT"->Set.of("departmentId");case "ROLE"->Set.of("departmentId","roleId");case "REQUESTER_MANAGER"->Set.of();default->{fail("assignment.strategy",node.id,"Strategy không hợp lệ.");yield Set.of();}};
            for(String target:List.of("specificUserId","departmentId","roleId")){if(targets.contains(target)){if(!refs.active(target,uuid(assignment.get(target),"assignment."+target,node.id)))fail("assignment."+target,node.id,"Target không tồn tại hoặc INACTIVE.");}else if(assignment.containsKey(target))fail("assignment",node.id,"Target không thuộc strategy.");}
            var binding=object(bindings.get(node.id),"formVersionId",node.id);String formId=string(binding.get("formVersionId"),"formVersionId",node.id);
            var schema=refs.publishedForm(uuid(formId,"formVersionId",node.id)).orElseThrow(()->new ApplicationException(ErrorCode.VALIDATION_FAILED,"Form không được phép chọn.",List.of(new ValidationIssue("formVersionId","Cần form ACTIVE/PUBLISHED.",node.id))));schemas.put(formId,schema);
            if(schemas.size()>1)fail("formVersionId",node.id,"MVP sử dụng một form version chung.");
            var fields=object(schema.get("properties"),"schema",node.id);var access=object(step.getOrDefault("fieldAccess",Map.of()),"fieldAccess",node.id);
            for(var entry:access.entrySet())if(!fields.containsKey(entry.getKey())||!Set.of("HIDDEN","READ_ONLY","EDITABLE").contains(Objects.toString(entry.getValue(),"")))fail("fieldAccess."+entry.getKey(),node.id,"Quyền trường sai hoặc field không tồn tại.");
            if(step.containsKey("deadline")){var deadline=object(step.get("deadline"),"deadline",node.id);keys(deadline,Set.of("value","unit"),"deadline",node.id);Object value=deadline.get("value");if(!(value instanceof Number number)||number.doubleValue()!=number.longValue()||number.longValue()<1||number.longValue()>87600||!Set.of("HOURS","DAYS").contains(Objects.toString(deadline.get("unit"),"")))fail("deadline",node.id,"Hạn xử lý không hợp lệ.");}
            if(!(step.get("actions") instanceof List<?>))fail("actions",node.id,"Cần danh sách action.");var actions=(List<?>)step.get("actions");
            if(actions.isEmpty()||new HashSet<>(actions).size()!=actions.size()||actions.stream().anyMatch(a->!(a instanceof String)||!ACTIONS.contains(a)))fail("actions",node.id,"Action không hợp lệ hoặc bị trùng.");
            for(var action:actions)if(outgoing(node.id,flows).stream().filter(f->action.equals(object(steps.getOrDefault(f.id,Map.of()),"step",f.id).get("outcome"))).count()!=1)fail("actions",node.id,"Action cần đúng một đường ra: "+action);
            for(var flow:outgoing(node.id,flows))if(!actions.contains(object(steps.getOrDefault(flow.id,Map.of()),"step",flow.id).get("outcome")))fail("outcome",flow.id,"Luồng từ task cần outcome thuộc action được cấp.");
        }
        for(var flow:flows.values()){
            var step=object(steps.getOrDefault(flow.id,Map.of()),"step",flow.id);var source=nodes.get(flow.source);
            if(step.containsKey("outcome")&&!source.type.equals("userTask"))fail("outcome",flow.id,"Outcome chỉ dùng trên flow từ UserTask.");
            if(step.containsKey("condition")){
                if(!source.type.equals("exclusiveGateway")||Boolean.TRUE.equals(step.get("default")))fail("condition",flow.id,"Condition chỉ dành cho nhánh XOR không mặc định.");
                var condition=object(step.get("condition"),"condition",flow.id);keys(condition,Set.of("field","operator","value"),"condition",flow.id);String field=string(condition.get("field"),"condition.field",flow.id);
                if(schemas.isEmpty())fail("condition.field",flow.id,"Thiếu schema form.");var properties=object(schemas.values().iterator().next().get("properties"),"properties",flow.id);
                var definition=object(properties.get(field),"condition.field",flow.id);String type=string(definition.get("x-field-type"),"condition.field",flow.id);String operator=string(condition.get("operator"),"condition.operator",flow.id);
                if(!Set.of("EQ","NE","GT","GE","LT","LE").contains(operator)||Set.of("ATTACHMENT","MULTI_SELECT").contains(type)||(!Set.of("INTEGER","DECIMAL","MONEY_VND","DATE").contains(type)&&!Set.of("EQ","NE").contains(operator)))fail("condition.operator",flow.id,"Toán tử không hợp kiểu trường.");
                Object value=condition.get("value");boolean valid=switch(type){
                    case "CHECKBOX" -> value instanceof Boolean;
                    case "INTEGER" -> value instanceof Number n&&n.doubleValue()==n.longValue()&&Math.abs(n.doubleValue())<=9007199254740991d;
                    case "DECIMAL" -> value instanceof String text&&text.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");
                    case "MONEY_VND" -> value instanceof String text&&text.matches("0|[1-9][0-9]*");
                    case "DATE" -> {try{yield value instanceof String text&&LocalDate.parse(text).toString().equals(text);}catch(Exception ex){yield false;}}
                    default -> value instanceof String text&&text.length()<=4000;
                };if(!valid||(definition.get("enum") instanceof List<?> options&&!options.contains(value)))fail("condition.value",flow.id,"Giá trị điều kiện sai kiểu hoặc không thuộc enum.");
            }
        }
        for(var node:nodes.values())if(node.type.equals("exclusiveGateway")){
            var outgoing=outgoing(node.id,flows);if(outgoing.size()>1){if(!node.xml.hasAttribute("default"))fail("default",node.id,"XOR phân nhánh cần default.");for(var f:outgoing)if(!f.id.equals(node.xml.getAttribute("default"))&&!object(steps.getOrDefault(f.id,Map.of()),"step",f.id).containsKey("condition"))fail("condition",f.id,"Nhánh XOR cần điều kiện.");}
        }
    }
}
