package worker.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String region,
        String endpoint,
        @DefaultValue("false") boolean pathStyle,
        S3 s3
) {
    public record S3(String bucket) { }
}
