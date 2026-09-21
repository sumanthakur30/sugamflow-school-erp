package com.sugamflow.school.support.ticket.dto;

import com.sugamflow.school.support.ticket.model.SupportTicketStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketStatusRequest(@NotNull SupportTicketStatus status) {}
