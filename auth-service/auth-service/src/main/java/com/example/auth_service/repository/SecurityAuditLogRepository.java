package com.example.auth_service.repository;

import com.example.auth_service.model.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    // Find all logs for a specific user
    List<SecurityAuditLog> findByUsernameOrderByTimestampDesc(String username);

    // Find failed login attempts for a user within time range
    List<SecurityAuditLog> findByUsernameAndEventTypeAndTimestampAfter(
            String username,
            String eventType,
            LocalDateTime after
    );

    // Find all failed attempts from specific IP
    List<SecurityAuditLog> findByIpAddressAndEventTypeAndTimestampAfter(
            String ipAddress,
            String eventType,
            LocalDateTime after
    );

    // Find recent logs by event type
    List<SecurityAuditLog> findByEventTypeAndTimestampAfterOrderByTimestampDesc(
            String eventType,
            LocalDateTime after
    );
}