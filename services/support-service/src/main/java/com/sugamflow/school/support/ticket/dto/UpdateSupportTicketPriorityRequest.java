package com.sugamflow.school.support.ticket.dto;

import com.sugamflow.school.support.ticket.model.SupportTicketPriority;

import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketPriorityRequest(@NotNull SupportTicketPriority priority) {}
