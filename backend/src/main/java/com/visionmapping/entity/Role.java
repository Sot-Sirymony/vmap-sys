package com.visionmapping.entity;

import com.visionmapping.security.Permission;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V23 (BRD 23.0.0, D-23-2): a named set of permissions. Participant, Coach and
 * Admin are built in; admins add custom roles (phase 4). The key never changes;
 * the name can (custom roles only). See V89__roles_and_permissions.sql.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role extends BaseAuditableEntity {

    @Id
    @Column(name = "role_key", length = 40)
    private String key;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_key"))
    @Column(name = "permission", length = 60)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    public Role(String key, String name, String description, Set<Permission> permissions) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }
}
