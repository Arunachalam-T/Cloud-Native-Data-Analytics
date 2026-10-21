package com.dataanalytics.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name:data-analytics-dev-bucket}")
    private String bucketName;

    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    private final Path localStorageDir = Path.of("uploads");

    /**
     * Upload a multipart file to S3 under datasets/ prefix.
     * Seamlessly falls back to local storage if AWS credentials are not configured.
     * @return the object key
     */
    public String uploadFile(MultipartFile file, String datasetName) throws IOException {
        String s3Key = "datasets/" + UUID.randomUUID() + "/" + sanitizeFilename(file.getOriginalFilename());

        if (isAwsConfigured()) {
            try {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(file.getSize());
                metadata.setContentType(file.getContentType());
                metadata.addUserMetadata("dataset-name", datasetName != null ? datasetName : "");

                PutObjectRequest putRequest = new PutObjectRequest(
                    bucketName,
                    s3Key,
                    file.getInputStream(),
                    metadata
                );

                amazonS3.putObject(putRequest);
                log.info("Uploaded file to AWS S3: bucket={}, key={}", bucketName, s3Key);
                return s3Key;
            } catch (Exception e) {
                log.warn("S3 upload failed ({}), falling back to local storage: {}", e.getMessage(), s3Key);
            }
        }

        // Local storage fallback for local development
        saveToLocalStorage(file, s3Key);
        log.info("Stored file in local directory: key={}", s3Key);
        return s3Key;
    }

    /**
     * Download an object as an InputStream from local disk or S3.
     */
    public InputStream downloadFile(String s3Key) {
        Path localPath = localStorageDir.resolve(s3Key);
        if (Files.exists(localPath)) {
            try {
                return Files.newInputStream(localPath);
            } catch (IOException e) {
                log.error("Failed to read local file: {}", localPath, e);
            }
        }

        try {
            S3Object s3Object = amazonS3.getObject(bucketName, s3Key);
            return s3Object.getObjectContent();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from S3 or local storage: " + s3Key, e);
        }
    }

    /**
     * Generate a URL for the file (S3 pre-signed or local file URL).
     */
    public String generatePresignedUrl(String s3Key) {
        Path localPath = localStorageDir.resolve(s3Key);
        if (Files.exists(localPath)) {
            return localPath.toUri().toString();
        }

        try {
            Date expiration = new Date(System.currentTimeMillis() + 3_600_000L); // 1 hour
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, s3Key)
                .withMethod(com.amazonaws.HttpMethod.GET)
                .withExpiration(expiration);
            URL url = amazonS3.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            return localPath.toUri().toString();
        }
    }

    /**
     * Delete a file from local storage or S3.
     */
    public void deleteFile(String s3Key) {
        Path localPath = localStorageDir.resolve(s3Key);
        try {
            Files.deleteIfExists(localPath);
        } catch (IOException ignored) {}

        if (isAwsConfigured()) {
            try {
                amazonS3.deleteObject(bucketName, s3Key);
                log.info("Deleted file from S3: bucket={}, key={}", bucketName, s3Key);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Check if a bucket exists and create it if it doesn't (when AWS is configured).
     */
    public void ensureBucketExists() {
        if (isAwsConfigured()) {
            try {
                if (!amazonS3.doesBucketExistV2(bucketName)) {
                    amazonS3.createBucket(bucketName);
                    log.info("Created S3 bucket: {}", bucketName);
                }
            } catch (Exception e) {
                log.warn("Could not ensure S3 bucket exists: {}", e.getMessage());
            }
        }
    }

    private void saveToLocalStorage(MultipartFile file, String s3Key) throws IOException {
        Path targetPath = localStorageDir.resolve(s3Key);
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isAwsConfigured() {
        return amazonS3 != null &&
               accessKey != null &&
               !accessKey.isBlank() &&
               !accessKey.contains("your-") &&
               !accessKey.equals("your-access-key-id");
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "upload";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
