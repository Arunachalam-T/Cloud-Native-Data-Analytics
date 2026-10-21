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
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Upload a multipart file to S3 under datasets/ prefix.
     * @return the S3 object key
     */
    public String uploadFile(MultipartFile file, String datasetName) throws IOException {
        String s3Key = "datasets/" + UUID.randomUUID() + "/" + sanitizeFilename(file.getOriginalFilename());

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.addUserMetadata("dataset-name", datasetName);

        PutObjectRequest putRequest = new PutObjectRequest(
            bucketName,
            s3Key,
            file.getInputStream(),
            metadata
        );

        amazonS3.putObject(putRequest);
        log.info("Uploaded file to S3: bucket={}, key={}", bucketName, s3Key);
        return s3Key;
    }

    /**
     * Download an S3 object as an InputStream.
     */
    public InputStream downloadFile(String s3Key) {
        S3Object s3Object = amazonS3.getObject(bucketName, s3Key);
        return s3Object.getObjectContent();
    }

    /**
     * Generate a pre-signed URL valid for 1 hour.
     */
    public String generatePresignedUrl(String s3Key) {
        Date expiration = new Date(System.currentTimeMillis() + 3_600_000L); // 1 hour
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, s3Key)
            .withMethod(com.amazonaws.HttpMethod.GET)
            .withExpiration(expiration);
        URL url = amazonS3.generatePresignedUrl(request);
        return url.toString();
    }

    /**
     * Delete a file from S3.
     */
    public void deleteFile(String s3Key) {
        amazonS3.deleteObject(bucketName, s3Key);
        log.info("Deleted file from S3: bucket={}, key={}", bucketName, s3Key);
    }

    /**
     * Check if a bucket exists and create it if it doesn't.
     */
    public void ensureBucketExists() {
        if (!amazonS3.doesBucketExistV2(bucketName)) {
            amazonS3.createBucket(bucketName);
            log.info("Created S3 bucket: {}", bucketName);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "upload";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
