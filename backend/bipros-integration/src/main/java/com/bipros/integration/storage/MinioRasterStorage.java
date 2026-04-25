package com.bipros.integration.storage;

import com.bipros.common.exception.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * S3-compatible implementation. Works against MinIO in dev (docker-compose.yml
 * spins up a container on :9000) and against AWS S3 in prod by changing the
 * {@code bipros.storage.s3.endpoint} property.
 * <p>
 * Active when {@code bipros.storage.kind=s3}. Auto-creates the bucket on
 * startup so devs don't have to click through the MinIO console.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "bipros.storage.kind", havingValue = "s3")
public class MinioRasterStorage implements RasterStorage {

    private final S3Client s3;
    private final String bucket;

    public MinioRasterStorage(
        @Value("${bipros.storage.s3.endpoint}") String endpoint,
        @Value("${bipros.storage.s3.access-key}") String accessKey,
        @Value("${bipros.storage.s3.secret-key}") String secretKey,
        @Value("${bipros.storage.s3.region:us-east-1}") String region,
        @Value("${bipros.storage.s3.bucket}") String bucket
    ) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder()
                // MinIO uses path-style URLs (bucket in the path, not the
                // subdomain) because the endpoint is localhost.
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException missing) {
            log.info("[Storage] Creating S3 bucket '{}'", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (Exception e) {
            // Network / auth / endpoint issues — don't crash the app at startup
            // since dev restarts happen frequently without docker running. The
            // first put() will surface the real error clearly.
            log.warn("[Storage] Bucket check failed for '{}': {}", bucket, e.getMessage());
        }
    }

    @Override
    public URI put(String key, byte[] bytes, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build(),
                RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new BusinessRuleException("STORAGE_WRITE",
                "Failed to store raster object: " + e.getMessage());
        }
        try {
            return new URI("s3", bucket, "/" + key, null);
        } catch (URISyntaxException e) {
            throw new BusinessRuleException("STORAGE_URI", e.getMessage());
        }
    }

    @Override
    public byte[] get(URI uri) {
        String key = keyFromUri(uri);
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                ResponseTransformer.toBytes()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BusinessRuleException("STORAGE_NOT_FOUND", "Object not found: " + uri);
        }
    }

    @Override
    public void delete(URI uri) {
        String key = keyFromUri(uri);
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    private String keyFromUri(URI uri) {
        if (!"s3".equals(uri.getScheme())) {
            throw new BusinessRuleException("STORAGE_URI",
                "Expected s3:// URI, got " + uri.getScheme());
        }
        // URI path for s3://bucket/key includes the leading slash.
        String path = uri.getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
