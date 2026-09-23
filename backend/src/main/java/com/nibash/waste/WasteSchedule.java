package com.nibash.waste;

import com.nibash.building.Building;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A waste collection slot. {@code schedule_time} is the first (or only) collection; a recurring
 * schedule repeats from it. Tenant path: {@code WasteSchedule -> building}.
 */
@Entity
@Table(name = "waste_schedules")
public class WasteSchedule {

    public static final List<String> RECURRENCES = List.of("daily", "weekly", "biweekly", "monthly");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "schedule_time", nullable = false)
    private LocalDateTime scheduleTime;

    @Column(length = 50)
    private String recurring;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public LocalDateTime getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(LocalDateTime scheduleTime) { this.scheduleTime = scheduleTime; }
    public String getRecurring() { return recurring; }
    public void setRecurring(String recurring) { this.recurring = recurring; }

    /**
     * The first collection at or after {@code now}: the schedule itself when it is still ahead,
     * otherwise — for a recurring schedule — the next repetition. A one-off in the past has none.
     */
    public LocalDateTime nextOccurrence(LocalDateTime now) {
        if (!scheduleTime.isBefore(now)) {
            return scheduleTime;
        }
        if (recurring == null || recurring.isBlank()) {
            return null;
        }
        if (!RECURRENCES.contains(recurring)) {
            return null;
        }
        // Stepping is cheap even for a daily schedule started years ago, and monthly steps keep
        // the day-of-month the way plusMonths does (31 Jan → 28 Feb → 28 Mar).
        LocalDateTime next = scheduleTime;
        while (next.isBefore(now)) {
            next = switch (recurring) {
                case "daily" -> next.plusDays(1);
                case "weekly" -> next.plusWeeks(1);
                case "biweekly" -> next.plusWeeks(2);
                default -> next.plusMonths(1);
            };
        }
        return next;
    }
}
