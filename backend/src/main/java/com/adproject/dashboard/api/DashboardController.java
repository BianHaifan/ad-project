package com.adproject.dashboard.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.dashboard.application.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruiter")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    DashboardResponses.DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.dashboard(user);
    }
}
