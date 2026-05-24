package com.movie.notification_service.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_EMAIL = "ticket.email.queue";
    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";
    public static final String ROUTING_KEY_EMAIL = "ticket.email.send";

    @Bean
    public Queue emailQueue() {
        return new Queue(QUEUE_EMAIL, true); // true = bền vững (Durable), server reset không mất data
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE_NOTIFICATION);
    }

    @Bean
    public Binding bindingEmailQueue(Queue emailQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationExchange).with(ROUTING_KEY_EMAIL);
    }
}