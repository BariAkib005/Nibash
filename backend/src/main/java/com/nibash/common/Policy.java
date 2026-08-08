package com.nibash.common;

import com.nibash.auth.CurrentUser;
import com.nibash.user.User;

/**
 * The endpoint policies from spec §5, as explicit guards rather than annotations — the rules are
 * short and reading them at the call site beats chasing an annotation's meaning.
 *
 * <ul>
 *   <li><b>IsAuthenticated</b> — any valid token (enforced by the security chain).</li>
 *   <li><b>CommitteeOrAdmin</b> — reads open to any authenticated user; writes need back-office or
 *       role admin/committee.</li>
 *   <li><b>CommitteeOrAdminStrict</b> — every method needs back-office or admin/committee.</li>
 * </ul>
 */
public final class Policy {

    private Policy() {
    }

    /** Guards a write on a CommitteeOrAdmin resource. */
    public static void requireManager() {
        requireManager(CurrentUser.require());
    }

    public static void requireManager(User caller) {
        if (!caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("You do not have permission to perform this action.");
        }
    }

    /** Guards every method on a strict resource (e.g. user administration). */
    public static void requireStrict() {
        requireManager();
    }

    /** Guards a back-office-only action, e.g. running the seeder (spec §8.25). */
    public static void requireBackOffice() {
        if (!CurrentUser.require().isBackOffice()) {
            throw ApiException.forbidden("Admin access required.");
        }
    }
}
