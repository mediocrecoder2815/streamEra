package worker.configs;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AwsProperties.class)
public class S3Config {

    @Bean
    S3Client s3Client(AwsProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
