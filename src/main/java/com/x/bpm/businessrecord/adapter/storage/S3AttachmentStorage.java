package com.x.bpm.businessrecord.adapter.storage;

import com.x.bpm.businessrecord.application.AttachmentStorage;
import com.x.bpm.businessrecord.domain.AttachmentFilePolicy;
import com.x.bpm.shared.error.*;
import java.io.IOException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class S3AttachmentStorage implements AttachmentStorage {
    private final S3Client client;private final String bucket;
    public S3AttachmentStorage(S3Client client,String bucket){this.client=client;this.bucket=bucket;}
    public String bucket(){return bucket;}
    public void put(String key,byte[] bytes,String type){
        if(!TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("Attachment writes require an application transaction");
        // Compensation includes ambiguous PUT failures and never deletes another request's random key.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{delete(key);}catch(RuntimeException cleanup){org.slf4j.LoggerFactory.getLogger(S3AttachmentStorage.class).error("Attachment cleanup required for object {}",key,cleanup);}}
        });
        try{String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));client.putObject(request->request.bucket(bucket).key(key).contentType(type).metadata(java.util.Map.of("bpm-sha256",hash)),RequestBody.fromBytes(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}catch(RuntimeException failure){throw unavailable(failure);}
    }
    public byte[] read(String storedBucket,String key){
        // No caller-controlled endpoint or bucket. A deployment must explicitly migrate metadata when changing bucket.
        if(!bucket.equals(storedBucket))throw unavailable(null);
        try(var response=client.getObject(request->request.bucket(bucket).key(key))){
            byte[] bytes=response.readNBytes(AttachmentFilePolicy.MAX_BYTES+1);
            if(bytes.length>AttachmentFilePolicy.MAX_BYTES)throw unavailable(null);return bytes;
        }catch(IOException|RuntimeException failure){throw unavailable(failure);}
    }
    public void delete(String key){try{client.deleteObject(request->request.bucket(bucket).key(key));}catch(RuntimeException failure){throw unavailable(failure);}}
    public void verify(String storedBucket,String key,long size,String sha256){
        if(!bucket.equals(storedBucket))throw unavailable(null);
        try{var metadata=client.headObject(request->request.bucket(bucket).key(key));if(metadata.contentLength()!=size||!sha256.equals(metadata.metadata().get("bpm-sha256")))throw unavailable(null);}catch(RuntimeException failure){throw unavailable(failure);}
    }
    private static ApplicationException unavailable(Exception cause){var error=new ApplicationException(ErrorCode.INTERNAL_ERROR,"Không thể truy cập dịch vụ tệp. Vui lòng thử lại.");if(cause!=null)error.initCause(cause);return error;}
}
