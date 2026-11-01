package com.visionmapping.dto.response;

public record HabitResponse(
        Long id,
        String name,
        boolean checkedOnDate,
        int currentStreak,
        boolean archived
) {
}
