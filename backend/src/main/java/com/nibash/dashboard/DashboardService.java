package com.nibash.dashboard;

import com.nibash.asset.Asset;
import com.nibash.asset.AssetController.AssetDto;
import com.nibash.asset.AssetMaintenance;
import com.nibash.asset.AssetMaintenanceController.MaintenanceDto;
import com.nibash.asset.LiftController;
import com.nibash.asset.LiftStatusLog;
import com.nibash.auth.AuthService;
import com.nibash.auth.dto.AuthDtos;
import com.nibash.booking.Booking;
import com.nibash.booking.BookingController.BookingDto;
import com.nibash.booking.Resource;
import com.nibash.booking.ResourceController.ResourceDto;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.chat.ChatAccess;
import com.nibash.chat.ChatMessageController.MessageDto;
import com.nibash.chat.ChatRoom;
import com.nibash.chat.ChatRoomController.RoomDto;
import com.nibash.chat.MessageRepository;
import com.nibash.common.Dtos.DirectoryEntry;
import com.nibash.common.Dtos.StaffDto;
import com.nibash.common.Dtos.UnitDto;
import com.nibash.document.DocumentController.DocumentDto;
import com.nibash.document.DocumentRepository;
import com.nibash.emergency.Emergency;
import com.nibash.emergency.EmergencyContact;
import com.nibash.emergency.EmergencyContactController.ContactDto;
import com.nibash.emergency.EmergencyController.EmergencyDto;
import com.nibash.finance.FinanceDtos.InvoiceDto;
import com.nibash.finance.Invoice;
import com.nibash.gate.GateEvent;
import com.nibash.gate.GateEventController.GateEventDto;
import com.nibash.intercom.IntercomController.LogDto;
import com.nibash.intercom.IntercomLogRepository;
import com.nibash.notice.Notice;
import com.nibash.notice.NoticeController.NoticeDto;
import com.nibash.notification.Notification;
import com.nibash.notification.NotificationController.NotificationDto;
import com.nibash.parking.ParkingController;
import com.nibash.parking.ParkingController.SlotDto;
import com.nibash.parking.ParkingSlot;
import com.nibash.parking.VehicleController.VehicleDto;
import com.nibash.parking.VehicleRepository;
import com.nibash.poll.Poll;
import com.nibash.poll.PollController.OptionDto;
import com.nibash.poll.PollController.PollDto;
import com.nibash.rental.ListingController.ListingDto;
import com.nibash.rental.ListingRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.Attendance;
import com.nibash.staffing.AttendanceController.AttendanceDto;
import com.nibash.staffing.Staff;
import com.nibash.tenancy.TenantService;
import com.nibash.ticket.Ticket;
import com.nibash.ticket.TicketDtos.TicketDto;
import com.nibash.unit.Unit;
import com.nibash.user.User;
import com.nibash.vendor.ReviewController.ReviewDto;
import com.nibash.vendor.ReviewRepository;
import com.nibash.vendor.ServiceController.ServiceDto;
import com.nibash.vendor.VendorController.VendorDto;
import com.nibash.vendor.VendorRepository;
import com.nibash.visitor.Appointment;
import com.nibash.visitor.Visitor;
import com.nibash.visitor.VisitorDtos.AppointmentDto;
import com.nibash.visitor.VisitorDtos.VisitorDto;
import com.nibash.waste.WasteSchedule;
import com.nibash.waste.WasteScheduleController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/dashboard/summary/} — the single call that hydrates every dashboard module (spec §9).
 *
 * <p>Efficiency is the point of the endpoint, so every section is one query: to-one associations
 * the DTOs touch are join-fetched, and collections (invoice items, ticket images, poll options)
 * load through {@code hibernate.default_batch_fetch_size} as one {@code IN (...)} query each. The
 * statement count therefore stays flat as the data grows — the integration test pins it.
 *
 * <p>A {@code building_id} outside the caller's buildings silently falls back to their own
 * building (§6.4); it never errors and never leaks.
 */
