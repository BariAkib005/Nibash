package com.nibash.tenancy;

import java.util.List;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Per-request cache of the caller's allowed building IDs (spec §6.2), so membership is resolved
 * once per request rather than once per query.
 *
 * <p>Registered as a scoped proxy so singletons like {@link TenantService} can hold a reference.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class TenantContext {

    private List<Long> allowedBuildingIds;

    public List<Long> getAllowedBuildingIds() {
        return allowedBuildingIds;
    }

    public void setAllowedBuildingIds(List<Long> allowedBuildingIds) {
        this.allowedBuildingIds = allowedBuildingIds;
    }
}
