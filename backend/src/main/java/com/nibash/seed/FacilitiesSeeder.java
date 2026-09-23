package com.nibash.seed;

import com.nibash.access.AccessCard;
import com.nibash.access.AccessCardRepository;
import com.nibash.asset.Asset;
import com.nibash.asset.AssetMaintenance;
import com.nibash.asset.AssetMaintenanceRepository;
import com.nibash.asset.AssetRepository;
import com.nibash.asset.LiftStatusLog;
import com.nibash.asset.LiftStatusLogRepository;
import com.nibash.building.Building;
import com.nibash.chat.ChatRoom;
import com.nibash.chat.ChatRoomRepository;
import com.nibash.chat.Message;
import com.nibash.chat.MessageRepository;
import com.nibash.chat.RoomMember;
import com.nibash.chat.RoomMemberRepository;
import com.nibash.common.Times;
import com.nibash.document.Document;
import com.nibash.document.DocumentAclRole;
import com.nibash.document.DocumentAclRoleRepository;
import com.nibash.document.DocumentAuditLog;
import com.nibash.document.DocumentAuditLogRepository;
import com.nibash.document.DocumentRepository;
import com.nibash.intercom.IntercomDevice;
import com.nibash.intercom.IntercomDeviceRepository;
import com.nibash.intercom.IntercomLog;
import com.nibash.intercom.IntercomLogRepository;
import com.nibash.ml.MlCityPriceCache;
import com.nibash.ml.MlCityPriceCacheRepository;
import com.nibash.ml.MlModel;
import com.nibash.ml.MlModelRepository;
import com.nibash.parking.ParkingSlot;
import com.nibash.parking.ParkingSlotRepository;
import com.nibash.parking.Vehicle;
import com.nibash.parking.VehicleRepository;
import com.nibash.rental.Listing;
import com.nibash.rental.ListingRepository;
import com.nibash.rental.RentalRequest;
import com.nibash.rental.RentalRequestRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.storage.StorageService;
import com.nibash.unit.Unit;
import com.nibash.unit.UnitRepository;
import com.nibash.user.User;
import com.nibash.utility.UtilityBill;
import com.nibash.utility.UtilityBillRepository;
import com.nibash.utility.UtilityMeter;
import com.nibash.utility.UtilityMeterRepository;
import com.nibash.vendor.Review;
import com.nibash.vendor.ReviewRepository;
import com.nibash.vendor.Service;
import com.nibash.vendor.ServiceRepository;
import com.nibash.vendor.Vendor;
import com.nibash.vendor.VendorRepository;
import com.nibash.visitor.Appointment;
import com.nibash.visitor.AppointmentRepository;
import com.nibash.waste.WasteSchedule;
import com.nibash.waste.WasteScheduleRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

/**
 * Week 5 demo fixtures (spec §14 items 7, 9, 11–14): vendors and reviews, documents, intercom,
 * chat, rentals, utilities, assets and lifts, waste, access cards, parking, the ML estimator — and
 * the visitor appointment the Week 4 fixture never seeded.
 *
 * <p>Same contract as {@link ModuleSeeder}: every row is upserted on a natural key, so running the
 * seeder again creates nothing new. Everything hangs off Gulshan Lakeview Heights, the building
 * the demo journeys walk through.
 */
@org.springframework.stereotype.Service
public class FacilitiesSeeder {

    private final ServiceRepository services;
    private final VendorRepository vendors;
    private final ReviewRepository reviews;
    private final DocumentRepository documents;
    private final DocumentAclRoleRepository documentRoles;
    private final DocumentAuditLogRepository documentAudit;
    private final IntercomDeviceRepository intercomDevices;
    private final IntercomLogRepository intercomLogs;
    private final ChatRoomRepository rooms;
    private final RoomMemberRepository members;
    private final MessageRepository messages;
    private final ListingRepository listings;
    private final RentalRequestRepository rentalRequests;
    private final UtilityMeterRepository meters;
    private final UtilityBillRepository bills;
    private final AssetRepository assets;
    private final AssetMaintenanceRepository maintenance;
    private final LiftStatusLogRepository lifts;
    private final WasteScheduleRepository waste;
    private final AccessCardRepository cards;
    private final ParkingSlotRepository slots;
    private final VehicleRepository vehicles;
    private final MlModelRepository models;
    private final MlCityPriceCacheRepository priceCache;
    private final AppointmentRepository appointments;
    private final ResidentRepository residents;
    private final UnitRepository units;
    private final StorageService storage;

