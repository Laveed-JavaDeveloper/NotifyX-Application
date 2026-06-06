package com.example.notifyx.repository;

import com.example.notifyx.model.NotificationLog;
import com.example.notifyx.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationLog, String> {
    List<NotificationLog> findByStatus(NotificationStatus status);
    Page<NotificationLog> findByStatus(NotificationStatus status, Pageable pageable);
    long countByStatus(NotificationStatus status);
}
