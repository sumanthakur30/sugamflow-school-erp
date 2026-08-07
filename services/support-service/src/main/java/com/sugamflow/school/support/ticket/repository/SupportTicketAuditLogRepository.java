package com.sugamflow.school.support.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.support.ticket.model.SupportTicketAuditLog;

public interface SupportTicketAuditLogRepository
    extends JpaRepository<SupportTicketAuditLog, Long> {}
