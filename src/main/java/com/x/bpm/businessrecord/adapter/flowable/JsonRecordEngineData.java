package com.x.bpm.businessrecord.adapter.flowable;
import com.x.bpm.businessrecord.application.RecordEngineData;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component
public class JsonRecordEngineData implements RecordEngineData {
    private final ObjectMapper json;
    public JsonRecordEngineData(ObjectMapper json){this.json=json;}
    public String encode(Map<String,Object> data){return json.writeValueAsString(data);}
}
