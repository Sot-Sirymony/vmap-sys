package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** V9 (AI-FR-23): English texts to draft in Khmer, for an admin to review. */
public record AiTranslateRequest(
        @NotEmpty @Size(max = 40) List<@Size(max = 4000) String> texts,
        @Pattern(regexp = "km") String targetLanguage) {
    public AiTranslateRequest {
        texts = Copies.list(texts);
    }
}
