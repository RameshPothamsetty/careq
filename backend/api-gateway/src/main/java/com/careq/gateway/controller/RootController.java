package com.careq.gateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Friendly landing page for the bare gateway URL.
 *
 * The gateway is an API, not a website: opening the root used to fall through
 * to Spring Boot's default Whitelabel 404 page, which looks like an error.
 * This handler returns a small page pointing at the useful endpoints instead
 * (live-run polish).
 */
@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> root() {
        return Mono.just("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>CareQ API Gateway</title>
                  <style>
                    body { font-family: system-ui, -apple-system, sans-serif; max-width: 640px; margin: 48px auto; padding: 0 16px; color: #1f2937; line-height: 1.6; }
                    h1 { color: #0f766e; }
                    .card { border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px 20px; margin: 16px 0; }
                    a { color: #0f766e; }
                    code { background: #f3f4f6; padding: 2px 6px; border-radius: 4px; }
                    ul { margin: 8px 0; }
                  </style>
                </head>
                <body>
                  <h1>CareQ API Gateway</h1>
                  <p>Backend API for <strong>CareQ — SmartOPD AI</strong>. This is an API gateway — there is no web UI here; the web app lives on Vercel.</p>
                  <div class="card">
                    <h3>Useful links</h3>
                    <ul>
                      <li><a href="/swagger-ui.html">Swagger API documentation</a></li>
                      <li><a href="/api/auth/health">Health check</a></li>
                    </ul>
                  </div>
                  <div class="card">
                    <h3>Web app</h3>
                    <p><a href="https://careq-frontend-eta.vercel.app">https://careq-frontend-eta.vercel.app</a></p>
                    <p><small>API base: <code>/api/...</code> — auth, users, doctors, queue, notifications</small></p>
                  </div>
                </body>
                </html>
                """);
    }
}
