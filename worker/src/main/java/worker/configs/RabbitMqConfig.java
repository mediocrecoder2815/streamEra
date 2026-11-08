package worker.configs;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String QUEUE = "stream-era-video-upload";
    public static final String TOPIC_EXCHANGE_NAME = "video-upload-exchange";
    public static final String ROUTING_KEY_GENERAL_MESSAGE = "event.video.*";


    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    @Bean
    public Queue queueConsumer() {
        return new Queue(QUEUE);
    }

    @Bean
    Binding bindingConsumer(Queue queueConsumer, TopicExchange exchange) {
        return BindingBuilder.bind(queueConsumer).to(exchange).with(ROUTING_KEY_GENERAL_MESSAGE);
    }
}
