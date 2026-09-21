package com.sugamflow.school.support.ticket.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.support.ticket.model.SupportProduct;
import com.sugamflow.school.support.ticket.model.SupportTicket;
import com.sugamflow.school.support.ticket.model.SupportTicketStatus;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
  Page<SupportTicket> findByProductAndOrganizationIdAndShopIdOrderByCreatedAtDesc(
      SupportProduct product, String organizationId, String shopId, Pageable pageable);

  Optional<SupportTicket> findByIdAndProductAndOrganizationIdAndShopId(
      Long id, SupportProduct product, String organizationId, String shopId);

  Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status, Pageable pageable);

  Page<SupportTicket> findByProductOrderByCreatedAtDesc(SupportProduct product, Pageable pageable);

  Page<SupportTicket> findByProductAndStatusOrderByCreatedAtDesc(
      SupportProduct product, SupportTicketStatus status, Pageable pageable);
}
