package com.baakio.controller;

import com.baakio.dto.reminder.ReminderRequest;
import com.baakio.dto.reminder.ReminderResponse;
import com.baakio.service.ReminderService;
import com.baakio.security.AccountHolderPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/reminder-preview")
public class ReminderController {
    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) { this.reminders = reminders; }

    @PostMapping
    public ReminderResponse preview(@AuthenticationPrincipal AccountHolderPrincipal principal,
            @PathVariable long customerId, @Valid @RequestBody ReminderRequest body) {
        return reminders.preview(principal, customerId, body);
    }
}
