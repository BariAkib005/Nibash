package com.nibash.membership;

import com.nibash.activity.ActivityLogService;
import com.nibash.auth.CurrentUser;
import com.nibash.auth.PasswordPolicy;
import com.nibash.auth.TokenService;
import com.nibash.auth.dto.AuthDtos.AuthResponse;
import com.nibash.auth.dto.AuthDtos.BuildingDto;
import com.nibash.auth.dto.AuthDtos.UserDto;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.common.Times;
import com.nibash.jobs.NotificationService;
import com.nibash.tenancy.TenantService;
import com.nibash.unit.Unit;
import com.nibash.unit.UnitRepository;
import com.nibash.user.Roles;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/invitations/} — how people other than the owner get into a building.
 *
 * <p>A manager (CommitteeOrAdmin) invites someone by email as a resident, committee member, guard or
 * staff member. The response carries a one-time link, {@code /join#<token>}, to share however suits —
 * it is emailed too when SMTP is configured. The token sits in the URL fragment so browsers never send
 * it to a server or put it in a Referer; only its SHA-256 is stored, and it expires after seven days.
 *
 * <p>{@code preview} and {@code accept} are public: the invitee has no account yet. Accepting creates
 * the login (or, for an email that already has one, asks for that account's password) and attaches
 * it to the building, then signs the person in.
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    static final int VALID_DAYS = 7;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final InvitationRepository invitations;
    private final BuildingRepository buildings;
    private final UnitRepository units;
    private final UserRepository users;
    private final MembershipService membership;
    private final TenantService tenancy;
    private final TokenService tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final ActivityLogService activity;
    private final String appUrl;

    public InvitationController(InvitationRepository invitations, BuildingRepository buildings, UnitRepository units,
                                UserRepository users, MembershipService membership, TenantService tenancy,
                                TokenService tokens, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
                                NotificationService notifications, ActivityLogService activity,
                                @Value("${nibash.app-url:http://127.0.0.1:5173}") String appUrl) {
        this.invitations = invitations;
        this.buildings = buildings;
        this.units = units;
        this.users = users;
        this.membership = membership;
        this.tenancy = tenancy;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.activity = activity;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    public record InvitationDto(Long id, Long building, String email, String name, String phone, String role,
                                Long unit, String unitNumber, boolean isOwner, String staffRole, String designation,
                                String status, LocalDateTime createdAt, LocalDateTime expiresAt,
                                LocalDateTime acceptedAt, String invitedByName) {

        public static InvitationDto from(Invitation i) {
            return new InvitationDto(i.getId(), i.getBuilding().getId(), i.getEmail(), i.getName(), i.getPhone(),
                    i.getRole(), i.getUnit() == null ? null : i.getUnit().getId(),
                    i.getUnit() == null ? null : i.getUnit().getUnitNumber(), i.isOwner(), i.getStaffRole(),
                    i.getDesignation(), i.status(Times.now()), i.getCreatedAt(), i.getExpiresAt(),
                    i.getAcceptedAt(), i.getInvitedBy() == null ? null : i.getInvitedBy().getName());
        }
    }

    /** Create and renew are the only times a link exists in the clear; it is never shown again. */
    public record IssuedDto(InvitationDto invitation, String invitePath, boolean emailed) {
    }

    public record PreviewDto(String buildingName, String buildingAddress, String name, String email, String role,
                             String unitNumber, String staffRole, LocalDateTime expiresAt, boolean accountExists) {
    }

    // ------------------------------------------------------------------ managers

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<InvitationDto> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(name = "building_id", required = false) Long buildingId,
                                            @RequestParam(required = false) String roles) {
        Policy.requireManager();
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        List<String> wanted = parseRoles(roles);
        if (scope.isEmpty() || wanted.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(invitations.findOpen(scope, wanted, pageable), InvitationDto::from);
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<IssuedDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);
        Building building = buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found."));

        String role = Body.requireStr(body, "role").trim().toLowerCase(Locale.ROOT);
        Body.requireOneOf(role, Invitation.ROLES, "role");
        String name = limit(Body.requireStr(body, "name"), 100, "name");
        String email = Body.requireStr(body, "email");
        if (!EMAIL.matcher(email).matches() || email.length() > 150) {
            throw ApiException.badRequest("Enter a valid email address.");
        }

        Invitation invitation = new Invitation();
        invitation.setBuilding(building);
        invitation.setEmail(email);
        invitation.setName(name);
        invitation.setPhone(blankToNull(limit(Body.str(body, "phone"), 20, "phone")));
        invitation.setRole(role);
        invitation.setInvitedBy(caller);
        if (invitation.joinsAsResident()) {
            invitation.setUnit(unitIn(building, Body.asLong(body, "unit")));
            invitation.setOwner(Body.asBool(body, "is_owner"));
        } else {
            String staffRole = blankToNull(limit(Body.str(body, "staff_role"), 50, "staff_role"));
            if (staffRole == null && Roles.GUARD.equals(role)) {
                staffRole = "Security";
            }
            if (staffRole == null) {
                throw ApiException.badRequest("Choose what they'll do — for example Cleaning or Maintenance.");
            }
            invitation.setStaffRole(staffRole);
            invitation.setDesignation(blankToNull(limit(Body.str(body, "designation"), 100, "designation")));
        }

        users.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (membership.belongsTo(existing, buildingId)) {
                throw ApiException.badRequest(email + " is already part of " + building.getName() + ".");
            }
            String conflict = membership.roleConflict(existing, role);
            if (conflict != null) {
                throw ApiException.badRequest(conflict);
            }
        });
        if (invitations.existsOpenFor(buildingId, email, Times.now())) {
            throw ApiException.badRequest(email + " already has a pending invitation here. "
                    + "Use “New link” on it to send a fresh one.");
        }

        IssuedDto issued = issue(invitation, caller);
        activity.record(caller, "invitation", issued.invitation().id(), "created",
                Map.of("email", email, "role", role, "building", building.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(issued);
    }

    /** A fresh link and a fresh seven days; the previous link stops working. */
    @PostMapping("/{id}/renew/")
    @Transactional
    public IssuedDto renew(@PathVariable Long id) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Invitation invitation = scoped(id);
        if (invitation.getAcceptedAt() != null) {
            throw ApiException.badRequest("This invitation has already been accepted.");
        }
        return issue(invitation, caller);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        Policy.requireManager();
        Invitation invitation = scoped(id);
        if (invitation.getAcceptedAt() != null) {
            throw ApiException.badRequest("This invitation has already been accepted, so it stays on file.");
        }
        invitations.delete(invitation);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ------------------------------------------------------------------ the invitee (public)

    @PostMapping("/preview/")
    @Transactional(readOnly = true)
    public PreviewDto preview(@RequestBody Map<String, Object> body) {
        Invitation invitation = invitations.findByTokenHash(hash(Body.requireStr(body, "token").trim()))
                .orElseThrow(InvitationController::unknownLink);
        requireUsable(invitation);
        return new PreviewDto(invitation.getBuilding().getName(), invitation.getBuilding().getAddress(),
                invitation.getName(), invitation.getEmail(), invitation.getRole(),
                invitation.getUnit() == null ? null : invitation.getUnit().getUnitNumber(),
                invitation.getStaffRole(), invitation.getExpiresAt(),
                users.existsByEmailIgnoreCase(invitation.getEmail()));
    }

    @PostMapping("/accept/")
    @Transactional
    public AuthResponse accept(@RequestBody Map<String, Object> body) {
        Invitation invitation = invitations.lockByTokenHash(hash(Body.requireStr(body, "token").trim()))
                .orElseThrow(InvitationController::unknownLink);
        requireUsable(invitation);
        String password = Body.str(body, "password"); // never trimmed: spaces are part of a password
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("password is required");
        }
        Building building = invitation.getBuilding();

        User user = users.findByEmailIgnoreCase(invitation.getEmail()).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw ApiException.badRequest("That isn't the password for " + user.getEmail()
                        + ". Use your existing Nibash password to accept.");
            }
            if (membership.belongsTo(user, building.getId())) {
                throw ApiException.badRequest("You're already part of " + building.getName() + ".");
            }
            String conflict = membership.roleConflict(user, invitation.getRole());
            if (conflict != null) {
                throw ApiException.badRequest(conflict);
            }
            membership.adoptRole(user, invitation.getRole());
        } else {
            String name = blankToNull(limit(Body.str(body, "name"), 100, "name"));
            name = name == null ? invitation.getName() : name.trim();
            passwordPolicy.validate(password, invitation.getEmail(), name);
            String phone = blankToNull(limit(Body.str(body, "phone"), 20, "phone"));

            user = new User();
            user.setName(name);
            user.setEmail(invitation.getEmail());
            user.setPhone(phone != null ? phone : invitation.getPhone() == null ? "" : invitation.getPhone());
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(invitation.getRole());
            user = users.save(user);
        }

        if (invitation.joinsAsResident()) {
            Unit unit = invitation.getUnit();
            membership.addResident(user, building, unit, invitation.isOwner(), LocalDate.now());
            if (unit != null && Unit.AVAILABLE.equals(unit.getStatus())) {
                unit.setStatus(Unit.OCCUPIED);
                units.save(unit);
            }
        } else {
            String contact = blankToNull(user.getPhone());
            membership.addStaff(user, building, invitation.getStaffRole(), invitation.getDesignation(),
                    contact != null ? contact : invitation.getPhone());
        }

        invitation.setAcceptedAt(Times.now());
        invitation.setAcceptedUser(user);
        invitations.save(invitation);
        activity.record(user, "invitation", invitation.getId(), "accepted",
                Map.of("role", invitation.getRole(), "building", building.getName()));

        return new AuthResponse(tokens.getOrCreate(user), UserDto.from(user), BuildingDto.from(building));
    }

    // ------------------------------------------------------------------ helpers

    private IssuedDto issue(Invitation invitation, User inviter) {
        String token = newToken();
        invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(Times.now().plusDays(VALID_DAYS));
        Invitation saved = invitations.save(invitation);

        String path = "/join#" + token;
        boolean emailed = notifications.email(saved.getEmail(),
                "You're invited to " + saved.getBuilding().getName() + " on Nibash",
                """
                Hello %s,

                %s has invited you to join %s on Nibash as %s.

                Open this link to set up your login. It works once and expires on %s:
                %s

                If you weren't expecting this, you can ignore this email.
                """.formatted(saved.getName(), inviter.getName(), saved.getBuilding().getName(),
                        roleLabel(saved), saved.getExpiresAt().format(DAY), appUrl + path));
        return new IssuedDto(InvitationDto.from(saved), path, emailed);
    }

    private static String roleLabel(Invitation invitation) {
        return switch (invitation.getRole()) {
            case Roles.COMMITTEE -> "a committee member";
            case Roles.GUARD -> "a security guard";
            case Roles.STAFF -> "staff (" + invitation.getStaffRole() + ")";
            default -> invitation.getUnit() == null
                    ? "a resident"
                    : "a resident of unit " + invitation.getUnit().getUnitNumber();
        };
    }

    private void requireUsable(Invitation invitation) {
        if (invitation.getAcceptedAt() != null) {
            throw ApiException.badRequest("This invitation has already been used. Sign in with your email and password.");
        }
        if (!invitation.getExpiresAt().isAfter(Times.now())) {
            throw ApiException.badRequest("This invitation expired on " + invitation.getExpiresAt().format(DAY)
                    + ". Ask the building for a new link.");
        }
    }

    private static ApiException unknownLink() {
        return ApiException.notFound("This invitation link isn't valid. It may have been replaced by a newer one.");
    }

    private Unit unitIn(Building building, Long unitId) {
        if (unitId == null) {
            return null;
        }
        Unit unit = units.findById(unitId).orElseThrow(() -> ApiException.notFound("Not found."));
        if (!unit.getBuilding().getId().equals(building.getId())) {
            throw ApiException.badRequest("That unit is in a different building.");
        }
        return unit;
    }

    private Invitation scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return invitations.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private static List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Invitation.ROLES;
        }
        List<String> wanted = new ArrayList<>();
        for (String role : roles.split(",")) {
            String trimmed = role.trim().toLowerCase(Locale.ROOT);
            if (Invitation.ROLES.contains(trimmed)) {
                wanted.add(trimmed);
            }
        }
        return wanted;
    }

    /** 32 random bytes as URL-safe base64: 43 characters, 256 bits. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String limit(String value, int max, String field) {
        if (value != null && value.trim().length() > max) {
            throw ApiException.badRequest(field + " must be at most " + max + " characters.");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