@Service
public class DashboardService {

    private static final List<String> OCCUPIED = List.of(Unit.OCCUPIED, Unit.SOLD, Unit.RENTED);
    private static final List<String> CLOSED_TICKETS = List.of(Ticket.RESOLVED, Ticket.CLOSED);

    @PersistenceContext
    private EntityManager em;

    private final TenantService tenancy;
    private final BuildingRepository buildings;
    private final ResidentRepository residents;
    private final AuthService auth;
    private final ChatAccess chat;
    private final MessageRepository messages;
    private final VendorRepository vendors;
    private final ReviewRepository reviews;
    private final DocumentRepository documents;
    private final IntercomLogRepository intercomLogs;
    private final ListingRepository listings;
    private final VehicleRepository vehicles;
    private final ParkingController parking;
    private final ZoneId zone;

    public DashboardService(TenantService tenancy, BuildingRepository buildings, ResidentRepository residents,
                            AuthService auth, ChatAccess chat, MessageRepository messages, VendorRepository vendors,
                            ReviewRepository reviews, DocumentRepository documents,
                            IntercomLogRepository intercomLogs, ListingRepository listings,
                            VehicleRepository vehicles, ParkingController parking,
                            @Value("${nibash.timezone}") String timezone) {
        this.tenancy = tenancy;
        this.buildings = buildings;
        this.residents = residents;
        this.auth = auth;
        this.chat = chat;
        this.messages = messages;
        this.vendors = vendors;
        this.reviews = reviews;
        this.documents = documents;
        this.intercomLogs = intercomLogs;
        this.listings = listings;
        this.vehicles = vehicles;
        this.parking = parking;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(User caller, Long requestedBuildingId) {
        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        List<Building> visible = allowed.isEmpty() ? List.of() : buildings.findByIdInOrderByNameAsc(allowed);

        Map<String, Object> out = new LinkedHashMap<>();
        if (visible.isEmpty()) {
            out.put("building", null);
            out.put("buildings", List.of());
            out.put("me", AuthDtos.UserDto.from(caller));
            out.put("current_resident_id", null);
            out.put("metrics", Map.of());
            out.put("sections", Map.of());
            return out;
        }

        Building building = select(caller, visible, requestedBuildingId);
        Long id = building.getId();

        out.put("building", AuthDtos.BuildingDto.from(building));
        out.put("buildings", visible.stream().map(AuthDtos.BuildingDto::from).toList());
        out.put("me", AuthDtos.UserDto.from(caller));
        out.put("current_resident_id",
                residents.findByUserIdAndBuildingId(caller.getId(), id).map(Resident::getId).orElse(null));
        out.put("metrics", metrics(id));
        out.put("sections", sections(caller, id));
        return out;
    }

    // ---------------------------------------------------------------- building selection

    private Building select(User caller, List<Building> visible, Long requested) {
        if (requested != null) {
            for (Building b : visible) {
                if (b.getId().equals(requested)) {
                    return b;
                }
            }
        }
        Long home = auth.homeBuilding(caller).map(Building::getId).orElse(null);
        return visible.stream().filter(b -> b.getId().equals(home)).findFirst().orElse(visible.getFirst());
    }

    // ---------------------------------------------------------------- metrics

    private Map<String, Object> metrics(Long buildingId) {
        Object[] invoiceTotals = em.createQuery("""
                        select coalesce(sum(case when i.status <> 'paid' then i.amount else 0 end), 0),
                               count(i),
                               coalesce(sum(case when i.status = 'paid' then 1 else 0 end), 0)
                        from Invoice i where i.building.id = :b
                        """, Object[].class)
                .setParameter("b", buildingId).getSingleResult();
        BigDecimal outstanding = toDecimal(invoiceTotals[0]);
        long invoiceCount = ((Number) invoiceTotals[1]).longValue();
        long paidCount = ((Number) invoiceTotals[2]).longValue();

        BigDecimal paymentsTotal = toDecimal(em.createQuery(
                        "select coalesce(sum(p.amount), 0) from Payment p where p.invoice.building.id = :b")
                .setParameter("b", buildingId).getSingleResult());

        long openTickets = em.createQuery(
                        "select count(t) from Ticket t where t.building.id = :b and t.status not in :closed", Long.class)
                .setParameter("b", buildingId).setParameter("closed", CLOSED_TICKETS).getSingleResult();

        LocalDate today = LocalDate.now(zone);
        long visitorsToday = em.createQuery("""
                        select count(a) from Appointment a
                        where a.building.id = :b and a.scheduledTime >= :from and a.scheduledTime < :to
                        """, Long.class)
                .setParameter("b", buildingId)
                .setParameter("from", today.atStartOfDay())
                .setParameter("to", today.plusDays(1).atStartOfDay())
                .getSingleResult();

        Object[] unitTotals = em.createQuery("""
                        select count(u), coalesce(sum(case when u.status in :occupied then 1 else 0 end), 0)
                        from Unit u where u.building.id = :b
                        """, Object[].class)
                .setParameter("b", buildingId).setParameter("occupied", OCCUPIED).getSingleResult();
        long totalUnits = ((Number) unitTotals[0]).longValue();
        long occupiedUnits = ((Number) unitTotals[1]).longValue();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("outstanding", outstanding);
        metrics.put("payments_total", paymentsTotal);
        metrics.put("collection_rate", invoiceCount == 0 ? 0 : Math.round(paidCount * 100.0 / invoiceCount));
        metrics.put("open_tickets", openTickets);
        metrics.put("visitors_today", visitorsToday);
        metrics.put("occupancy_rate", totalUnits == 0 ? 0 : Math.round(occupiedUnits * 100.0 / totalUnits));
        metrics.put("occupied_units", occupiedUnits);
        metrics.put("total_units", totalUnits);
        return metrics;
    }

    // ---------------------------------------------------------------- sections

    private Map<String, Object> sections(User caller, Long b) {
        Map<String, Object> s = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(zone);
        LocalDateTime now = LocalDateTime.now(zone);

        s.put("invoices", top(Invoice.class, """
                select i from Invoice i join fetch i.resident r join fetch r.user left join fetch r.unit
                where i.building.id = :b order by i.createdAt desc, i.id desc""", b, 10)
                .stream().map(InvoiceDto::from).toList());

        s.put("expenses", expenseCategories(b));

        s.put("notices", top(Notice.class, """
                select n from Notice n
                where n.building.id = :b
                  and n.publishDate <= :now and (n.expiryDate is null or n.expiryDate >= :now)
                order by n.pinned desc, n.publishDate desc, n.id desc""", b, 8, Map.of("now", now))
                .stream().map(NoticeDto::from).toList());

        s.put("appointments", top(Appointment.class, """
                select a from Appointment a join fetch a.resident r join fetch r.user left join fetch r.unit
                where a.building.id = :b order by a.scheduledTime desc, a.id desc""", b, 8)
                .stream().map(AppointmentDto::from).toList());

        s.put("visitors", top(Visitor.class, """
                select v from Visitor v join fetch v.appointment a join fetch a.resident r join fetch r.user
                where a.building.id = :b order by v.checkinTime desc nulls last, v.id desc""", b, 8)
                .stream().map(VisitorDto::from).toList());

        s.put("tickets", top(Ticket.class, """
                select t from Ticket t join fetch t.resident r join fetch r.user left join fetch r.unit
                left join fetch t.assignedTo
                where t.building.id = :b order by t.createdAt desc, t.id desc""", b, 12)
                .stream().map(TicketDto::from).toList());

        s.put("services", em.createQuery("select s from Service s order by s.name asc",
                        com.nibash.vendor.Service.class)
                .setMaxResults(12).getResultList().stream().map(ServiceDto::from).toList());

        s.put("vendors", vendors.findBestRated(b, PageRequest.of(0, 8)).stream().map(VendorDto::from).toList());

        s.put("reviews", reviews.findNewestForBuildingVendors(b, PageRequest.of(0, 8))
                .stream().map(ReviewDto::from).toList());

        s.put("resources", all(Resource.class,
                "select r from Resource r where r.building.id = :b order by r.name asc", b)
                .stream().map(ResourceDto::from).toList());

        s.put("bookings", top(Booking.class, """
                select bk from Booking bk join fetch bk.resource res join fetch bk.resident r join fetch r.user
                where res.building.id = :b order by bk.startTime desc, bk.id desc""", b, 8)
                .stream().map(BookingDto::from).toList());

        s.put("polls", polls(b));

        s.put("documents", documents.findNewestActive(b, PageRequest.of(0, 8))
                .stream().map(DocumentDto::from).toList());

        s.put("emergencies", top(Emergency.class, """
                select e from Emergency e join fetch e.resident r join fetch r.user left join fetch r.unit
                where e.building.id = :b order by e.timestamp desc, e.id desc""", b, 5)
                .stream().map(EmergencyDto::from).toList());

        s.put("staff", all(Staff.class, "select st from Staff st where st.building.id = :b order by st.name asc", b)
                .stream().map(StaffDto::from).toList());

        s.put("attendance", top(Attendance.class, """
                select at from Attendance at join fetch at.staff st
                where st.building.id = :b order by at.checkinTime desc, at.id desc""", b, 8)
                .stream().map(AttendanceDto::from).toList());

        s.put("directory", directory(b));

        s.put("intercom_logs", intercomLogs.findNewest(b, PageRequest.of(0, 8)).stream().map(LogDto::from).toList());

        List<ChatRoom> rooms = chat.readableRooms(caller, List.of(b));
        s.put("chat_rooms", rooms.stream().map(RoomDto::from).toList());
        List<MessageDto> latestMessages = new ArrayList<>();
        if (!rooms.isEmpty()) {
            messages.findLatestInRoom(rooms.getFirst().getId(), PageRequest.of(0, 30))
                    .forEach(m -> latestMessages.add(MessageDto.from(m)));
            Collections.reverse(latestMessages);
        }
        s.put("messages", latestMessages);

        s.put("listings", listings.findNewest(b, PageRequest.of(0, 8)).stream().map(ListingDto::from).toList());

        s.put("gate_logs", top(GateEvent.class, """
                select g from GateEvent g left join fetch g.actor
                where g.building.id = :b order by g.timestamp desc, g.id desc""", b, 10)
                .stream().map(GateEventDto::from).toList());

        s.put("lifts", lifts(b));

        s.put("waste", WasteScheduleController.upcoming(
                all(WasteSchedule.class, "select w from WasteSchedule w where w.building.id = :b", b), 5));

        s.put("units", all(Unit.class,
                "select u from Unit u where u.building.id = :b order by u.floor asc, u.unitNumber asc", b)
                .stream().map(UnitDto::from).toList());

        s.put("assets", all(Asset.class, "select a from Asset a where a.building.id = :b order by a.name asc", b)
                .stream().map(a -> AssetDto.from(a, today)).toList());

        s.put("asset_maintenance", top(AssetMaintenance.class, """
                select m from AssetMaintenance m join fetch m.asset a left join fetch m.vendor
                where a.building.id = :b order by m.scheduledDate desc, m.id desc""", b, 8)
                .stream().map(MaintenanceDto::from).toList());

        s.put("parking_slots", all(ParkingSlot.class,
                "select p from ParkingSlot p where p.building.id = :b order by p.slotNumber asc", b)
                .stream().map(SlotDto::from).toList());

        s.put("vehicles", vehicles.findForBuilding(b).stream().map(VehicleDto::from).toList());

        s.put("parking_layout", parking.readLayout(b));

        s.put("notifications", top(Notification.class, """
                select n from Notification n where n.building.id = :b
                order by n.sentAt desc, n.id desc""", b, 8)
                .stream().map(NotificationDto::from).toList());

        s.put("emergency_contacts", all(EmergencyContact.class,
                "select c from EmergencyContact c where c.building.id = :b order by c.type asc, c.name asc", b)
                .stream().map(ContactDto::from).toList());
        return s;
    }

    /** Top expense categories by total: {@code {category, total, entries}}. */
    private List<Map<String, Object>> expenseCategories(Long b) {
        List<Object[]> rows = em.createQuery("""
                        select e.category, sum(e.amount), count(e) from Expense e
                        where e.building.id = :b group by e.category order by sum(e.amount) desc
                        """, Object[].class)
                .setParameter("b", b).setMaxResults(8).getResultList();
        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", row[0]);
            m.put("total", toDecimal(row[1]));
            m.put("entries", ((Number) row[2]).longValue());
            return m;
        }).toList();
    }

    /** Five newest polls with per-option tallies — all five tallied by one grouped query. */
    private List<PollDto> polls(Long b) {
        List<Poll> polls = top(Poll.class, """
                select p from Poll p join fetch p.createdBy
                where p.building.id = :b order by p.startDate desc, p.id desc""", b, 5);
        if (polls.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        em.createQuery("""
                        select v.option.id, count(v) from Vote v
                        where v.poll.id in :ids group by v.option.id
                        """, Object[].class)
                .setParameter("ids", polls.stream().map(Poll::getId).toList())
                .getResultList()
                .forEach(row -> counts.put((Long) row[0], ((Number) row[1]).longValue()));

        return polls.stream().map(poll -> {
            long total = poll.getOptions().stream().mapToLong(o -> counts.getOrDefault(o.getId(), 0L)).sum();
            List<OptionDto> options = poll.getOptions().stream().map(option -> {
                long count = counts.getOrDefault(option.getId(), 0L);
                return OptionDto.from(option, count, percentage(count, total));
            }).toList();
            return new PollDto(poll.getId(), poll.getBuilding().getId(), poll.getQuestion(),
                    poll.getCreatedBy().getId(), poll.getStartDate(), poll.getEndDate(), poll.isClosed(), total, options);
        }).toList();
    }

    /** Every resident, with the same opt-in privacy gating as {@code /api/directory/} (spec §8.1). */
    private List<DirectoryEntry> directory(Long b) {
        return all(Resident.class, """
                select r from Resident r join fetch r.user u left join fetch r.unit un
                where r.building.id = :b
                order by coalesce(un.unitNumber, 'zzzz') asc, u.name asc""", b)
                .stream().map(DirectoryEntry::from).toList();
    }

    /** The §9 {@code lifts} shape: {@code [{id, asset_id, name, status, timestamp}]}. */
    private List<Map<String, Object>> lifts(Long b) {
        List<LiftStatusLog> newestFirst = all(LiftStatusLog.class, """
                select l from LiftStatusLog l left join fetch l.asset
                where l.building.id = :b order by l.timestamp desc, l.id desc""", b);
        return LiftController.latestPerLift(newestFirst).stream().map(lift -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", lift.id());
            m.put("asset_id", lift.asset());
            m.put("name", lift.name());
            m.put("status", lift.status());
            m.put("timestamp", lift.timestamp());
            return m;
        }).toList();
    }

    // ---------------------------------------------------------------- query helpers

    private <T> List<T> top(Class<T> type, String jpql, Long buildingId, int limit) {
        return top(type, jpql, buildingId, limit, Map.of());
    }

    private <T> List<T> top(Class<T> type, String jpql, Long buildingId, int limit, Map<String, Object> params) {
        var query = em.createQuery(jpql, type).setParameter("b", buildingId).setMaxResults(limit);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    private <T> List<T> all(Class<T> type, String jpql, Long buildingId) {
        return em.createQuery(jpql, type).setParameter("b", buildingId).getResultList();
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
    }

    private static BigDecimal percentage(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
