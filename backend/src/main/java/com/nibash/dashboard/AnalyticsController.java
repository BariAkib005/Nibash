package com.nibash.dashboard;

import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.tenancy.TenantService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/analytics/overview?building_ids[]=1&building_ids[]=2} — multi-building KPIs,
 * IsAuthenticated (spec §8.24). Also accepts repeated {@code building_ids}.
 *
 * <p>Requested ids are intersected with the caller's buildings; an empty intersection means "all
 * of mine". The five spec totals are returned as specified; {@code per_building} adds the same
 * figures building by building, which is what a portfolio screen actually needs to draw.
 */
@RestController
public class AnalyticsController {

    private static final List<String> OCCUPIED = List.of("occupied", "sold", "rented");

    @PersistenceContext
    private EntityManager em;

    private final TenantService tenancy;
    private final BuildingRepository buildings;

    public AnalyticsController(TenantService tenancy, BuildingRepository buildings) {
        this.tenancy = tenancy;
        this.buildings = buildings;
    }

    @GetMapping({"/api/analytics/overview", "/api/analytics/overview/"})
    @Transactional(readOnly = true)
    public Map<String, Object> overview(HttpServletRequest request) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        List<Long> scope = intersect(requestedIds(request), allowed);

        Map<Long, Map<String, Object>> perBuilding = new LinkedHashMap<>();
        if (!scope.isEmpty()) {
            for (Building b : buildings.findByIdInOrderByNameAsc(scope)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("building_id", b.getId());
                row.put("name", b.getName());
                row.put("invoices", 0L);
                row.put("payments_sum", 0.0);
                row.put("open_tickets", 0L);
                row.put("bookings", 0L);
                row.put("occupancy", 0L);
                row.put("total_units", 0L);
                perBuilding.put(b.getId(), row);
            }
            fill(perBuilding, "invoices",
                    "select i.building.id, count(i) from Invoice i where i.building.id in :ids group by i.building.id", scope);
            fill(perBuilding, "payments_sum",
                    "select p.invoice.building.id, sum(p.amount) from Payment p where p.invoice.building.id in :ids group by p.invoice.building.id", scope);
            fill(perBuilding, "open_tickets",
                    "select t.building.id, count(t) from Ticket t where t.building.id in :ids and t.status = 'open' group by t.building.id", scope);
            fill(perBuilding, "bookings",
                    "select b.resource.building.id, count(b) from Booking b where b.resource.building.id in :ids group by b.resource.building.id", scope);
            fill(perBuilding, "total_units",
                    "select u.building.id, count(u) from Unit u where u.building.id in :ids group by u.building.id", scope);
            List<Object[]> occupied = em.createQuery(
                            "select u.building.id, count(u) from Unit u where u.building.id in :ids and u.status in :occupied group by u.building.id",
                            Object[].class)
                    .setParameter("ids", scope).setParameter("occupied", OCCUPIED).getResultList();
            for (Object[] row : occupied) {
                perBuilding.get((Long) row[0]).put("occupancy", ((Number) row[1]).longValue());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoices", sumLong(perBuilding, "invoices"));
        out.put("payments_sum", perBuilding.values().stream().mapToDouble(r -> (Double) r.get("payments_sum")).sum());
        out.put("open_tickets", sumLong(perBuilding, "open_tickets"));
        out.put("bookings", sumLong(perBuilding, "bookings"));
        out.put("occupancy", sumLong(perBuilding, "occupancy"));
        out.put("per_building", new ArrayList<>(perBuilding.values()));
        return out;
    }

    private void fill(Map<Long, Map<String, Object>> rows, String key, String jpql, List<Long> ids) {
        for (Object[] row : em.createQuery(jpql, Object[].class).setParameter("ids", ids).getResultList()) {
            Object value = row[1];
            rows.get((Long) row[0]).put(key, value instanceof BigDecimal d ? d.doubleValue()
                    : "payments_sum".equals(key) ? ((Number) value).doubleValue() : ((Number) value).longValue());
        }
    }

    private static long sumLong(Map<Long, Map<String, Object>> rows, String key) {
        return rows.values().stream().mapToLong(r -> ((Number) r.get(key)).longValue()).sum();
    }

    /** {@code building_ids[]=1&building_ids[]=2} or {@code building_ids=1&building_ids=2}; junk is ignored. */
    private static Set<Long> requestedIds(HttpServletRequest request) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : List.of("building_ids[]", "building_ids")) {
            String[] values = request.getParameterValues(name);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                for (String part : value.split(",")) {
                    try {
                        ids.add(Long.valueOf(part.trim()));
                    } catch (NumberFormatException ignored) {
                        // a malformed id narrows nothing
                    }
                }
            }
        }
        return ids;
    }

    private static List<Long> intersect(Set<Long> requested, List<Long> allowed) {
        List<Long> both = requested.stream().filter(allowed::contains).toList();
        return both.isEmpty() ? allowed : both;
    }
}
