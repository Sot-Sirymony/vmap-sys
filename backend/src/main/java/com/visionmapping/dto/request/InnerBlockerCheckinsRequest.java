package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.InnerBlockerAnswer;
import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** V34 (D-34-10): the weekly answers, one per blocker. A blocker left out keeps no answer for this review. */
public record InnerBlockerCheckinsRequest(@NotNull @Size(max = 10) List<@NotNull @Valid Checkin> checkins) {

    public InnerBlockerCheckinsRequest {
        checkins = Copies.list(checkins);
    }

    public record Checkin(@NotNull @Positive Long innerBlockerId, @NotNull InnerBlockerAnswer answer) {
    }
}
