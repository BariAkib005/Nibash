package com.nibash.ticket;

import java.time.LocalDateTime;
import java.util.List;

/** Read shapes for the maintenance module (spec §8.7). */
public final class TicketDtos {

    private TicketDtos() {
    }

    public record TicketImageDto(Long id, Long ticket, String imagePath) {

        public static TicketImageDto from(TicketImage image) {
            return new TicketImageDto(image.getId(), image.getTicket().getId(), image.getImagePath());
        }
    }

    /**
     * Tickets carry the two names the board would otherwise fetch per card — who raised it and who
     * is on it — plus the nested images the detail view needs.
     */
    public record TicketDto(
            Long id, Long building, Long resident, String category, String description,
            String status, String priority, Long assignedTo, Long serviceVendor,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt,
            List<TicketImageDto> images, String residentName, String assignedToName,
            String unitNumber) {

        public static TicketDto from(Ticket t) {
            var resident = t.getResident();
            var unit = resident.getUnit();
            return new TicketDto(
                    t.getId(), t.getBuilding().getId(), resident.getId(), t.getCategory(),
                    t.getDescription(), t.getStatus(), t.getPriority(),
                    t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
                    t.getServiceVendor() == null ? null : t.getServiceVendor().getId(),
                    t.getCreatedAt(), t.getUpdatedAt(), t.getClosedAt(),
                    t.getImages().stream().map(TicketImageDto::from).toList(),
                    resident.getUser().getName(),
                    t.getAssignedTo() == null ? null : t.getAssignedTo().getName(),
                    unit == null ? null : unit.getUnitNumber());
        }
    }
}
