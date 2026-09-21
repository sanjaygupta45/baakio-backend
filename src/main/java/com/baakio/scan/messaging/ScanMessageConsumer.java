package com.baakio.scan.messaging;

import com.baakio.config.RabbitMqConfig;
import com.baakio.scan.application.ScanJobWorker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "baakio.scan.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ScanMessageConsumer {
    private final ScanJobWorker worker;
    private final ObjectMapper mapper;

    public ScanMessageConsumer(ScanJobWorker worker, ObjectMapper mapper) {
        this.worker = worker;
        this.mapper = mapper;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void receive(String message) throws Exception {
        JsonNode body = mapper.readTree(message);
        if (!body.hasNonNull("scanId") || !body.get("scanId").canConvertToLong()) {
            throw new IllegalArgumentException("Invalid scan queue message.");
        }
        worker.process(body.get("scanId").longValue());
    }
}
