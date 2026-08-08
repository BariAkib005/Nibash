package com.nibash.auth.dto;

import com.nibash.building.Building;
import com.nibash.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request/response shapes for spec §4.2. Jackson is configured for snake_case, so
 * {@code avatarPath} serializes as {@code avatar_path}.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") String email,
            @NotBlank(message = "Password is required.") String password) {
    }

    public record SignupRequest(
            @NotBlank(message = "Name is required.") String name,
            @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") String email,
            @NotBlank(message = "Password is required.") String password,
            String buildingName,
            List<String> modules) {
    }

    /** The caller's profile. Password hash, DOB and national ID are never included (spec §4.1). */
    public record UserDto(Long id, String name, String email, String phone, String role, String avatarPath) {

        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getAvatarPath());
        }
    }

    public record BuildingDto(
            Long id, String name, String address, Long developer, Long primaryContact,
            Short yearBuilt, Integer numFloors, Integer totalUnits, String website,
            String amenitiesJson, String photoPath, LocalDateTime createdAt) {

        public static BuildingDto from(Building b) {
            if (b == null) {
                return null;
            }
            return new BuildingDto(
                    b.getId(), b.getName(), b.getAddress(),
                    b.getDeveloper() == null ? null : b.getDeveloper().getId(),
                    b.getPrimaryContact() == null ? null : b.getPrimaryContact().getId(),
                    b.getYearBuilt(), b.getNumFloors(), b.getTotalUnits(), b.getWebsite(),
                    b.getAmenitiesJson(), b.getPhotoPath(), b.getCreatedAt());
        }
    }

    /** Login and signup return the same shape; /me returns it without the token. */
    public record AuthResponse(String token, UserDto user, BuildingDto building) {
    }

    public record SessionResponse(UserDto user, BuildingDto building) {
    }
}
