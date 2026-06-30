package com.ims.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Real AWS SDK v2 clients. Only created when ims.aws.enabled=true (prod/default).
 * In the local profile these beans are absent and in-memory ports take over.
 */
@Configuration
@ConditionalOnProperty(name = "ims.aws.enabled", havingValue = "true", matchIfMissing = true)
public class AwsConfig {

    private final ImsProperties props;

    public AwsConfig(ImsProperties props) {
        this.props = props;
    }

    private Region region() {
        return Region.of(props.getAws().getRegion());
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(region())
                .credentialsProvider(DefaultCredentialsProvider.create());
        String endpoint = props.getAws().getDynamodb().getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "ims.aws.sqs.enabled", havingValue = "true", matchIfMissing = true)
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(region())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "ims.aws.sns.enabled", havingValue = "true", matchIfMissing = true)
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(region())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(region())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "ims.forecast.provider", havingValue = "bedrock")
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(region())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
