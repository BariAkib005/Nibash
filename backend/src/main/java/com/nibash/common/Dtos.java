package com.nibash.common;

import com.nibash.building.Building;
import com.nibash.resident.Resident;
import com.nibash.staffing.Staff;
import com.nibash.unit.Unit;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read shapes for the core registry. Serialization rule (spec §8): all columns flat, FKs carried as
 * the raw ID under the FK's field name. Jackson renders these snake_case.
 *
 * <p>{@code password_hash}, {@code dob} and {@code national_id} are absent by construction —
 * they are never on a DTO, so they can never leak.
 */
public final class Dtos {

    private Dtos() {
    }

    public record BuildingDto(
            Long id, String name, String address, Long developer, Long primaryContact,
            Short yearBuilt, Integer numFloors, Integer totalUnits, String website,
            String amenitiesJson, String photoPath, LocalDateTime createdAt) {

        public static BuildingDto from(Building b) {
            if (b == null) {
                return null;
            }
            return new BuildingDto(b.getId(), b.getName(), b.getAddress(),
                    b.getDeveloper() == null ? null : b.getDeveloper().getId(),
                    b.getPrimaryContact() == null ? null : b.getPrimaryContact().getId(),
                    b.getYearBuilt(), b.getNumFloors(), b.getTotalUnits(), b.getWebsite(),
                    b.getAmenitiesJson(), b.getPhotoPath(), b.getCreatedAt());
        }
    }

    public record UnitDto(
            Long id, Long building, String unitNumber, Integer floor, String type,
            BigDecimal sizeSqft, BigDecimal price, String status, LocalDateTime createdAt) {

        public static UnitDto from(Unit u) {
            return new UnitDto(u.getId(), u.getBuilding().getId(), u.getUnitNumber(), u.getFloor(),
                    u.getType(), u.getSizeSqft(), u.getPrice(), u.getStatus(), u.getCreatedAt());
        }
    }

    /** Residents carry a few denormalised extras the frontend would otherwise have to fetch. */
    public record ResidentDto(
            Long id, Long user, Long building, Long unit, boolean isOwner, boolean optIn,
            LocalDate startDate, LocalDate endDate, LocalDateTime createdAt,
            String residentName, String residentEmail, String unitNumber) {

        public static ResidentDto from(Resident r) {
            return new ResidentDto(r.getId(), r.getUser().getId(), r.getBuilding().getId(),
                    r.getUnit() == null ? null : r.getUnit().getId(),
                    r.isOwner(), r.isOptIn(), r.getStartDate(), r.getEndDate(), r.getCreatedAt(),
                    r.getUser().getName(), r.getUser().getEmail(),
                    r.getUnit() == null ? null : r.getUnit().getUnitNumber());
        }
    }

    public record UserDto(
            Long id, String name, String email, String phone, String role, boolean isListed,
            String avatarPath, String address, String bio, String emergencyContactPhone,
            LocalDateTime createdAt) {

        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                    u.isListed(), u.getAvatarPath(), u.getAddress(), u.getBio(),
                    u.getEmergencyContactPhone(), u.getCreatedAt());
        }
    }

    public record StaffDto(
            Long id, Long user, String name, String role, String designation,
            String qualifications, Long building, String contactInfo) {

        public static StaffDto from(Staff s) {
            return new StaffDto(s.getId(), s.getUser() == null ? null : s.getUser().getId(),
                    s.getName(), s.getRole(), s.getDesignation(), s.getQualifications(),
                    s.getBuilding().getId(), s.getContactInfo());
        }
    }

    /**
     * A directory row (spec §8.1). {@code email} and {@code phone} are {@code null} unless the
     * resident opted in — privacy is enforced here, not in the client.
     */
    public record DirectoryEntry(
            Long id, String name, Long unit, String unitNumber, Long building,
            String email, String phone, String avatarPath, boolean isOwner, boolean optIn) {

        public static DirectoryEntry from(Resident r) {
            User u = r.getUser();
            boolean share = r.isOptIn();
            return new DirectoryEntry(
                    r.getId(), u.getName(),
                    r.getUnit() == null ? null : r.getUnit().getId(),
                    r.getUnit() == null ? null : r.getUnit().getUnitNumber(),
                    r.getBuilding().getId(),
                    share ? u.getEmail() : null,
                    share ? u.getPhone() : null,
                    u.getAvatarPath(), r.isOwner(), r.isOptIn());
        }
    }

    public record DirectoryResponse(int count, java.util.List<DirectoryEntry> results) {
    }
}
