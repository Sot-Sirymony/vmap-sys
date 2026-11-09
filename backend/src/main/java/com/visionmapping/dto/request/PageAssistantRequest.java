package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.visionmapping.util.Copies;
import java.util.Map;

/**
 * V15 (AH-FR-01 to AH-FR-07): what the page assistant is helping with.
 *
 * <p>page is one of the six pages that have the assistant. area names the part
 * of the page, for example {@code form}, {@code item}, {@code list},
 * {@code field:hook}, {@code statement:fearScore} or {@code question:3}. itemId
 * is the open obstacle or message, or the module number on a module page; the
 * server checks it belongs to the participant. form holds what the participant
 * has typed but not saved (AH-FR-05). It is data only and is never stored.
 */
public record PageAssistantRequest(
        @NotNull @Pattern(regexp = PAGES) String page,
        @NotNull @Size(max = 40) @Pattern(regexp = AREA) String area,
        Long itemId,
        @Size(max = 60) Map<@Size(max = 60) String, @Size(max = 4000) String> form) {

    public static final String PAGES = "MODULES|MODULE|ASSESSMENT|OBSTACLES|COMMUNICATION|WORK_STYLE";
    public static final String AREA = "[a-zA-Z0-9:_.-]+";

    public PageAssistantRequest {
        form = Copies.map(form);
    }

    /** The unsaved form, or an empty map when none was sent. */
    public Map<String, String> formValues() {
        return form == null ? Map.of() : form;
    }
}
