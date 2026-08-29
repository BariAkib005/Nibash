package com.nibash.seed;

import com.nibash.booking.Resource;
import com.nibash.booking.ResourceRepository;
import com.nibash.building.Building;
import com.nibash.community.Event;
import com.nibash.community.EventRepository;
import com.nibash.emergency.EmergencyContact;
import com.nibash.emergency.EmergencyContactRepository;
import com.nibash.finance.BillType;
import com.nibash.finance.BillTypeRepository;
import com.nibash.finance.Expense;
import com.nibash.finance.ExpenseRepository;
import com.nibash.finance.Invoice;
import com.nibash.finance.InvoiceItem;
import com.nibash.finance.InvoiceRepository;
import com.nibash.notice.Notice;
import com.nibash.notice.NoticeRepository;
import com.nibash.poll.Poll;
import com.nibash.poll.PollOption;
import com.nibash.poll.PollRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.Staff;
import com.nibash.staffing.StaffRepository;
import com.nibash.ticket.Ticket;
import com.nibash.ticket.TicketRepository;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Week 3 and 4 demo fixtures — money, work orders, the noticeboard, bookable rooms, a live poll and
 * an event.
 *
 * <p>Split out of {@link DemoSeeder} rather than bolted onto it: the account/building/unit fixture
 * is stable now, while module data grows every week, and keeping them apart means one file stops
 * changing. Same contract as the parent seeder — every row is upserted on a natural key, so running
 * this twice creates nothing new.
 */
@Service
public class ModuleSeeder {

    private final BillTypeRepository billTypes;
    private final InvoiceRepository invoices;
    private final ExpenseRepository expenses;
    private final TicketRepository tickets;
    private final NoticeRepository notices;
    private final ResourceRepository resources;
    private final PollRepository polls;
    private final EventRepository events;
    private final EmergencyContactRepository contacts;
    private final ResidentRepository residents;
    private final StaffRepository staff;

    public ModuleSeeder(BillTypeRepository billTypes, InvoiceRepository invoices,
                        ExpenseRepository expenses, TicketRepository tickets, NoticeRepository notices,
                        ResourceRepository resources, PollRepository polls, EventRepository events,
                        EmergencyContactRepository contacts, ResidentRepository residents,
                        StaffRepository staff) {
        this.billTypes = billTypes;
        this.invoices = invoices;
        this.expenses = expenses;
        this.tickets = tickets;
        this.notices = notices;
        this.resources = resources;
        this.polls = polls;
        this.events = events;
        this.contacts = contacts;
        this.residents = residents;
        this.staff = staff;
    }

    /** Returns the fragments the parent seeder folds into its summary line. */
    public List<String> seed(Building gulshan, Building banani, User admin1, User committee1) {
        List<String> report = new ArrayList<>();

        BillType serviceCharge = billType("Service Charge", "Monthly building maintenance charge");
        billType("Utility", "Metered water, gas and electricity");
        billType("Parking", "Reserved parking slot fee");
        report.add("3 bill types");

        report.add(seedFinance(gulshan, serviceCharge, admin1) + " invoices");
        report.add(seedExpenses(gulshan, admin1) + " expenses");
        report.add(seedTickets(gulshan) + " tickets");
        report.add(seedNotices(gulshan, committee1) + " notices");
        report.add(seedResources(gulshan, banani) + " bookable resources");
        report.add(seedPollsAndEvents(gulshan, committee1) + " polls/events");
        report.add(seedContacts(gulshan, banani) + " emergency contacts");

        return report;
    }

    // ------------------------------------------------------------------ finance

    private int seedFinance(Building building, BillType billType, User raisedBy) {
        List<Resident> members = residents.findByBuildingIdOrderByIdAsc(building.getId());
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        int created = 0;

        for (int i = 0; i < members.size(); i++) {
            Resident resident = members.get(i);
            // The same deterministic key generate-monthly uses, so the seeder and the batch agree.
            String number = "AUTO-%d-%d%02d".formatted(resident.getId(), month.getYear(), month.getMonthValue());
            if (invoices.findByInvoiceNumber(number).isPresent()) {
                continue;
            }

            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber(number);
            invoice.setResident(resident);
            invoice.setBuilding(building);
            invoice.setBillType(billType);
            invoice.setDueDate(month.plusMonths(1).withDayOfMonth(10));
            // A realistic mix: some settled, most outstanding, so the dashboard shows a real rate.
            invoice.setStatus(i % 3 == 0 ? Invoice.PAID : Invoice.PENDING);

            InvoiceItem line = new InvoiceItem();
            line.setDescription("Monthly Service Charge");
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(new BigDecimal("2000.00"));
            line.setTotalAmount(new BigDecimal("2000.00"));
            invoice.addItem(line);
            invoice.setAmount(new BigDecimal("2000.00"));

            invoices.save(invoice);
            created++;
        }
        return created;
    }

