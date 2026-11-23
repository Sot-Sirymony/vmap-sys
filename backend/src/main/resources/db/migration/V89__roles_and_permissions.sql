-- V23 (BRD 23.0.0) phase 1: roles are rows with permissions, not just names.
-- app_users.role keeps holding the role's key (now checked against roles), so
-- nothing changes for anyone: the three built-in roles get exactly the access
-- they had (UM-FR-03). Custom roles come in phase 4.

CREATE TABLE roles (
    role_key VARCHAR(40) PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(200),
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
-- Names are also compared without regard to case in code (UM-FR-09).

INSERT INTO roles (role_key, name, description, built_in, created_at, updated_at) VALUES
    ('USER', 'Participant', 'Works on their own vision map.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COACH', 'Coach', 'Reads, never changes, the participants of the cohorts they coach.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ADMIN', 'Admin', 'Runs the program. Always has every permission.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- UM-FR-01: one row per permission a role has. The Admin role is given every
-- permission in code as well, so it can never lose one (D-23-2).
CREATE TABLE role_permissions (
    role_key VARCHAR(40) NOT NULL REFERENCES roles(role_key) ON DELETE CASCADE,
    permission VARCHAR(60) NOT NULL,
    PRIMARY KEY (role_key, permission)
);

INSERT INTO role_permissions (role_key, permission) VALUES
    ('USER', 'USE_OWN_PLAN'),
    ('COACH', 'USE_OWN_PLAN'),
    ('COACH', 'COACH_COHORTS'),
    ('ADMIN', 'USE_OWN_PLAN'),
    ('ADMIN', 'COACH_COHORTS'),
    ('ADMIN', 'VIEW_USERS'),
    ('ADMIN', 'MANAGE_USERS'),
    ('ADMIN', 'DELETE_USERS'),
    ('ADMIN', 'RESET_TWO_FACTOR'),
    ('ADMIN', 'MANAGE_COHORTS'),
    ('ADMIN', 'MANAGE_MODULE_CONTENT'),
    ('ADMIN', 'MANAGE_INTERFACE_TEXT'),
    ('ADMIN', 'MANAGE_AI_SETTINGS'),
    ('ADMIN', 'VIEW_AI_REPORTS'),
    ('ADMIN', 'VIEW_SECURITY_LOG'),
    ('ADMIN', 'HANDLE_SUPPORT'),
    ('ADMIN', 'MANAGE_ROLES');

ALTER TABLE app_users ADD CONSTRAINT fk_app_users_role FOREIGN KEY (role) REFERENCES roles(role_key);
