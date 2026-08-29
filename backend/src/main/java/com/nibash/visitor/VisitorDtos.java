package com.nibash.visitor;

import java.time.LocalDateTime;

/** Read shapes for the gate flow (spec §8.6). */
public final class VisitorDtos {

    private VisitorDtos() {
    }

    public record AppointmentDto(
            Long id, Long building, Long resident, String visitorName, String visitorPhone,
            LocalDateTime scheduledTime, boolean approved, String qrToken, LocalDateTime createdAt,
            String residentName, String unitNumber) {

        public static AppointmentDto from(Appointment a) {
            var resident = a.getResident();
            var unit = resident.getUnit();
            return new AppointmentDto(a.getId(), a.getBuilding().getId(), resident.getId(),
                    a.getVisitorName(), a.getVisitorPhone(), a.getScheduledTime(), a.isApproved(),
                    a.getQrToken(), a.getCreatedAt(), resident.getUser().getName(),
                    unit == null ? null : unit.getUnitNumber());
        }
    }

    public record VisitorDto(
            Long id, Long appointment, LocalDateTime checkinTime, LocalDateTime checkoutTime,
            String status, Long handledBy, String visitorName, String visitorPhone,
            LocalDateTime scheduledTime, String residentName, String unitNumber) {

        public static VisitorDto from(Visitor v) {
            Appointment a = v.getAppointment();
            var unit = a.getResident().getUnit();
            return new VisitorDto(v.getId(), a.getId(), v.getCheckinTime(), v.getCheckoutTime(),
                    v.getStatus(), v.getHandledBy() == null ? null : v.getHandledBy().getId(),
                    a.getVisitorName(), a.getVisitorPhone(), a.getScheduledTime(),
                    a.getResident().getUser().getName(),
                    unit == null ? null : unit.getUnitNumber());
        }
    }
}
