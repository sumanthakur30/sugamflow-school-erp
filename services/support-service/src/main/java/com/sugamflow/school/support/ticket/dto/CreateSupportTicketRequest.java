package com.sugamflow.school.support.ticket.dto;

import com.sugamflow.school.support.ticket.model.SupportIssueType;
import com.sugamflow.school.support.ticket.model.SupportProduct;
import com.sugamflow.school.support.ticket.model.SupportTicketPriority;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
    SupportProduct product,
    @NotNull SupportIssueType issueType,
    @NotBlank @Size(max = 255) String subject,
    @NotBlank @Size(max = 10000) String description,
    SupportTicketPriority priority,
    @Email @Size(max = 255) String contactEmail,
    @Pattern(regexp = "^[+]?[0-9\\s-]{7,20}$", message = "Invalid mobile number") @Size(max = 20)
        String contactMobile,
    @Size(max = 100) String moduleName,
    @Size(max = 50) String appVersion,
    @Size(max = 2000) String deviceInfo) {}
