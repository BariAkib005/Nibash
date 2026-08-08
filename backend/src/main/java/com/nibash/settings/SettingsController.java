package com.nibash.settings;

import com.nibash.auth.AuthService;
import com.nibash.auth.CurrentUser;
import com.nibash.auth.TokenService;
import com.nibash.building.Building;
import com.nibash.building.BuildingController;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Dtos.BuildingDto;
import com.nibash.common.Dtos.UserDto;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/settings/} (spec §4.3) — three sections selected by the {@code section} field:
 * {@code user}, {@code password} (rotates the token) and {@code building} (admin/committee only).
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final UserRepository users;
    private final BuildingRepository buildings;
    private final TenantService tenancy;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public SettingsController(UserRepository users, BuildingRepository buildings, TenantService tenancy,
                              TokenService tokens, PasswordEncoder passwordEncoder, AuthService authService) {
        this.users = users;
        this.buildings = buildings;
        this.tenancy = tenancy;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@RequestParam(name = "building_id", required = false) Long buildingId) {
        User caller = CurrentUser.require();
        Building building = resolveBuilding(caller, buildingId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", UserDto.from(caller));
        response.put("building", BuildingDto.from(building));
        return response;
    }

    @PatchMapping("/")
    @Transactional
    public Map<String, Object> patch(@RequestBody Map<String, Object> body) {
        String section = body.get("section") == null ? "" : String.valueOf(body.get("section"));
        return switch (section) {
            case "user" -> updateProfile(body);
            case "password" -> changePassword(body);
            case "building" -> updateBuilding(body);
            default -> throw ApiException.badRequest("Unknown section.");
        };
    }

    private Map<String, Object> updateProfile(Map<String, Object> body) {
        User caller = CurrentUser.require();
        User user = users.findById(caller.getId()).orElseThrow(() -> ApiException.notFound("Not found."));

        if (body.containsKey("name")) {
            user.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("phone")) {
            user.setPhone(String.valueOf(body.get("phone")));
        }
        users.save(user);

        return Map.of("detail", "Profile updated.", "user", UserDto.from(user));
    }

    /** Changing the password revokes existing tokens and issues a fresh one (spec §4.3). */
    private Map<String, Object> changePassword(Map<String, Object> body) {
        String current = str(body.get("current_password"));
        String next = str(body.get("new_password"));
        if (current == null || current.isBlank() || next == null || next.isBlank()) {
            throw ApiException.badRequest("current_password and new_password are required.");
        }

        User caller = CurrentUser.require();
        User user = users.findById(caller.getId()).orElseThrow(() -> ApiException.notFound("Not found."));

        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(next));
        users.save(user);

        return Map.of("detail", "Password changed.", "token", tokens.rotate(user));
    }

    private Map<String, Object> updateBuilding(Map<String, Object> body) {
        User caller = CurrentUser.require();
        Long buildingId = body.get("building_id") == null
                ? null
                : Long.valueOf(String.valueOf(body.get("building_id")));
        if (buildingId == null) {
            throw ApiException.badRequest("building_id is required.");
        }
        // Outside the allowed set → 404 before any permission message leaks the building's existence.
        tenancy.requireAccess(caller, buildingId);

        if (!caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("Admin access required.");
        }

        Building building = buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        BuildingController.applyUpdates(building, body);
        buildings.save(building);

        return Map.of("detail", "Building updated.", "building", BuildingDto.from(building));
    }

    /** Honours {@code ?building_id=} within the allowed set, else falls back to the home building. */
    private Building resolveBuilding(User caller, Long buildingId) {
        if (buildingId != null && tenancy.canAccess(caller, buildingId)) {
            return buildings.findById(buildingId).orElse(null);
        }
        return authService.homeBuilding(caller).orElse(null);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
