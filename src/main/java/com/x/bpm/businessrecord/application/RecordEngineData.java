package com.x.bpm.businessrecord.application;
import java.util.Map;
/** JSON scalar variable avoids arbitrary Java object serialization in the engine. */
public interface RecordEngineData {String encode(Map<String,Object> data);}
