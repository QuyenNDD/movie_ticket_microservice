package com.movie.payment_service.config;

import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.booking-paid-queue}")
    private String bookingPaidQueue;

    @Value("${app.rabbitmq.booking-paid-routing-key}")
    private String bookingPaidRoutingKey;

    @PostConstruct
    public void init() {
        System.out.println(">>> RabbitMQConfig loaded successfully");
    }

    @Bean
    public DirectExchange movieTicketExchange() {
        System.out.println(">>> Creating RabbitMQ exchange: " + exchangeName);
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue bookingPaidQueue() {
        System.out.println(">>> Creating RabbitMQ queue: " + bookingPaidQueue);
        return new Queue(bookingPaidQueue, true);
    }

    @Bean
    public Binding bookingPaidBinding() {
        System.out.println(">>> Creating RabbitMQ binding: " + bookingPaidRoutingKey);
        return BindingBuilder
                .bind(bookingPaidQueue())
                .to(movieTicketExchange())
                .with(bookingPaidRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public ApplicationRunner rabbitAdminInitializer(RabbitAdmin rabbitAdmin) {
        return args -> {
            System.out.println(">>> Forcing RabbitMQ declare exchange/queue/binding...");
            rabbitAdmin.initialize();
            System.out.println(">>> RabbitMQ declare completed.");
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}