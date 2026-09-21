package com.baakio.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "baakio.scan.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfig {
    public static final String EXCHANGE = "baakio.jobs";
    public static final String ROUTING_KEY = "scan.requested";
    public static final String QUEUE = "scan.extract";
    public static final String DLX = "baakio.jobs.dlx";
    public static final String DLQ = "scan.extract.dlq";

    @Bean
    TopicExchange scanExchange() { return new TopicExchange(EXCHANGE, true, false); }

    @Bean
    DirectExchange scanDeadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean
    Queue scanQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    Queue scanDeadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean
    Binding scanBinding(@Qualifier("scanQueue") Queue scanQueue, TopicExchange scanExchange) {
        return BindingBuilder.bind(scanQueue).to(scanExchange).with(ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding(@Qualifier("scanDeadLetterQueue") Queue scanDeadLetterQueue, DirectExchange scanDeadLetterExchange) {
        return BindingBuilder.bind(scanDeadLetterQueue).to(scanDeadLetterExchange).with(DLQ);
    }

    @Bean
    @Primary
    RabbitTemplate scanRabbitTemplate(CachingConnectionFactory connectionFactory) {
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }
}
