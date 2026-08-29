package com.nibash.staffing;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One staff shift. Tenant path: {@code Attendance -> staff -> building} (spec §8.5). */
@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "checkin_time", nullable = false)
    private LocalDateTime checkinTime;

    /** Null means the shift is still open — the flag the idempotent check-in keys on. */
    @Column(name = "checkout_time")
    private LocalDateTime checkoutTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public LocalDateTime getCheckinTime() { return checkinTime; }
    public void setCheckinTime(LocalDateTime checkinTime) { this.checkinTime = checkinTime; }
    public LocalDateTime getCheckoutTime() { return checkoutTime; }
    public void setCheckoutTime(LocalDateTime checkoutTime) { this.checkoutTime = checkoutTime; }
}
