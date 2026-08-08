package com.nibash.user;

import java.util.Set;

/** The five business roles (spec §1). Stored as lowercase strings, matching the API contract. */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String COMMITTEE = "committee";
    public static final String RESIDENT = "resident";
    public static final String GUARD = "guard";
    public static final String STAFF = "staff";

    public static final Set<String> ALL = Set.of(ADMIN, COMMITTEE, RESIDENT, GUARD, STAFF);

    private Roles() {
    }

    public static boolean isValid(String role) {
        return role != null && ALL.contains(role);
    }
}
