package com.visionmapping.dto.response;

/**
 * V23 (UM-FR-01): one permission in the catalogue.
 *
 * @param adminOnly can never be given to a role other than Admin (Manage roles)
 * @param always    every role has it (Use own plan)
 */
public record PermissionResponse(String key, String group, String label, String description, boolean adminOnly, boolean always) {
}
