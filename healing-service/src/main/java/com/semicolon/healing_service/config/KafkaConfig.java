package com.semicolon.healing_service.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic circuitEventsTopic() {
        return TopicBuilder.name("circuit-events")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic anomalyAlertsTopic() {
        return TopicBuilder.name("anomaly-alerts")
                .partitions(1)
                .replicas(1)
                .build();
    }
}

