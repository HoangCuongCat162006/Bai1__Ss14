package com.example.bai1__ss14.repository;

import com.example.bai1__ss14.model.CompensationLog;

import java.util.List;
import java.util.Optional;

public interface CompensationLogRepository {
    CompensationLog save(CompensationLog log);
    Optional<CompensationLog> findById(String id);
    List<CompensationLog> findPendingLogs();
    List<CompensationLog> findAll();
    void clear();
}
