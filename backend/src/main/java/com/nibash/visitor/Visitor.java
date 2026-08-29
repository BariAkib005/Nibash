package com.nibash.visitor;

import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/** The actual gate crossing. Tenant path: {@code Visitor -> appointment -> building}. */
@Entity
@Table(name = "visitors")
public class Visitor {

    public static final String PENDING = "pending";
    public static final String CHECKED_IN = "checked_in";
    public static final String CHECKED_OUT = "checked_out";
    public static final List<String> STATUSES = List.of(PENDING, CHECKED_IN, CHECKED_OUT);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "checkin_time")
    private LocalDateTime checkinTime;

    @Column(name = "checkout_time")
    private LocalDateTime checkoutTime;

    @Column(nullable = false, length = 12)
    private String status = PENDING;

    /** The guard on duty. Nullable because a scan can happen from an unattended kiosk. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private User handledBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public LocalDateTime getCheckinTime() { return checkinTime; }
    public void setCheckinTime(LocalDateTime checkinTime) { this.checkinTime = checkinTime; }
    public LocalDateTime getCheckoutTime() { return checkoutTime; }
    public void setCheckoutTime(LocalDateTime checkoutTime) { this.checkoutTime = checkoutTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public User getHandledBy() { return handledBy; }
    public void setHandledBy(User handledBy) { this.handledBy = handledBy; }
}
