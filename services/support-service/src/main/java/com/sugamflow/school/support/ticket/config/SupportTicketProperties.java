package com.sugamflow.school.support.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school.support")
public class SupportTicketProperties {
  private String storageDir = "./data/support-attachments";
  private long maxAttachmentBytes = 5L * 1024 * 1024;
  private int maxAttachmentsPerTicket = 5;
  private String allowedContentTypes = "image/png,image/jpeg,image/webp,image/gif,application/pdf";

  public String getStorageDir() {
    return storageDir;
  }

  public void setStorageDir(String storageDir) {
    this.storageDir = storageDir;
  }

  public long getMaxAttachmentBytes() {
    return maxAttachmentBytes;
  }

  public void setMaxAttachmentBytes(long maxAttachmentBytes) {
    this.maxAttachmentBytes = maxAttachmentBytes;
  }

  public int getMaxAttachmentsPerTicket() {
    return maxAttachmentsPerTicket;
  }

  public void setMaxAttachmentsPerTicket(int maxAttachmentsPerTicket) {
    this.maxAttachmentsPerTicket = maxAttachmentsPerTicket;
  }

  public String getAllowedContentTypes() {
    return allowedContentTypes;
  }

  public void setAllowedContentTypes(String allowedContentTypes) {
    this.allowedContentTypes = allowedContentTypes;
  }
}
