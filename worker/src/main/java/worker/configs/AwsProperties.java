package worker.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(String region, S3 s3) {
    public record S3(String bucket) { }
}