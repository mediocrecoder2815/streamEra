package pet.project.streamEra.videoDomain.rabbitmq;

import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import pet.project.streamEra.configs.RabbitMqConfig;
import pet.project.streamEra.videoDomain.entity.VideoEvent;

import javax.sound.midi.Receiver;
import java.util.ArrayDeque;

@Component
@AllArgsConstructor
public class VideoMessageSender {

    private RabbitTemplate rabbitTemplate;

    public void sendMessage(String routingKey, VideoEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.TOPIC_EXCHANGE_NAME, routingKey, event);
    }
}
