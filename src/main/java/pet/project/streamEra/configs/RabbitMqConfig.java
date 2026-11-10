package pet.project.streamEra.configs;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pet.project.streamEra.videoDomain.entity.VideoEvent;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

        public static final String QUEUE = "stream-era-video-upload";
        public static final String TOPIC_EXCHANGE_NAME = "video-upload-exchange";
        public static final String ROUTING_KEY_ALL_EVENTS = "event.video.*";
        public static final String ROUTING_KEY_UPLOAD = "event.video.upload";

        @Bean
        TopicExchange eventsExchange() {
            return new TopicExchange(TOPIC_EXCHANGE_NAME);
        }

        @Bean
        Jackson2JsonMessageConverter messageConverter() {
            Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
            // The worker has its own VideoEvent class in a different package.
            // Use a shared type id so both applications can deserialize the
            // same JSON without knowing each other's class names.
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
            // The main app listens for processed/failed events. It also receives
            // upload events because of this wildcard binding, but VideoService
            // acknowledges and ignores them; the worker owns those.
            return BindingBuilder.bind(queueConsumer).to(exchange).with(ROUTING_KEY_ALL_EVENTS);
        }

}