    public FacilitiesSeeder(ServiceRepository services, VendorRepository vendors, ReviewRepository reviews,
                            DocumentRepository documents, DocumentAclRoleRepository documentRoles,
                            DocumentAuditLogRepository documentAudit, IntercomDeviceRepository intercomDevices,
                            IntercomLogRepository intercomLogs, ChatRoomRepository rooms,
                            RoomMemberRepository members, MessageRepository messages, ListingRepository listings,
                            RentalRequestRepository rentalRequests, UtilityMeterRepository meters,
                            UtilityBillRepository bills, AssetRepository assets,
                            AssetMaintenanceRepository maintenance, LiftStatusLogRepository lifts,
                            WasteScheduleRepository waste, AccessCardRepository cards, ParkingSlotRepository slots,
                            VehicleRepository vehicles, MlModelRepository models,
                            MlCityPriceCacheRepository priceCache, AppointmentRepository appointments,
                            ResidentRepository residents, UnitRepository units, StorageService storage) {
        this.services = services;
        this.vendors = vendors;
        this.reviews = reviews;
        this.documents = documents;
        this.documentRoles = documentRoles;
        this.documentAudit = documentAudit;
        this.intercomDevices = intercomDevices;
        this.intercomLogs = intercomLogs;
        this.rooms = rooms;
        this.members = members;
        this.messages = messages;
        this.listings = listings;
        this.rentalRequests = rentalRequests;
        this.meters = meters;
        this.bills = bills;
        this.assets = assets;
        this.maintenance = maintenance;
        this.lifts = lifts;
        this.waste = waste;
        this.cards = cards;
        this.slots = slots;
        this.vehicles = vehicles;
        this.models = models;
        this.priceCache = priceCache;
        this.appointments = appointments;
        this.residents = residents;
        this.units = units;
        this.storage = storage;
    }

    /** Returns the fragments the parent seeder folds into its summary line. */
    public List<String> seed(Building gulshan, User committee1, User resident1, User resident2, User admin1) {
        List<String> report = new ArrayList<>();
        Resident owner1 = residentOf(resident1, gulshan);
        Resident tenant2 = residentOf(resident2, gulshan);
        Resident committee = residentOf(committee1, gulshan);
        if (owner1 == null || tenant2 == null || committee == null) {
            report.add("facilities skipped (residents missing)");
            return report;
        }

        report.add(seedVendors(gulshan, owner1, committee) + " vendors/reviews");
        report.add(seedDocuments(gulshan, committee1) + " documents");
        report.add(seedIntercom(gulshan) + " intercom rows");
        report.add(seedChat(gulshan, owner1, tenant2, committee, admin1) + " chat rows");
        report.add(seedRentals(gulshan, committee, tenant2) + " rental rows");
        report.add(seedUtilities(gulshan) + " utility rows");
        report.add(seedAssets(gulshan) + " asset rows");
        report.add(seedWaste(gulshan) + " waste schedules");
        report.add(seedAccessCard(owner1) + " access cards");
        report.add(seedParking(gulshan, owner1, committee) + " parking rows");
        report.add(seedMl() + " ML rows");
        report.add(seedAppointment(gulshan, owner1) + " visitor passes");
        return report;
    }

    // ------------------------------------------------------------------ vendors (spec §14.7)

