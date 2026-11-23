package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/**
 * V23 (UM-FR-08): one role on the Roles and permissions page.
 *
 * @param editable    whether its permissions can be changed (Coach and custom roles; never Admin or Participant)
 * @param renameable  whether its name and description can be changed (custom roles only)
 * @param holders     how many accounts have it
 * @param privileged  holders must use two-step sign-in (UM-FR-06)
 */
public record RoleResponse(
        String key,
        String name,
        String description,
        boolean builtIn,
        boolean editable,
        boolean renameable,
        long holders,
        boolean privileged,
        List<String> permissions
) {

    public RoleResponse {
        permissions = Copies.list(permissions);
    }
}
