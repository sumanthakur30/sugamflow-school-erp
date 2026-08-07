package com.sugamflow.school.support.ticket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSupportTicketRequest(
    @Size(max = 255) String subject,
    @Size(max = 10000) String additionalDescription,
    @Size(max = 100) String moduleName,
    @Email @Size(max = 255) String contactEmail,
    @Pattern(regexp = "^[+]?[0-9\\s-]{7,20}$", message = "Invalid mobile number") @Size(max = 20)
        String contactMobile) {}