    private int seedExpenses(Building building, User createdBy) {
        record Row(String category, String amount, int monthsAgo, String description) {
        }
        List<Row> rows = List.of(
                new Row("Lift Maintenance", "18500.00", 0, "Quarterly AMC visit for both lifts"),
                new Row("Generator Fuel", "24000.00", 0, "Diesel top-up, 400 litres"),
                new Row("Cleaning Supplies", "6800.00", 1, "Monthly consumables"),
                new Row("Security", "45000.00", 1, "Guard salaries"),
                new Row("Lift Maintenance", "18500.00", 2, "Quarterly AMC visit"),
                new Row("Plumbing", "9200.00", 2, "Riser repair on floors 3-5"));

        int created = 0;
        for (Row row : rows) {
            LocalDate date = LocalDate.now().minusMonths(row.monthsAgo()).withDayOfMonth(12);
            boolean exists = expenses.findByBuildingIdIn(List.of(building.getId()),
                            org.springframework.data.domain.PageRequest.of(0, 200)).stream()
                    .anyMatch(e -> e.getCategory().equals(row.category()) && e.getDate().equals(date));
            if (exists) {
                continue;
            }
            Expense expense = new Expense();
            expense.setBuilding(building);
            expense.setCategory(row.category());
            expense.setAmount(new BigDecimal(row.amount()));
            expense.setDescription(row.description());
            expense.setDate(date);
            expense.setCreatedBy(createdBy);
            expenses.save(expense);
            created++;
        }
        return created;
    }

    // ------------------------------------------------------------------ maintenance