    private int seedVendors(Building gulshan, Resident reviewerA, Resident reviewerB) {
        int created = 0;
        Service plumbing = service("Plumbing");
        Service electrical = service("Electrical");
        Service cleaning = service("Cleaning");
        Service liftService = service("Lift Maintenance");
        service("Security");

        // Coordinates are real Dhaka neighbourhoods, so the nearby search has honest distances:
        // from Gulshan 2 the Mirpur plumber is ~5.4 km — just outside the default 5 km radius.
        long before = vendors.count();
        Vendor rahman = vendor("Rahman Plumbing Works", plumbing, gulshan, "+8801712345601", "4.5", "23.792500", "90.407800");
        vendor("Mirpur Pipe Fixers", plumbing, null, "+8801712345602", "3.9", "23.822300", "90.365400");
        vendor("Bijli Electric Services", electrical, null, "+8801712345603", "4.3", "23.780600", "90.419300");
        Vendor liftCare = vendor("Dhaka Lift Care Ltd.", liftService, gulshan, "+8801712345604", "4.8", "23.781500", "90.400500");
        vendor("CleanPro Dhaka", cleaning, null, "+8801712345605", "4.1", "23.793700", "90.406600");
        created += (int) (vendors.count() - before);

        created += review(liftCare, reviewerA, 5, "Fixed the lift sensor the same afternoon. Very professional.");
        created += review(rahman, reviewerB, 5, "Came within the hour for the riser leak and left the stairwell clean.");
        created += review(rahman, reviewerA, 4, "Good work, slightly pricier than quoted.");
        // Reviewed vendors carry the average of their reviews, as the review API keeps them.
        for (Vendor reviewed : List.of(rahman, liftCare)) {
            reviewed.setRating(reviews.roundedAverage(reviewed.getId()));
            vendors.save(reviewed);
        }
        return created;
    }

    private Service service(String name) {
        return services.findByNameIgnoreCase(name).orElseGet(() -> {
            Service s = new Service();
            s.setName(name);
            return services.save(s);
        });
    }

    private Vendor vendor(String name, Service service, Building building, String contact, String rating,
                          String lat, String lng) {
        return vendors.findFirstByNameIgnoreCase(name).orElseGet(() -> {
            Vendor v = new Vendor();
            v.setName(name);
            v.setService(service);
            v.setBuilding(building);
            v.setContactInfo(contact);
            v.setRating(new BigDecimal(rating));
            v.setLatitude(new BigDecimal(lat));
            v.setLongitude(new BigDecimal(lng));
            return vendors.save(v);
        });
    }

    private int review(Vendor vendor, Resident author, int stars, String comment) {
        boolean exists = reviews.findByResidentBuildingIdInAndVendorId(List.of(author.getBuilding().getId()),
                        vendor.getId(), PageRequest.of(0, 50)).stream()
                .anyMatch(r -> r.getResident().getId().equals(author.getId()));
        if (exists) {
            return 0;
        }
        Review r = new Review();
        r.setVendor(vendor);
        r.setResident(author);
        r.setRating((short) stars);
        r.setComment(comment);
        reviews.saveAndFlush(r);
        return 1;
    }

    // ------------------------------------------------------------------ documents (spec §14.11)

    private int seedDocuments(Building gulshan, User uploader) {
        int created = 0;
        if (documents.findFirstByBuildingIdAndTitle(gulshan.getId(), "Building bylaws").isEmpty()) {
            Document bylaws = document(gulshan, uploader, "Building bylaws", 4, null, "building-bylaws-v4.pdf",
                    List.of("Gulshan Lakeview Heights - Building bylaws (version 4)",
                            "1. Quiet hours run from 22:00 to 07:00.",
                            "2. The rooftop lounge is bookable through Nibash.",
                            "3. Visitors are registered at the gate with a QR pass."));
            DocumentAclRole grant = new DocumentAclRole();
            grant.setDocument(bylaws);
            grant.setRole("resident");
            grant.setCanView(true);
            grant.setCanEdit(false);
            documentRoles.save(grant);
            created++;
        }
        if (documents.findFirstByBuildingIdAndTitle(gulshan.getId(), "Annual budget 2026").isEmpty()) {
            Document v1 = document(gulshan, uploader, "Annual budget 2026", 1, null, "annual-budget-2026-v1.pdf",
                    List.of("Annual budget 2026 - draft", "Service charge: BDT 2,000 per unit per month."));
            v1.setActive(false);
            documents.save(v1);
            document(gulshan, uploader, "Annual budget 2026", 2, v1, "annual-budget-2026-v2.pdf",
                    List.of("Annual budget 2026 - approved at the AGM",
                            "Service charge: BDT 2,000 per unit per month.",
                            "Lift modernisation reserve: BDT 1,200,000."));
            created += 2;
        }
        return created;
    }

