package com.semicolon.healing_service.repository;

import com.semicolon.healing_service.model.CircuitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CircuitEventRepository extends JpaRepository<CircuitEvent, Long> {

    List<CircuitEvent> findTop20ByServiceNameOrderByTriggeredAtDesc(String serviceName);

    List<CircuitEvent> findTop50ByOrderByTriggeredAtDesc();

    Optional<CircuitEvent> findTopByServiceNameAndToStateOrderByTriggeredAtDesc(
            String serviceName, CircuitEvent.CircuitState toState);

    long countByServiceName(String serviceName);
}