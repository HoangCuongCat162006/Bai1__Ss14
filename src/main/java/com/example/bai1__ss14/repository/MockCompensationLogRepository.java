package com.example.bai1__ss14.repository;

import com.example.bai1__ss14.model.CompensationLog;
import com.example.bai1__ss14.model.CompensationStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class MockCompensationLogRepository implements CompensationLogRepository {

    private final ConcurrentHashMap<String, CompensationLog> logs = new ConcurrentHashMap<>();

    @Override
    public CompensationLog save(CompensationLog log) {
        logs.put(log.getId(), log);
        return log;
    }

    @Override
    public Optional<CompensationLog> findById(String id) {
        return Optional.ofNullable(logs.get(id));
    }

    @Override
    public List<CompensationLog> findPendingLogs() {
        return logs.values().stream()
                .filter(l -> l.getStatus() == CompensationStatus.PENDING)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompensationLog> findAll() {
        return new ArrayList<>(logs.values());
    }

    @Override
    public void clear() {
        logs.clear();
    }
}