    private Document document(Building building, User uploader, String title, int version, Document parent,
                              String fileName, List<String> lines) {
        String relative = "documents/" + fileName;
        writeIfMissing(relative, simplePdf(lines));
        Document d = new Document();
        d.setBuilding(building);
        d.setTitle(title);
        d.setFilePath(relative);
        d.setVersion(version);
        d.setMimeType("application/pdf");
        d.setParent(parent);
        d.setUploadedBy(uploader);
        Document saved = documents.save(d);
        DocumentAuditLog audit = new DocumentAuditLog();
        audit.setDocument(saved);
        audit.setUser(uploader);
        audit.setEventType(DocumentAuditLog.EDIT);
        documentAudit.save(audit);
        return saved;
    }

    private void writeIfMissing(String relative, byte[] bytes) {
        Path target = storage.resolve(relative);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write demo document " + relative, e);
        }
    }

    /** Marks where {@link #simplePdf} writes the page's content stream. */
    private static final String STREAM_PLACEHOLDER = "<content stream>";

    /** A minimal one-page PDF with a correct cross-reference table, so any viewer opens it. */
    static byte[] simplePdf(List<String> lines) {
        StringBuilder text = new StringBuilder("BT /F1 13 Tf 16 TL 72 770 Td ");
        for (String line : lines) {
            String escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
            text.append('(').append(escaped).append(") Tj T* ");
        }
        text.append("ET");
        byte[] stream = text.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                STREAM_PLACEHOLDER,
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n");
            if (STREAM_PLACEHOLDER.equals(objects.get(i))) {
                write(out, "<< /Length " + stream.length + " >>\nstream\n");
                out.writeBytes(stream);
                write(out, "\nendstream\n");
            } else {
                write(out, objects.get(i) + "\n");
            }
            write(out, "endobj\n");
        }
        int xref = out.size();
        write(out, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, "%010d 00000 n \n".formatted(offset));
        }
        write(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    // ------------------------------------------------------------------ intercom (spec §14.11)

    private int seedIntercom(Building gulshan) {
        if (intercomDevices.findByBuildingIdAndIpAddress(gulshan.getId(), "192.168.10.25").isPresent()) {
            return 0;
        }
        IntercomDevice device = new IntercomDevice();
        device.setBuilding(gulshan);
        device.setDeviceName("North Gate Intercom");
        device.setIpAddress("192.168.10.25");
        intercomDevices.save(device);

        IntercomLog ring = new IntercomLog();
        ring.setDevice(device);
        ring.setEventType("ring");
        ring.setTimestamp(Times.now().minusHours(3));
        ring.setDetails("Visitor rang flat 01A from the north gate.");
        intercomLogs.save(ring);
        return 2;
    }

    // ------------------------------------------------------------------ chat (spec §14.11)

    private int seedChat(Building gulshan, Resident owner1, Resident tenant2, Resident committee, User admin1) {
        int created = 0;
        List<Resident> everyone = residents.findByBuildingIdOrderByIdAsc(gulshan.getId());
        Resident admin = residentOf(admin1, gulshan);

        ChatRoom general = room(gulshan, "General", true);
        ChatRoom security = room(gulshan, "Security Desk", true);
        ChatRoom committeeRoom = room(gulshan, "Committee", false);
        for (Resident r : everyone) {
            created += member(general, r) + member(security, r);
        }
        created += member(committeeRoom, committee);
        if (admin != null) {
            created += member(committeeRoom, admin);
        }

        created += message(general, committee, "Welcome to the building chat! Please keep it friendly.", 26);
        created += message(general, owner1, "Thanks! Does anyone know when the rooftop lounge reopens?", 25);
        created += message(general, committee, "It reopens this Friday after the deep clean.", 24);
        created += message(security, tenant2, "The north gate intercom buzzes twice for every ring — known issue?", 5);
        created += message(committeeRoom, committee, "Draft agenda for the next committee meeting is in Documents.", 2);
        return created;
    }

    private ChatRoom room(Building building, String name, boolean isPublic) {
        return rooms.findByBuildingIdAndName(building.getId(), name).orElseGet(() -> {
            ChatRoom r = new ChatRoom();
            r.setBuilding(building);
            r.setName(name);
            r.setPublicRoom(isPublic);
            return rooms.save(r);
        });
    }

    private int member(ChatRoom room, Resident resident) {
        if (members.findByRoomIdAndResidentId(room.getId(), resident.getId()).isPresent()) {
            return 0;
        }
        RoomMember m = new RoomMember();
        m.setRoom(room);
        m.setResident(resident);
        members.save(m);
        return 1;
    }

    private int message(ChatRoom room, Resident sender, String content, int hoursAgo) {
        if (messages.existsByRoomIdAndContent(room.getId(), content)) {
            return 0;
        }
        Message m = new Message();
        m.setRoom(room);
        m.setResident(sender);
        m.setContent(content);
        m.setSentAt(Times.now().minusHours(hoursAgo));
        messages.save(m);
        return 1;
    }

    // ------------------------------------------------------------------ rentals (spec §14.12)

    private int seedRentals(Building gulshan, Resident lister, Resident requester) {
        String title = "Bright 2BHK on floor 3 — 03A";
        if (listings.findFirstByBuildingIdAndTitle(gulshan.getId(), title).isPresent()) {
            return 0;
        }
        Listing listing = new Listing();
        listing.setResident(lister);
        listing.setBuilding(gulshan);
        listing.setUnit(units.findByBuildingIdAndUnitNumber(gulshan.getId(), "03A").orElse(null));
        listing.setTitle(title);
        listing.setDescription("South-facing 2BHK, 1,250 sq ft, lift and generator backup, "
                + "gas line, one reserved parking bay. Family preferred.");
        listing.setRent(new BigDecimal("65000.00"));
        listing.setAvailableFrom(LocalDate.now().plusMonths(1).withDayOfMonth(1));
        listings.save(listing);

        RentalRequest request = new RentalRequest();
        request.setListing(listing);
        request.setTenant(requester);
        request.setStatus(RentalRequest.PENDING);
        rentalRequests.save(request);
        return 2;
    }

    // ------------------------------------------------------------------ utilities (spec §14.12)

    private int seedUtilities(Building gulshan) {
        int created = 0;
        LocalDate lastReading = LocalDate.now().minusMonths(1).withDayOfMonth(28);
        String[][] rates = {{"electricity", "E", "1850.00", "412.00"}, {"water", "W", "420.00", "18.00"}};
        for (String unitNumber : List.of("01A", "01B", "02A", "02B")) {
            Unit unit = units.findByBuildingIdAndUnitNumber(gulshan.getId(), unitNumber).orElse(null);
            if (unit == null) {
                continue;
            }
            for (String[] rate : rates) {
                String number = "GLH-" + rate[1] + "-" + unitNumber;
                UtilityMeter meter = meters.findByMeterNumberIgnoreCase(number).orElse(null);
                if (meter == null) {
                    meter = new UtilityMeter();
                    meter.setUnit(unit);
                    meter.setType(rate[0]);
                    meter.setMeterNumber(number);
                    meter = meters.save(meter);
                    created++;
                }
                if (!bills.existsByMeterIdAndReadingDate(meter.getId(), lastReading)) {
                    UtilityBill bill = new UtilityBill();
                    bill.setMeter(meter);
                    bill.setReadingDate(lastReading);
                    bill.setReadingValue(new BigDecimal(rate[3]));
                    bill.setAmount(new BigDecimal(rate[2]));
                    bill.setStatus(UtilityBill.PENDING);
                    bills.save(bill);
                    created++;
                }
            }
        }
        return created;
    }

    // ------------------------------------------------------------------ assets & lifts (spec §14.12)

    private int seedAssets(Building gulshan) {
        int created = 0;
        LocalDate today = LocalDate.now();
        Asset liftA = asset(gulshan, "Lift A", "Passenger lift", today.minusYears(4), today.plusYears(2), Asset.OPERATIONAL);
        // Inside the 60-day window, so the warranty warning shows in the demo.
        Asset liftB = asset(gulshan, "Lift B", "Passenger lift", today.minusYears(4), today.plusDays(35), Asset.OPERATIONAL);
        Asset generator = asset(gulshan, "Diesel Generator", "Standby generator", today.minusYears(6),
                today.minusMonths(8), Asset.UNDER_MAINTENANCE);
        asset(gulshan, "Water Pump 1", "Pump", today.minusYears(2), today.plusYears(1), Asset.OPERATIONAL);

        Vendor liftCare = vendors.findFirstByNameIgnoreCase("Dhaka Lift Care Ltd.").orElse(null);
        created += service(liftA, today.minusMonths(1), today.minusMonths(1).plusDays(1),
                "Quarterly AMC inspection", "18500.00", liftCare);
        created += service(generator, today.plusDays(6), null,
                "Replace fuel injectors and load-test", "32000.00", null);
        created += service(liftB, today.plusDays(20), null, "Door sensor replacement", "7500.00", liftCare);

        if (lifts.countByBuildingId(gulshan.getId()) == 0) {
            created += liftLog(gulshan, liftA, "operational", 2);
            created += liftLog(gulshan, liftB, "maintenance", 1);
        }
        return created;
    }

    private Asset asset(Building building, String name, String type, LocalDate purchased, LocalDate warranty,
                        String status) {
        return assets.findFirstByBuildingIdAndName(building.getId(), name).orElseGet(() -> {
            Asset a = new Asset();
            a.setBuilding(building);
            a.setName(name);
            a.setType(type);
            a.setPurchaseDate(purchased);
            a.setWarrantyExpiry(warranty);
            a.setStatus(status);
            return assets.save(a);
        });
    }

    private int service(Asset asset, LocalDate scheduled, LocalDate completed, String description, String cost,
                        Vendor vendor) {
        if (maintenance.existsByAssetIdAndDescription(asset.getId(), description)) {
            return 0;
        }
        AssetMaintenance m = new AssetMaintenance();
        m.setAsset(asset);
        m.setScheduledDate(scheduled);
        m.setCompletedDate(completed);
        m.setDescription(description);
        m.setCost(new BigDecimal(cost));
        m.setVendor(vendor);
        maintenance.save(m);
        return 1;
    }

    private int liftLog(Building building, Asset lift, String status, int hoursAgo) {
        LiftStatusLog log = new LiftStatusLog();
        log.setBuilding(building);
        log.setAsset(lift);
        log.setStatus(status);
        log.setTimestamp(Times.now().minusHours(hoursAgo));
        lifts.save(log);
        return 1;
    }

    // ------------------------------------------------------------------ waste (spec §14.12)

    private int seedWaste(Building gulshan) {
        if (waste.countByBuildingId(gulshan.getId()) > 0) {
            return 0;
        }
        // Recurring from a fixed past start, so "next collection" rolls forward and never goes stale.
        LocalDate anchor = LocalDate.now().minusWeeks(4);
        waste.save(schedule(gulshan, anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY)).atTime(7, 0), "weekly"));
        waste.save(schedule(gulshan, anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).atTime(8, 30), "weekly"));
        waste.save(schedule(gulshan, anchor.withDayOfMonth(1).atTime(9, 0), "monthly"));
        return 3;
    }

    private static WasteSchedule schedule(Building building, LocalDateTime first, String recurring) {
        WasteSchedule s = new WasteSchedule();
        s.setBuilding(building);
        s.setScheduleTime(first);
        s.setRecurring(recurring);
        return s;
    }

    // ------------------------------------------------------------------ access cards (spec §14.12)

    private int seedAccessCard(Resident holder) {
        if (cards.findByCardNumber("GLH-AC-0001").isPresent()) {
            return 0;
        }
        AccessCard card = new AccessCard();
        card.setResident(holder);
        card.setCardNumber("GLH-AC-0001");
        card.setIssuedAt(LocalDateTime.now().minusMonths(6));
        card.setStatus("active");
        cards.save(card);
        return 1;
    }

    // ------------------------------------------------------------------ parking (spec §14.13)

    private int seedParking(Building gulshan, Resident carOwner, Resident bikeOwner) {
        int created = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 6; column++) {
                String number = "G%d-%02d".formatted(row, column);
                if (slots.findByBuildingIdAndSlotNumber(gulshan.getId(), number).isEmpty()) {
                    ParkingSlot slot = new ParkingSlot();
                    slot.setBuilding(gulshan);
                    slot.setSlotNumber(number);
                    slot.setStatus(ParkingSlot.AVAILABLE);
                    slots.save(slot);
                    created++;
                }
            }
        }
        slots.findByBuildingIdAndSlotNumber(gulshan.getId(), "G1-02").ifPresent(reserved -> {
            if (ParkingSlot.AVAILABLE.equals(reserved.getStatus()) && !vehicles.existsByParkingSlotId(reserved.getId())) {
                reserved.setStatus(ParkingSlot.RESERVED);
                slots.save(reserved);
            }
        });
        created += vehicle(gulshan, carOwner, "DHAKA METRO-GA 11-2345", "car", "G1-01");
        created += vehicle(gulshan, bikeOwner, "DHAKA METRO-HA 22-6789", "motorbike", "G1-03");
        return created;
    }

    private int vehicle(Building building, Resident owner, String number, String type, String slotNumber) {
        if (vehicles.findByVehicleNumberIgnoreCase(number).isPresent()) {
            return 0;
        }
        ParkingSlot slot = slots.findByBuildingIdAndSlotNumber(building.getId(), slotNumber)
                .filter(s -> !vehicles.existsByParkingSlotId(s.getId()))
                .orElse(null);
        Vehicle v = new Vehicle();
        v.setResident(owner);
        v.setVehicleNumber(number);
        v.setType(type);
        v.setParkingSlot(slot);
        vehicles.save(v);
        if (slot != null) {
            slot.setStatus(ParkingSlot.OCCUPIED);
            slots.save(slot);
        }
        return 1;
    }

    // ------------------------------------------------------------------ ML (spec §14.14)

    private int seedMl() {
        int created = 0;
        MlModel model = models.findByNameAndVersion("Dhaka Rent Estimator", "v2026.05").orElse(null);
        if (model == null) {
            model = new MlModel();
            model.setName("Dhaka Rent Estimator");
            model.setVersion("v2026.05");
            model.setArtifactPath("models/dhaka-rent-estimator-v2026.05.onnx");
            model = models.save(model);
            created++;
        }
        for (Map.Entry<String, String> city : Map.of("Dhaka", "72000.00", "Chattogram", "38000.00").entrySet()) {
            if (priceCache.findByCityIgnoreCaseAndModelId(city.getKey(), model.getId()).isEmpty()) {
                MlCityPriceCache row = new MlCityPriceCache();
                row.setCity(city.getKey());
                row.setCurrency("BDT");
                row.setEstimate(new BigDecimal(city.getValue()).setScale(2, RoundingMode.HALF_UP));
                row.setModel(model);
                priceCache.save(row);
                created++;
            }
        }
        return created;
    }

    // ------------------------------------------------------------------ visitor pass (spec §14.9)

    /**
     * An approved pass for the evening of the day the seeder runs — scannable in that day's demo.
     * Keyed on the visitor's name, so a later re-seed leaves it (and its date) alone.
     */
    private int seedAppointment(Building gulshan, Resident host) {
        boolean exists = appointments.findByBuildingIdIn(List.of(gulshan.getId()), PageRequest.of(0, 200)).stream()
                .anyMatch(a -> "Tahmid Karim".equals(a.getVisitorName()));
        if (exists) {
            return 0;
        }
        Appointment pass = new Appointment();
        pass.setBuilding(gulshan);
        pass.setResident(host);
        pass.setVisitorName("Tahmid Karim");
        pass.setVisitorPhone("+8801700000021");
        pass.setScheduledTime(LocalDate.now().atTime(18, 0));
        pass.setApproved(true);
        pass.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        appointments.save(pass);
        return 1;
    }

    private Resident residentOf(User user, Building building) {
        return user == null ? null : residents.findByUserIdAndBuildingId(user.getId(), building.getId()).orElse(null);
    }
}
