package worker.configs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AwsProperties.class)
public class S3Config {

    @Bean
    S3Client s3Client(AwsProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .forcePathStyle(properties.pathStyle());

        // MinIO/LocalStack need an explicit endpoint. Real AWS uses the default.
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }

        return builder.build();
    }
}
