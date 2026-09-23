package com.nibash.activity;

import com.nibash.user.User;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Records the handful of actions worth an audit line — batch billing, payments, document and
 * parking-layout changes. Called inside the caller's transaction, so the log line commits (or
 * rolls back) together with the change it describes.
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository logs;
    private final ObjectMapper json;

    public ActivityLogService(ActivityLogRepository logs, ObjectMapper json) {
        this.logs = logs;
        this.json = json;
    }

    public void record(User user, String entityType, Long entityId, String action, Map<String, ?> details) {
        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? 0 : entityId.intValue());
        log.setAction(action);
        log.setDetailsJson(details == null || details.isEmpty() ? null : json.writeValueAsString(details));
        logs.save(log);
    }
}
