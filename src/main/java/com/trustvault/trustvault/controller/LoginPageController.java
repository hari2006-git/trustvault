
package com.trustvault.trustvault.controller;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginPageController {

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage(
            CsrfToken csrfToken,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout) {

        String message = "";

        if (error != null) {
            message = """
                    <div class="auth-message auth-message-error"
                         role="alert">
                        Sign-in failed. Check your email and password,
                        then try again.
                    </div>
                    """;
        } else if (logout != null) {
            message = """
                    <div class="auth-message auth-message-success"
                         role="status">
                        You have signed out successfully.
                    </div>
                    """;
        }

        String page = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1">
                    <meta name="theme-color" content="#3b176d">
                    <title>Sign In | TrustVault</title>
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
                                YOUR PRIVATE WORKSPACE
                            </div>

                            <h2 class="auth-story-title">
                                Your documents.<br>
                                Your control.<br>
                                <span>Your TrustVault.</span>
                            </h2>

                            <p class="auth-story-copy">
                                A dedicated workspace for your important
                                documents, with server-enforced permissions
                                and recorded document activity.
                            </p>

                            <div class="auth-feature-list">
                                <div class="auth-feature">
                                    <span class="auth-feature-icon">◇</span>
                                    <span>Private document storage</span>
                                </div>
                                <div class="auth-feature">
                                    <span class="auth-feature-icon">⌁</span>
                                    <span>Access controlled by your account</span>
                                </div>
                                <div class="auth-feature">
                                    <span class="auth-feature-icon">◷</span>
                                    <span>Document activity auditing in development</span>
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

                            <p class="auth-eyebrow">WELCOME BACK</p>

                            <h1>
                                Sign in to<br>
                                <span class="auth-title-highlight">your workspace.</span>
                            </h1>

                            <p class="auth-subtitle">
                                Enter your account credentials to continue
                                to your document workspace.
                            </p>

                            __AUTH_MESSAGE__

                            <form class="auth-form"
                                  method="post"
                                  action="/login">

                                <input type="hidden"
                                       name="__CSRF_PARAMETER__"
                                       value="__CSRF_TOKEN__">

                                <div class="auth-field">
                                    <label for="username">Email address</label>
                                    <input
                                        class="auth-input"
                                        id="username"
                                        name="username"
                                        type="email"
                                        maxlength="320"
                                        placeholder="you@company.com"
                                        autocomplete="username"
                                        required
                                        autofocus>
                                </div>

                                <div class="auth-field">
                                    <label for="password">Password</label>
                                    <input
                                        class="auth-input"
                                        id="password"
                                        name="password"
                                        type="password"
                                        placeholder="Enter your password"
                                        autocomplete="current-password"
                                        required>
                                </div>

                                <button class="auth-primary-button"
                                        type="submit">
                                    Sign in securely <span aria-hidden="true">→</span>
                                </button>
                            </form>

                            <section class="auth-role-section" aria-labelledby="auth-role-heading">
                                <h2 id="auth-role-heading">Choose your workspace</h2>
                                <p class="auth-role-intro">Your assigned account role determines your permissions.</p>
                                <div class="auth-role-grid">
                                    <article class="auth-role-card auth-role-admin">
                                        <svg class="auth-role-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="10" cy="7" r="4"/><path d="M20 8v6m-3-3h6"/></svg>
                                        <h3>Admin</h3>
                                        <p>Manage users and administrative features permitted to your account.</p>
                                    </article>
                                    <article class="auth-role-card auth-role-owner">
                                        <svg class="auth-role-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z"/><path d="m9 12 2 2 4-4"/></svg>
                                        <h3>Owner</h3>
                                        <p>Manage your documents and grant or revoke access.</p>
                                    </article>
                                    <article class="auth-role-card auth-role-viewer">
                                        <svg class="auth-role-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>
                                        <h3>Viewer</h3>
                                        <p>View and download documents you are authorized to access.</p>
                                    </article>
                                </div>
                            </section>


                            <div class="auth-divider"></div>

                            <p class="auth-footer">
                                New to TrustVault?
                                <a class="auth-link" href="/register">
                                    Create an account
                                </a>
                            </p>

                            <div class="auth-security-note">
                                <span class="auth-security-symbol">◇</span>
                                <span>
                                    Your account is authenticated by the
                                    TrustVault server. Never share your
                                    password with anyone.
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
                        escapeHtml(csrfToken.getToken()))
                .replace("__AUTH_MESSAGE__", message);
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
