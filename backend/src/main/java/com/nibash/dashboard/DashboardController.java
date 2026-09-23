package com.nibash.dashboard;

import com.nibash.auth.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/dashboard/summary/?building_id=} — IsAuthenticated (spec §9). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary/")
    public Map<String, Object> summary(@RequestParam(name = "building_id", required = false) Long buildingId) {
        return dashboard.summary(CurrentUser.require(), buildingId);
    }
}
