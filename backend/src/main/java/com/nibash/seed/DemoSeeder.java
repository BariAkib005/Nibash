package com.nibash.seed;

import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.building.BuildingSetting;
import com.nibash.building.BuildingSettingRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.Staff;
import com.nibash.staffing.StaffRepository;
import com.nibash.unit.Unit;
import com.nibash.unit.UnitRepository;
import com.nibash.user.Roles;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The demo fixture from spec §14, seeded <b>idempotently</b>: every row is upserted on its natural
 * key, so running this twice creates nothing new and never duplicates.
 *
 * <p>Scope note: this seeds everything the Week 2 screens render — the ten demo accounts across all
 * five roles, both buildings, module settings, units with a realistic occupancy spread, residents
 * and staff. Module fixtures (invoices, tickets, visitors, bookings…) are seeded alongside their
 * modules in Weeks 3–5, when those entities exist. The seeder is designed to grow that way.
 */
@Service
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    /** Spec §4.4 — every demo account shares this password. */
    public static final String DEMO_PASSWORD = "Nibash@2026";

    private static final List<String> MODULES = List.of(
            "Finance", "Visitors", "Maintenance", "Messaging", "Documents",
            "Units & Occupancy", "Assets & Compliance", "Parking & Access");

    private final UserRepository users;
    private final BuildingRepository buildings;
    private final BuildingSettingRepository settings;
    private final UnitRepository units;
    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper json;
    private final ModuleSeeder moduleSeeder;

    public DemoSeeder(UserRepository users, BuildingRepository buildings, BuildingSettingRepository settings,
                      UnitRepository units, ResidentRepository residents, StaffRepository staff,
                      PasswordEncoder passwordEncoder, ObjectMapper json, ModuleSeeder moduleSeeder) {
        this.users = users;
        this.buildings = buildings;
        this.settings = settings;
        this.units = units;
        this.residents = residents;
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.json = json;
        this.moduleSeeder = moduleSeeder;
    }

    @Transactional
    public String seed() {
        List<String> report = new ArrayList<>();

        // ---------------------------------------------------------------- accounts (spec §4.4)
        User admin1 = user("admin1@nibash.bd", "Imran Chowdhury", Roles.ADMIN, "+8801711000001", true);
        User admin2 = user("admin2@nibash.bd", "Nusrat Jahan", Roles.ADMIN, "+8801711000002", true);
        User committee1 = user("committee1@nibash.bd", "Farhana Haque", Roles.COMMITTEE, "+8801711000003", false);
        User committee2 = user("committee2@nibash.bd", "Tanvir Alam", Roles.COMMITTEE, "+8801711000004", false);
        User resident1 = user("resident1@nibash.bd", "Ayesha Rahman", Roles.RESIDENT, "+8801711000005", false);
        User resident2 = user("resident2@nibash.bd", "Rafiq Hasan", Roles.RESIDENT, "+8801711000006", false);
        User guard1 = user("guard1@nibash.bd", "Jamal Uddin", Roles.GUARD, "+8801711000007", false);
        User guard2 = user("guard2@nibash.bd", "Sohel Mia", Roles.GUARD, "+8801711000008", false);
        User staff1 = user("staff1@nibash.bd", "Ruma Begum", Roles.STAFF, "+8801711000009", false);
        User staff2 = user("staff2@nibash.bd", "Kamal Sheikh", Roles.STAFF, "+8801711000010", false);
        report.add("10 demo accounts");

        // ---------------------------------------------------------------- buildings (spec §14.2)
        Building gulshan = building("Gulshan Lakeview Heights", "Road 71, Gulshan 2, Dhaka 1212",
                admin1, committee1, (short) 2021, 12, 48,
                List.of("Gym", "Rooftop Lounge", "Prayer Room", "Community Hall"));
        Building banani = building("Banani Garden Square", "Road 11, Banani, Dhaka 1213",
                admin2, committee2, (short) 2019, 10, 36,
                List.of("Gym", "Community Hall", "Children's Play Area"));
        report.add("2 buildings");

        // ---------------------------------------------------------------- settings (spec §14.3)
        setting(gulshan, BuildingSetting.ENABLED_MODULES, Map.of("modules", MODULES));
        setting(banani, BuildingSetting.ENABLED_MODULES, Map.of("modules", MODULES));
        setting(gulshan, BuildingSetting.PARKING_LAYOUT, Map.of("rows", 4, "columns", 6, "prefix", "G"));
        report.add("building settings");

        // ---------------------------------------------------------------- units (spec §14.4)
        // Gulshan: floors 1-6 × A/B. A = 2BHK, B = 3BHK. Floors 1-4 occupied, 5 rented, 6 available.
        int gulshanUnits = 0;
        for (int floor = 1; floor <= 6; floor++) {
            String status = floor <= 4 ? Unit.OCCUPIED : (floor == 5 ? Unit.RENTED : Unit.AVAILABLE);
            gulshanUnits += unit(gulshan, String.format("%02dA", floor), floor, "2BHK",
                    new BigDecimal("1250.00"), new BigDecimal("18500000.00"), status) ? 1 : 0;
            gulshanUnits += unit(gulshan, String.format("%02dB", floor), floor, "3BHK",
                    new BigDecimal("1650.00"), new BigDecimal("24500000.00"), status) ? 1 : 0;
        }
        // Banani: floors 1-4 × A/B/C, all 3BHK, floors 1-3 occupied.
        int bananiUnits = 0;
        for (int floor = 1; floor <= 4; floor++) {
            for (char suffix = 'A'; suffix <= 'C'; suffix++) {
                String status = floor <= 3 ? Unit.OCCUPIED : Unit.AVAILABLE;
                bananiUnits += unit(banani, String.format("%02d%s", floor, suffix), floor, "3BHK",
                        new BigDecimal("1500.00"), new BigDecimal("21000000.00"), status) ? 1 : 0;
            }
        }
        report.add((gulshanUnits + bananiUnits) + " units created (existing left untouched)");

        // ---------------------------------------------------------------- residents (spec §14.5)
        // Committee members are residents too — that is how they get building membership.
        resident(resident1, gulshan, "01A", true);
        resident(resident2, gulshan, "01B", false);
        resident(committee1, gulshan, "02A", true);
        resident(admin1, gulshan, "02B", true);
        resident(committee2, banani, "01A", true);
        resident(admin2, banani, "01B", true);
        report.add("6 residents");

        // ---------------------------------------------------------------- staff (spec §14.8)
        staffMember(gulshan, guard1, "Jamal Uddin", "Security", "Gate Officer", "+8801711000007");
        staffMember(gulshan, staff1, "Ruma Begum", "Cleaning", "Housekeeping Supervisor", "+8801711000009");
        staffMember(gulshan, staff2, "Kamal Sheikh", "Maintenance", "Plumbing & Electrical", "+8801711000010");
        staffMember(banani, guard2, "Sohel Mia", "Security", "Gate Officer", "+8801711000008");
        report.add("4 staff");

        // Week 3 and 4 module fixtures live in their own seeder (see the note there).
        report.addAll(moduleSeeder.seed(gulshan, banani, admin1, committee1));

        String summary = "Seeded: " + String.join(", ", report) + ". Demo password: " + DEMO_PASSWORD;
        log.info(summary);
        return summary;
    }

    // ------------------------------------------------------------------ upsert helpers

    /** Upsert on email — the natural key. An existing account keeps its password. */
    private User user(String email, String name, String role, String phone, boolean backOffice) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setName(name);
            u.setRole(role);
            u.setPhone(phone);
            u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            // Residents, committee and admins appear in the directory; guards and staff do not.
            u.setListed(Roles.RESIDENT.equals(role) || Roles.COMMITTEE.equals(role) || Roles.ADMIN.equals(role));
            u.setStaff(backOffice);
            u.setSuperuser(backOffice);
            return users.save(u);
        });
    }

    /** Upsert on name — buildings have no other natural key in the schema. */
    private Building building(String name, String address, User developer, User primaryContact,
                              Short yearBuilt, Integer floors, Integer totalUnits, List<String> amenities) {
        return buildings.findAll().stream()
                .filter(b -> b.getName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    Building b = new Building();
                    b.setName(name);
                    b.setAddress(address);
                    b.setDeveloper(developer);
                    b.setPrimaryContact(primaryContact);
                    b.setYearBuilt(yearBuilt);
                    b.setNumFloors(floors);
                    b.setTotalUnits(totalUnits);
                    b.setAmenitiesJson(writeJson(amenities));
                    return buildings.save(b);
                });
    }

    private void setting(Building building, String key, Map<String, Object> value) {
        settings.findByBuildingIdAndKeyName(building.getId(), key).orElseGet(() -> {
            BuildingSetting s = new BuildingSetting();
            s.setBuilding(building);
            s.setKeyName(key);
            s.setValueJson(writeJson(value));
            return settings.save(s);
        });
    }

    /** @return true when a new unit was created. */
    private boolean unit(Building building, String number, int floor, String type,
                         BigDecimal sqft, BigDecimal price, String status) {
        if (units.findByBuildingIdAndUnitNumber(building.getId(), number).isPresent()) {
            return false;
        }
        Unit u = new Unit();
        u.setBuilding(building);
        u.setUnitNumber(number);
        u.setFloor(floor);
        u.setType(type);
        u.setSizeSqft(sqft);
        u.setPrice(price);
        u.setStatus(status);
        units.save(u);
        return true;
    }

    /** Upsert on (user, building) — the schema's unique key for membership. */
    private void resident(User user, Building building, String unitNumber, boolean owner) {
        if (residents.findByUserIdAndBuildingId(user.getId(), building.getId()).isPresent()) {
            return;
        }
        Resident r = new Resident();
        r.setUser(user);
        r.setBuilding(building);
        r.setUnit(units.findByBuildingIdAndUnitNumber(building.getId(), unitNumber).orElse(null));
        r.setOwner(owner);
        // resident2 opts out of the directory, so the privacy gating is visible in the demo.
        r.setOptIn(!"resident2@nibash.bd".equals(user.getEmail()));
        r.setStartDate(LocalDate.now().minusMonths(8));
        residents.save(r);
    }

    private void staffMember(Building building, User user, String name, String role,
                             String designation, String contact) {
        if (staff.findByBuildingIdAndName(building.getId(), name).isPresent()) {
            return;
        }
        Staff s = new Staff();
        s.setBuilding(building);
        s.setUser(user);
        s.setName(name);
        s.setRole(role);
        s.setDesignation(designation);
        s.setContactInfo(contact);
        staff.save(s);
    }

    private String writeJson(Object value) {
        return json.writeValueAsString(value);
    }
}
