package com.nibash.auth;

import com.nibash.auth.dto.AuthDtos.*;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.building.BuildingSetting;
import com.nibash.building.BuildingSettingRepository;
import com.nibash.common.ApiException;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.StaffRepository;
import com.nibash.user.Roles;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;   // Spring Boot 4 ships Jackson 3 (tools.jackson.*)

@Service
public class AuthService {

    /** Modules a new workspace gets when the signup form doesn't specify any. */
    private static final List<String> DEFAULT_MODULES = List.of(
            "Finance", "Visitors", "Maintenance", "Messaging", "Documents",
            "Units & Occupancy", "Assets & Compliance", "Parking & Access");

    private final UserRepository users;
    private final BuildingRepository buildings;
    private final BuildingSettingRepository settings;
    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final TokenService tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository users, BuildingRepository buildings, BuildingSettingRepository settings,
                       ResidentRepository residents, StaffRepository staff, TokenService tokens,
                       PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.users = users;
        this.buildings = buildings;
        this.settings = settings;
        this.residents = residents;
        this.staff = staff;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.badRequest("Invalid email or password."));

        return new AuthResponse(
                tokens.getOrCreate(user),
                UserDto.from(user),
                BuildingDto.from(homeBuilding(user).orElse(null)));
    }

    /**
     * Self-serve workspace creation (spec §4.2): user + building + enabled_modules setting + token,
     * all in one transaction.
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.badRequest("An account with this email already exists.");
        }
        passwordPolicy.validate(request.password(), request.email(), request.name());

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(request.email().trim());
        user.setPhone("");
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Roles.ADMIN);
        user = users.save(user);

        Building building = new Building();
        String buildingName = request.buildingName() == null || request.buildingName().isBlank()
                ? "Nibash Demo Tower"
                : request.buildingName().trim();
        building.setName(buildingName);
        building.setAddress("Dhaka, Bangladesh");
        building.setDeveloper(user);
        building.setPrimaryContact(user);
        building.setTotalUnits(0);
        building = buildings.save(building);

        BuildingSetting modules = new BuildingSetting();
        modules.setBuilding(building);
        modules.setKeyName(BuildingSetting.ENABLED_MODULES);
        modules.setValueJson(writeJson(Map.of("modules",
                request.modules() == null || request.modules().isEmpty() ? DEFAULT_MODULES : request.modules())));
        settings.save(modules);

        return new AuthResponse(tokens.getOrCreate(user), UserDto.from(user), BuildingDto.from(building));
    }

    @Transactional(readOnly = true)
    public SessionResponse me(User user) {
        return new SessionResponse(UserDto.from(user), BuildingDto.from(homeBuilding(user).orElse(null)));
    }

    @Transactional
    public void logout(User user) {
        tokens.revokeAll(user.getId());
    }

    /**
     * "First building for user" (spec §4.2): resident row → staff row → developer/primary contact →
     * first building in the system (back-office convenience) → none.
     */
    @Transactional(readOnly = true)
    public Optional<Building> homeBuilding(User user) {
        Optional<Building> fromResident = residents.findFirstByUserIdOrderByIdAsc(user.getId())
                .map(r -> r.getBuilding());
        if (fromResident.isPresent()) {
            return fromResident;
        }
        Optional<Building> fromStaff = staff.findFirstByUserIdOrderByIdAsc(user.getId())
                .map(s -> s.getBuilding());
        if (fromStaff.isPresent()) {
            return fromStaff;
        }
        List<Building> owned = buildings.findOwnedBy(user.getId());
        if (!owned.isEmpty()) {
            return Optional.of(owned.getFirst());
        }
        return buildings.findFirstByOrderByIdAsc();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize building setting", e);
        }
    }
}
