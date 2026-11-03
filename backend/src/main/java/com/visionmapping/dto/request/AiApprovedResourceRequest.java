package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** V9 (AI-FR-26): a resource an admin has checked. url must be a web address when given. */
public record AiApprovedResourceRequest(
        @NotBlank @Pattern(regexp = "COURSE|BOOK|TOOL|ORGANISATION|FUNDING|PERSON|OTHER") String kind,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) @Pattern(regexp = "^$|^https?://\\S+$", message = "Use a full web address starting with http:// or https://.") String url,
        @Size(max = 500) String description,
        boolean active) {
}
