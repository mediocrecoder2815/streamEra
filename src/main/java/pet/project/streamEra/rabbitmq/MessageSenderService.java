package pet.project.streamEra.rabbitmq;

import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import pet.project.streamEra.configs.RabbitMqConfig;
import pet.project.streamEra.videoDomain.entity.VideoEvent;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@AllArgsConstructor
public class MessageSenderService {

    private RabbitTemplate rabbitTemplate;

    public void sendMessage(String routingKey, VideoEvent event) throws ExecutionException, InterruptedException, TimeoutException {
        CorrelationData correlationId = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(RabbitMqConfig.TOPIC_EXCHANGE_NAME,
                                    routingKey,
                                    event,
                                    correlationId);
        try{
            CorrelationData.Confirm confirm =
                    correlationId.getFuture().get(5, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                throw new IllegalStateException(
                        "Broker nacked message " + correlationId.getId()
                                + ": " + confirm.reason());
            }
            if (correlationId.getReturned() != null) {
                String replyText = correlationId.getReturned().getReplyText();
                throw new IllegalStateException(
                        "Message was unroutable: " + (replyText != null ? replyText : "null")
                );
        }
    }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for publisher confirm", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "No publisher confirm for message " + correlationId.getId(), e);
        }
}
}
