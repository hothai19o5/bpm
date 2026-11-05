package com.x.bpm.businessrecord.application;

/** Internal byte storage, independent of HTTP users and JPA transactions. */
public interface AttachmentStorage {
    String bucket();
    /** Called within a transaction; adapter enlists rollback cleanup before writing. */
    void put(String key,byte[] bytes,String contentType);
    byte[] read(String bucket,String key);
    /** Check the private object still matches its committed size/hash before accepting a form reference. */
    void verify(String bucket,String key,long size,String sha256);
    void delete(String key);
}
