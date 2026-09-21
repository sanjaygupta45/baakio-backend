package com.baakio.scan.messaging;

import com.baakio.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "baakio.scan.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ScanOutboxRelay {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;

    public ScanOutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${baakio.scan.messaging.poll-ms:1000}")
    public void publishBatch() {
        List<OutboxRow> events = transactions.execute(status -> claimBatch());
        if (events == null) return;
        for (OutboxRow event : events) publish(event);
    }

    private List<OutboxRow> claimBatch() {
        List<OutboxRow> events = jdbc.query("""
                select id, payload_json from outbox_event
                 where published_at is null and (claimed_until is null or claimed_until < UTC_TIMESTAMP(6))
                 order by id limit 20 for update skip locked
                """, (rs, row) -> new OutboxRow(rs.getLong("id"), rs.getString("payload_json")));
        for (OutboxRow event : events) {
            jdbc.update("""
                    update outbox_event set claimed_until=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 SECOND),
                      attempt_count=attempt_count+1 where id=? and published_at is null
                    """, event.id());
        }
        return events;
    }

    private void publish(OutboxRow event) {
        CorrelationData correlation = new CorrelationData("outbox-" + event.id());
        try {
            rabbit.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, event.payload(), message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ did not confirm the scan message.");
            if (correlation.getReturned() != null) throw new IllegalStateException("RabbitMQ did not route the scan message.");
            jdbc.update("update outbox_event set published_at=UTC_TIMESTAMP(6), claimed_until=null, last_error=null where id=? and published_at is null", event.id());
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            if (message.length() > 480) message = message.substring(0, 480);
            jdbc.update("update outbox_event set claimed_until=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 SECOND), last_error=? where id=? and published_at is null",
                    message, event.id());
        }
    }

    private record OutboxRow(long id, String payload) {}
}
