package com.careq.queue.exception;

import java.util.Arrays;

/**
 * Centralizes the role checks that were previously inlined in every queue
 * controller endpoint (code review finding, Day 7a). A mismatch throws
 * {@link UnauthorizedAccessException} (403), handled globally.
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    public static void requireRole(String actualRole, String expectedRole) {
        requireAnyRole(actualRole, expectedRole);
    }

    public static void requireAnyRole(String actualRole, String... expectedRoles) {
        boolean allowed = actualRole != null && Arrays.stream(expectedRoles)
                .anyMatch(expected -> expected.equalsIgnoreCase(actualRole));
        if (!allowed) {
            throw new UnauthorizedAccessException(
                    "You need role " + String.join(" or ", expectedRoles) + " to perform this action");
        }
    }
}
