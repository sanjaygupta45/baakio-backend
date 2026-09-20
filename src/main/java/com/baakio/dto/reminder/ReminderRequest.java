package com.baakio.dto.reminder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record ReminderRequest(
        @NotBlank @Pattern(regexp = "en-IN|hi-IN") String locale,
        @NotBlank @Pattern(regexp = "SHORT|DETAILED") String format,
        LocalDate periodStart,
        LocalDate periodEnd
) {}
