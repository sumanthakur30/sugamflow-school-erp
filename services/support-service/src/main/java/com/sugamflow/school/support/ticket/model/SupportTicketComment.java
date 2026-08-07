package com.sugamflow.school.support.ticket.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_ticket_comments")
public class SupportTicketComment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ticket_id", nullable = false)
  private Long ticketId;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(nullable = false, length = 255)
  private String author;

  @Column(name = "staff_response", nullable = false)
  private boolean staffResponse;

  @Column(name = "internal_note", nullable = false)
  private boolean internalNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTicketId() {
    return ticketId;
  }

  public void setTicketId(Long ticketId) {
    this.ticketId = ticketId;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public boolean isStaffResponse() {
    return staffResponse;
  }

  public void setStaffResponse(boolean staffResponse) {
    this.staffResponse = staffResponse;
  }

  public boolean isInternalNote() {
    return internalNote;
  }

  public void setInternalNote(boolean internalNote) {
    this.internalNote = internalNote;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
