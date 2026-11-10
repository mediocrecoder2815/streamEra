package worker.configs;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import worker.VideoEvent;

import java.util.Map;

@Configuration
public class RabbitMqConfig {
    // Keep the worker on its own queue; the monolith also listens to
    // event.video.* for processed/failed events.
    public static final String QUEUE = "stream-era-video-worker";
    public static final String TOPIC_EXCHANGE_NAME = "video-upload-exchange";
    public static final String ROUTING_KEY_UPLOAD = "event.video.upload";
    public static final String ROUTING_KEY_PROCESSED = "event.video.processed";
    public static final String ROUTING_KEY_FAILED = "event.video.failed";


    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setIdClassMapping(Map.of("streamEra.videoEvent", VideoEvent.class));
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public Queue queueConsumer() {
        return new Queue(QUEUE);
    }

    @Bean
    Binding bindingConsumer(Queue queueConsumer, TopicExchange exchange) {
        return BindingBuilder.bind(queueConsumer).to(exchange).with(ROUTING_KEY_UPLOAD);
    }
}
