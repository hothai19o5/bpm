package com.x.bpm.businessrecord.config;

import com.x.bpm.businessrecord.application.AttachmentStorage;
import com.x.bpm.businessrecord.adapter.storage.S3AttachmentStorage;
import com.x.bpm.shared.error.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods=false)
public class AttachmentStorageConfiguration {
    @Bean(destroyMethod="close") @ConditionalOnProperty(name="bpm.storage.enabled",havingValue="true")
    public S3Client attachmentS3Client(@Value("${bpm.storage.endpoint}") String endpoint,@Value("${bpm.storage.region}") String region,
            @Value("${bpm.storage.access-key}") String accessKey,@Value("${bpm.storage.secret-key}") String secretKey){
        var builder=S3Client.builder().region(Region.of(region)).forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(config->config.apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(5)));
        if(!endpoint.isBlank())builder.endpointOverride(URI.create(endpoint));
        if(!accessKey.isBlank())builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey,secretKey)));
        return builder.build();
    }
    @Bean @ConditionalOnProperty(name="bpm.storage.enabled",havingValue="true")
    public AttachmentStorage s3AttachmentStorage(S3Client client,@Value("${bpm.storage.bucket}") String bucket){return new S3AttachmentStorage(client,bucket);}
    @Bean @ConditionalOnMissingBean(AttachmentStorage.class)
    public AttachmentStorage unavailableAttachmentStorage(){return new AttachmentStorage(){
        private ApplicationException unavailable(){return new ApplicationException(ErrorCode.INTERNAL_ERROR,"Dịch vụ tệp chưa được cấu hình.");}
        public String bucket(){throw unavailable();}public void put(String key,byte[] bytes,String type){throw unavailable();}public byte[] read(String bucket,String key){throw unavailable();}public void delete(String key){throw unavailable();}public void verify(String bucket,String key,long size,String hash){throw unavailable();}
    };}
}