    private int seedTickets(Building building) {
        List<Resident> members = residents.findByBuildingIdOrderByIdAsc(building.getId());
        if (members.isEmpty()) {
            return 0;
        }
        record Row(String category, String description, String status, String priority) {
        }
        List<Row> rows = List.of(
                new Row("Maintenance", "Kitchen tap in 01A drips constantly", Ticket.OPEN, "medium"),
                new Row("Maintenance", "Bedroom AC in 02A is not cooling", Ticket.IN_PROGRESS, "high"),
                new Row("Cleaning", "Stairwell on floor 4 needs a deep clean", Ticket.OPEN, "low"),
                new Row("Security", "Intercom at the main gate cuts out", Ticket.RESOLVED, "high"),
                new Row("Maintenance", "Corridor light on floor 6 is flickering", Ticket.CLOSED, "low"));

        List<Staff> roster = staff.findByBuildingIdOrderByNameAsc(building.getId());
        int created = 0;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean exists = tickets.search(List.of(building.getId()), null, null,
                            org.springframework.data.domain.PageRequest.of(0, 200)).stream()
                    .anyMatch(t -> t.getDescription().equals(row.description()));
            if (exists) {
                continue;
            }

            Ticket ticket = new Ticket();
            ticket.setBuilding(building);
            ticket.setResident(members.get(i % members.size()));
            ticket.setCategory(row.category());
            ticket.setDescription(row.description());
            ticket.setStatus(row.status());
            ticket.setPriority(row.priority());

            // Mirror the controller's auto-assignment so seeded tickets look like created ones.
            roster.stream()
                    .filter(s -> s.getRole().toLowerCase().contains(row.category().toLowerCase()))
                    .findFirst()
                    .or(() -> roster.stream().findFirst())
                    .ifPresent(ticket::setAssignedTo);

            if (Ticket.RESOLVED.equals(row.status()) || Ticket.CLOSED.equals(row.status())) {
                ticket.setClosedAt(LocalDateTime.now().minusDays(2));
            }
            tickets.save(ticket);
            created++;
        }
        return created;
    }

    // ------------------------------------------------------------------ community

    private int seedNotices(Building building, User author) {
        record Row(String title, String body, boolean pinned, int publishedDaysAgo, Integer expiresInDays) {
        }
        List<Row> rows = List.of(
                new Row("Water supply interrupted on Friday",
                        "WASA maintenance means no supply between 9am and 2pm on Friday. "
                        + "Please store water the night before.", true, 2, 20),
                new Row("New waste collection time",
                        "Collection moves to 7:30am daily from the first of next month.", false, 6, null),
                new Row("Lift 2 out of service (resolved)",
                        "Lift 2 was out of service for a controller replacement. It is back in use.",
                        false, 45, -15));

        int created = 0;
        for (Row row : rows) {
            boolean exists = notices.board(List.of(building.getId()), null, row.title(),
                    org.springframework.data.domain.PageRequest.of(0, 5)).hasContent();
            if (exists) {
                continue;
            }
            Notice notice = new Notice();
            notice.setBuilding(building);
            notice.setCreatedBy(author);
            notice.setTitle(row.title());
            notice.setBody(row.body());
            notice.setPinned(row.pinned());
            notice.setPublishDate(LocalDateTime.now().minusDays(row.publishedDaysAgo()));
            if (row.expiresInDays() != null) {
                notice.setExpiryDate(LocalDateTime.now().plusDays(row.expiresInDays()));
            }
            notices.save(notice);
            created++;
        }
        return created;
    }

    private int seedResources(Building gulshan, Building banani) {
        int created = 0;
        created += resource(gulshan, "Rooftop Lounge", 40, "Level 12", "lounge");
        created += resource(gulshan, "Community Hall", 120, "Ground floor", "hall");
        created += resource(gulshan, "Gym", 15, "Basement 1", "gym");
        created += resource(banani, "Community Hall", 80, "Ground floor", "hall");
        return created;
    }

    private int resource(Building building, String name, int capacity, String location, String type) {
        boolean exists = resources.findByBuildingIdOrderByNameAsc(building.getId()).stream()
                .anyMatch(r -> r.getName().equals(name));
        if (exists) {
            return 0;
        }
        Resource resource = new Resource();
        resource.setBuilding(building);
        resource.setName(name);
        resource.setCapacity(capacity);
        resource.setLocation(location);
        resource.setType(type);
        resources.save(resource);
        return 1;
    }

    private int seedPollsAndEvents(Building building, User author) {
        int created = 0;
        String question = "Should we repaint the lobby this quarter?";

        boolean pollExists = polls.findByBuildingIdIn(List.of(building.getId()),
                        org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .anyMatch(p -> p.getQuestion().equals(question));

        if (!pollExists) {
            Poll poll = new Poll();
            poll.setBuilding(building);
            poll.setCreatedBy(author);
            poll.setQuestion(question);
            poll.setStartDate(LocalDateTime.now().minusDays(3));
            poll.setEndDate(LocalDateTime.now().plusDays(11));
            for (String choice : List.of("Yes, this quarter", "Next quarter", "Not needed")) {
                PollOption option = new PollOption();
                option.setOptionText(choice);
                poll.addOption(option);
            }
            polls.save(poll);
            created++;
        }

        String title = "Eid Milad community lunch";
        boolean eventExists = events.findByBuildingIdIn(List.of(building.getId()),
                        org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .anyMatch(e -> e.getTitle().equals(title));

        if (!eventExists) {
            Event event = new Event();
            event.setBuilding(building);
            event.setCreatedBy(author);
            event.setTitle(title);
            event.setDescription("Lunch in the community hall. Families welcome, please RSVP.");
            event.setEventDate(LocalDateTime.now().plusDays(12).withHour(13).withMinute(0)
                    .withSecond(0).withNano(0));
            events.save(event);
            created++;
        }
        return created;
    }

    private int seedContacts(Building gulshan, Building banani) {
        int created = 0;
        for (Building building : List.of(gulshan, banani)) {
            created += contact(building, "Fire Service", "999", "fire");
            created += contact(building, "Police (Gulshan)", "999", "police");
            created += contact(building, "Ambulance", "10921", "ambulance");
            created += contact(building, "Building Caretaker", "+8801711000011", "caretaker");
        }
        return created;
    }

    private int contact(Building building, String name, String phone, String type) {
        boolean exists = contacts.findByBuildingIdOrderByTypeAsc(building.getId()).stream()
                .anyMatch(c -> c.getName().equals(name));
        if (exists) {
            return 0;
        }
        EmergencyContact contact = new EmergencyContact();
        contact.setBuilding(building);
        contact.setName(name);
        contact.setPhone(phone);
        contact.setType(type);
        contacts.save(contact);
        return 1;
    }

    private BillType billType(String name, String description) {
        return billTypes.findByName(name).orElseGet(() -> {
            BillType type = new BillType();
            type.setName(name);
            type.setDescription(description);
            return billTypes.save(type);
        });
    }
}
