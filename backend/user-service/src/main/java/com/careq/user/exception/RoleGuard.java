package com.careq.user.exception;

import java.util.Arrays;

/**
 * Centralizes the admin role checks that were previously inlined in the
 * controller (code review finding). A mismatch throws
 * {@link UnauthorizedException} (403), handled globally.
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    public static void requireRole(String actualRole, String expectedRole) {
        boolean allowed = actualRole != null && expectedRole.equalsIgnoreCase(actualRole);
        if (!allowed) {
            throw new UnauthorizedException(
                    "You need role " + expectedRole + " to perform this action");
        }
    }
}
