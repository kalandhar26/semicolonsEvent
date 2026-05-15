package com.semicolon.order_service.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Topic for order events (consumed by other services)
    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name("orders")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Topic for anomaly alerts (published by Healing Service)
    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name("anomaly-alerts")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Topic for circuit breaker state change events
    @Bean
    public NewTopic circuitEventsTopic() {
        return TopicBuilder.name("circuit-events")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
