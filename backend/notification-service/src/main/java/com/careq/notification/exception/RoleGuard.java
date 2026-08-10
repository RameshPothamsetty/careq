package com.careq.notification.exception;

import java.util.Arrays;

/**
 * Centralizes role checks based on the X-User-Role header propagated by the
 * API Gateway. A mismatch throws {@link UnauthorizedException} (403), handled
 * globally — same convention as the other services.
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

    public static void requireAnyRole(String actualRole, String... expectedRoles) {
        boolean allowed = actualRole != null
                && Arrays.stream(expectedRoles).anyMatch(role -> role.equalsIgnoreCase(actualRole));
        if (!allowed) {
            throw new UnauthorizedException(
                    "You need one of the roles " + Arrays.toString(expectedRoles) + " to perform this action");
        }
    }
}
