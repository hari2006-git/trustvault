
package com.trustvault.trustvault.controller;

import com.trustvault.trustvault.service.AppUserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private final AppUserService appUserService;

    public RegistrationController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    public String registrationForm(CsrfToken csrfToken) {
        String page = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1">
                    <meta name="theme-color" content="#3b176d">
                    <title>Create Account | TrustVault</title>
                    <link rel="stylesheet" href="/css/auth.css">
                </head>
                <body>
                <div class="auth-page">

                    <section class="auth-story">
                        <a class="auth-brand" href="/">
                            <span class="auth-mark">T</span>
                            <span>TrustVault</span>
                        </a>

                        <div class="auth-story-content">
                            <div class="auth-tag">
                                <span class="auth-tag-dot"></span>
                                A BETTER DOCUMENT WORKSPACE
                            </div>

                            <h2 class="auth-story-title">
                                Make room for<br>
                                <span>peace of mind.</span>
                            </h2>

                            <p class="auth-story-copy">
                                Create your TrustVault account to organize
                                your important documents in one dedicated
                                workspace.
                            </p>

                            <div class="auth-feature-list">
                                <div class="auth-feature">
                                    <span class="auth-feature-icon">◇</span>
                                    <span>Private document storage</span>
                                </div>
                                <div class="auth-feature">
                                    <span class="auth-feature-icon">⌁</span>
                                    <span>Account-based authentication</span>
                                </div>
                                <div class="auth-feature">
                                    <span class="auth-feature-icon">◷</span>
                                    <span>Document permissions and audit features in development</span>
                                </div>
                            </div>
                        </div>

                        <div class="auth-story-footer">
                            TrustVault · Secure document workspace
                        </div>
                    </section>

                    <main class="auth-card-wrap">
                        <section class="auth-card">
                            <a class="auth-card-brand" href="/">
                                <span class="auth-mark">T</span>
                                <span>TrustVault</span>
                            </a>

                            <p class="auth-eyebrow">GET STARTED</p>

                            <h1>
                                Create your<br>
                                <span class="auth-title-highlight">TrustVault account.</span>
                            </h1>

                            <p class="auth-subtitle">
                                Enter your details to create your personal
                                document workspace.
                            </p>

                            <form class="auth-form"
                                  method="post"
                                  action="/register">

                                <input
                                    type="hidden"
                                    name="__CSRF_PARAMETER__"
                                    value="__CSRF_TOKEN__">

                                <div class="auth-field">
                                    <label for="displayName">Display name</label>
                                    <input
                                        class="auth-input"
                                        id="displayName"
                                        name="displayName"
                                        type="text"
                                        maxlength="100"
                                        placeholder="Your name"
                                        autocomplete="name"
                                        required>
                                </div>

                                <div class="auth-field">
                                    <label for="email">Email address</label>
                                    <input
                                        class="auth-input"
                                        id="email"
                                        name="email"
                                        type="email"
                                        maxlength="320"
                                        placeholder="you@company.com"
                                        autocomplete="email"
                                        required>
                                </div>

                                <div class="auth-field">
                                    <label for="password">Create password</label>
                                    <input
                                        class="auth-input"
                                        id="password"
                                        name="password"
                                        type="password"
                                        minlength="12"
                                        maxlength="72"
                                        autocomplete="new-password"
                                        placeholder="At least 12 characters"
                                        aria-describedby="passwordHint"
                                        required>
                                    <p class="auth-form-note" id="passwordHint">
                                        Use at least 12 characters. Never reuse
                                        a password from another account.
                                    </p>
                                </div>

                                <button class="auth-primary-button"
                                        type="submit">
                                    Create my account <span aria-hidden="true">→</span>
                                </button>
                            </form>

                            <div class="auth-divider"></div>

                            <p class="auth-footer">
                                Already have an account?
                                <a class="auth-link" href="/login">Sign in</a>
                            </p>

                            <div class="auth-security-note">
                                <span class="auth-security-symbol">◇</span>
                                <span>
                                    Passwords are hashed by the server before
                                    storage. Email verification and additional
                                    account protections remain to be implemented.
                                </span>
                            </div>
                        </section>
                    </main>
                </div>
                </body>
                </html>
                """;

        return page
                .replace(
                        "__CSRF_PARAMETER__",
                        escapeHtml(csrfToken.getParameterName()))
                .replace(
                        "__CSRF_TOKEN__",
                        escapeHtml(csrfToken.getToken()));
    }

    @PostMapping(
            value = "/register",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> register(
            @RequestParam String displayName,
            @RequestParam String email,
            @RequestParam String password) {

        try {
            appUserService.register(email, password, displayName);

            return resultPage(
                    HttpStatus.CREATED,
                    "Account created",
                    "Your TrustVault account has been created. You can now sign in.",
                    "Sign in",
                    "/login",
                    true);

        } catch (IllegalArgumentException exception) {
            // Do not render raw exception text or user input as HTML.
            return resultPage(
                    HttpStatus.BAD_REQUEST,
                    "Unable to create account",
                    "We could not create your account. Check your details and try again.",
                    "Return to registration",
                    "/register",
                    false);
        }
    }

    private ResponseEntity<String> resultPage(
            HttpStatus status,
            String title,
            String message,
            String linkText,
            String linkUrl,
            boolean success) {

        String messageClass = success
                ? "auth-message auth-message-success"
                : "auth-message auth-message-error";

        String page = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1">
                    <meta name="theme-color" content="#3b176d">
                    <title>__TITLE__ | TrustVault</title>
                    <link rel="stylesheet" href="/css/auth.css">
                </head>
                <body>
                <main class="auth-page auth-page-centered">
                    <section class="auth-card auth-result-card">
                        <a class="auth-card-brand" href="/">
                            <span class="auth-mark">T</span>
                            <span>TrustVault</span>
                        </a>

                        <p class="auth-eyebrow">TRUSTVAULT ACCOUNT</p>
                        <h1>__TITLE__</h1>

                        <p class="auth-subtitle">__MESSAGE__</p>

                        <a class="auth-primary-button"
                           href="__LINK_URL__">__LINK_TEXT__ →</a>
                    </section>
                </main>
                </body>
                </html>
                """;

        page = page
                .replace("__TITLE__", escapeHtml(title))
                .replace("__MESSAGE__", escapeHtml(message))
                .replace("__LINK_TEXT__", escapeHtml(linkText))
                .replace("__LINK_URL__", linkUrl);

        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(page);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
