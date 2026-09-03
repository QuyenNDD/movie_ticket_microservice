package com.movie.payment_service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.booking-paid-queue}")
    private String bookingPaidQueue;

    @Value("${app.rabbitmq.booking-paid-routing-key}")
    private String bookingPaidRoutingKey;

    @Value("${app.rabbitmq.booking-confirm-request-queue}")
    private String bookingConfirmRequestQueue;

    @Value("${app.rabbitmq.booking-confirm-request-routing-key}")
    private String bookingConfirmRequestRoutingKey;

    @Value("${app.rabbitmq.booking-confirm-result-queue}")
    private String bookingConfirmResultQueue;

    @Value("${app.rabbitmq.booking-confirm-result-routing-key}")
    private String bookingConfirmResultRoutingKey;

    @PostConstruct
    public void init() {
        log.info("RabbitMQConfig loaded successfully");
    }

    @Bean
    public DirectExchange movieTicketExchange() {
        log.info("Creating RabbitMQ exchange: {}", exchangeName);
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue bookingPaidQueue() {
        log.info("Creating RabbitMQ queue: {}", bookingPaidQueue);
        return new Queue(bookingPaidQueue, true);
    }

    @Bean
    public Binding bookingPaidBinding() {
        log.info("Creating RabbitMQ binding: {}", bookingPaidRoutingKey);
        return BindingBuilder
                .bind(bookingPaidQueue())
                .to(movieTicketExchange())
                .with(bookingPaidRoutingKey);
    }

    @Bean
    public Queue bookingConfirmRequestQueue() {
        return new Queue(bookingConfirmRequestQueue, true);
    }

    @Bean
    public Binding bookingConfirmRequestBinding() {
        return BindingBuilder
                .bind(bookingConfirmRequestQueue())
                .to(movieTicketExchange())
                .with(bookingConfirmRequestRoutingKey);
    }

    @Bean
    public Queue bookingConfirmResultQueue() {
        return new Queue(bookingConfirmResultQueue, true);
    }

    @Bean
    public Binding bookingConfirmResultBinding() {
        return BindingBuilder
                .bind(bookingConfirmResultQueue())
                .to(movieTicketExchange())
                .with(bookingConfirmResultRoutingKey);
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
            log.info("Forcing RabbitMQ declare exchange/queue/binding...");
            rabbitAdmin.initialize();
            log.info("RabbitMQ declare completed.");
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