package com.visionmapping.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** BRD 33 D-33-15: the @PreAuthorize constants stay equal to the permissions. */
class AuthoritiesTest {

    @Test
    void everyPermissionHasAnEqualConstantAndNothingElseIsListed() throws IllegalAccessException {
        Map<String, Field> constants = Arrays.stream(Authorities.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .collect(Collectors.toMap(Field::getName, Function.identity()));

        assertThat(constants.keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(Permission.values()).map(Enum::name).toList());
        for (Permission permission : Permission.values()) {
            assertThat(constants.get(permission.name()).get(null)).as(permission.name()).isEqualTo(permission.authority());
        }
    }
}
