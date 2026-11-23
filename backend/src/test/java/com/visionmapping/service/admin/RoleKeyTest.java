package com.visionmapping.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** V23 (UM-FR-09): custom role keys are made from the name; underscores never lead or trail. */
class RoleKeyTest {

    @Test
    void trimsUnderscoresAtBothEndsOnly() {
        assertThat(RoleService.trimUnderscores("__PROGRAM_ASSISTANT__")).isEqualTo("PROGRAM_ASSISTANT");
        assertThat(RoleService.trimUnderscores("PLAIN")).isEqualTo("PLAIN");
        assertThat(RoleService.trimUnderscores("___")).isEmpty();
        assertThat(RoleService.trimUnderscores("")).isEmpty();
    }
}
