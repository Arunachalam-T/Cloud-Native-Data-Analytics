package com.dataanalytics.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AwsConfig {

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.credentials.access-key:}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:}")
    private String secretKey;

    @Bean
    public AmazonS3 amazonS3() {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
            .withRegion(region);

        // Use explicit credentials if provided, otherwise fall back to default provider chain
        // (IAM role, environment variables, ~/.aws/credentials, etc.)
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(accessKey, secretKey)
                )
            );
        } else {
            builder.withCredentials(DefaultAWSCredentialsProviderChain.getInstance());
        }

        return builder.build();
    }
}
