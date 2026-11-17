package pet.project.streamEra.configs;



import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MessageConfirmConfig {
    @Bean
    RabbitTemplateCustomizer publisherConfirmCustomizer() {
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("Broker nacked message {}: {}",
                            correlation != null ? correlation.getId() : "unknown",
                            cause);
                }
            });

            template.setReturnsCallback(returned ->
                    log.error("Unroutable message: exchange={}, routingKey={}, reply={} {}",
                            returned.getExchange(),
                            returned.getRoutingKey(),
                            returned.getReplyCode(),
                            returned.getReplyText()));
        };
    }
}
