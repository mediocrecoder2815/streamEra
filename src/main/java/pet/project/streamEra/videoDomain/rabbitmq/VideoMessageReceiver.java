package pet.project.streamEra.videoDomain.rabbitmq;



import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pet.project.streamEra.configs.RabbitMqConfig;

@Component
public class VideoMessageReceiver {
    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void receiveMessage(String message) {
        System.out.println("Received <" + message + ">");
    }
}