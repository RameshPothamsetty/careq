package com.careq.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Structured audit trail for security-relevant auth events .
 *
 * <p>Events are written to a dedicated {@code AUDIT} logger as single-line
 * key=value records, which the Azure container logs retain and Log
 * Analytics can query (see docs/12_MONITORING.md). No separate audit table:
 * the container logs ARE the audit store for this demo-scale deployment.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    /**
     * @param event    e.g. signup, login_success, login_failed, verify_email,
     *                 resend_verification, forgot_password, reset_password
     * @param email    the acting account (may be null for unknown emails)
     * @param ip       caller IP (from X-Forwarded-For when proxied)
     * @param detail   free-form detail (e.g. "rate_limited", "invalid_token")
     */
    public void log(String event, String email, String ip, String detail) {
        log.info("event={} email={} ip={} detail={}", event, email, ip, detail == null ? "-" : detail);
    }
}
