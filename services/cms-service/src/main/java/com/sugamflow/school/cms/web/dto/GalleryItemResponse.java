package com.sugamflow.school.cms.web.dto;

import java.util.UUID;

public record GalleryItemResponse(
    UUID id,
    String title,
    String caption,
    String imageUrl,
    String album,
    int sortOrder,
    String status) {}
