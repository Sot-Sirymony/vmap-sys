package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V11 Phase 5 (PB-FR-23 to PB-FR-26, PB-FR-38): ask for the helpers, risks and
 * resources a plan needs. items are the goals and steps of the Build my plan
 * draft, by draft key, so suggestions can point at them; the saved plan is read
 * on the server. useFears (off unless the participant ticks it) lets the
 * participant's own fear journal suggest risks.
 */
public record PlanHelpersRequest(
        @Size(max = 60) List<@NotNull @Valid Item> items,
        boolean useFears,
        @Valid PlanAskRequest ask) {

    public PlanHelpersRequest {
        items = Copies.list(items);
    }


    public record Item(
            @NotBlank @Size(max = 40) String key,
            @NotNull @Pattern(regexp = "GOAL|STEP") String kind,
            @Size(max = 220) String title,
            @Size(max = 1000) String resources,
            @Size(max = 40) String goalKey) {
    }
}
