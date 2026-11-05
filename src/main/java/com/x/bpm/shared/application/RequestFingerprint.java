package com.x.bpm.shared.application;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Deterministic typed JSON fingerprint; independent of object/map insertion order. */
public final class RequestFingerprint {
    private RequestFingerprint() { }
    public static String of(Object payload) {
        try {var digest=MessageDigest.getInstance("SHA-256");append(digest,payload);return HexFormat.of().formatHex(digest.digest());}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static void append(MessageDigest hash,Object value) {
        if(value==null){text(hash,"null");return;}
        if(value instanceof Map<?,?> map){if(map.keySet().stream().anyMatch(key->!(key instanceof String)))throw new IllegalArgumentException("JSON object keys must be strings");text(hash,"object");hash.update(ByteBuffer.allocate(4).putInt(map.size()).array());
            map.keySet().stream().map(Object::toString).sorted().forEach(key->{text(hash,key);append(hash,map.get(key));});return;}
        if(value instanceof List<?> list){text(hash,"array");hash.update(ByteBuffer.allocate(4).putInt(list.size()).array());list.forEach(item->append(hash,item));return;}
        if(!(value instanceof String||value instanceof Boolean||value instanceof Number))throw new IllegalArgumentException("Fingerprint accepts JSON values only");
        text(hash,value.getClass().getName());text(hash,value.toString());
    }
    private static void text(MessageDigest hash,String text){byte[] bytes=text.getBytes(StandardCharsets.UTF_8);hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);}
}
