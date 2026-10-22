
"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const uploadForm = document.getElementById("uploadForm");
    const documentFile = document.getElementById("documentFile");
    const uploadButton = document.getElementById("uploadButton");
    const uploadMessage = document.getElementById("uploadMessage");

    const documentCount = document.getElementById("documentCount");
    const storageUsage = document.getElementById("storageUsage");
    const workspaceStatus = document.getElementById("workspaceStatus");

    const documentsList = document.getElementById("documentsList");
    const documentsEmpty = document.getElementById("documentsEmpty");
    const documentsMessage = document.getElementById("documentsMessage");
    const refreshDocuments = document.getElementById("refreshDocuments");

    // New search and filter controls.
    const searchName = document.getElementById("documentSearchName");
    const searchOwner = document.getElementById("documentSearchOwner");
    const dateFrom = document.getElementById("documentDateFrom");
    const dateTo = document.getElementById("documentDateTo");
    const sortSelect = document.getElementById("documentSort");
    const clearFilters = document.getElementById("clearDocumentFilters");
    const filterStatus = document.getElementById("documentFilterStatus");

    const MAX_FILE_BYTES = 2 * 1024 * 1024 * 1024;

    // The complete authorized document list is used for dashboard statistics.
    let allDocuments = [];
    let searchTimer = null;
    let searchRequestId = 0;
    let isLoading = false;
    let currentUser = null;
    let documentView = "all";

    // Switch between documents owned by the user and documents shared with them.
    document.querySelectorAll("[data-document-view]").forEach((link) => {
        link.addEventListener("click", () => {
            documentView = link.dataset.documentView;

            document.querySelectorAll(".nav-item").forEach((item) => {
                item.classList.remove("active");
            });
            link.classList.add("active");

            // Reuse the existing authorized list and current search filters.
            searchDocuments();
        });
    });

    async function loadCurrentUser() {
        const response = await fetch("/api/me", {
            credentials: "same-origin",
            headers: { Accept: "application/json" }
        });

        if (!response.ok) {
            throw new Error("Could not determine the current user's role.");
        }

        currentUser = await response.json();

        const displayName =
            currentUser.displayName || currentUser.email || "User";
        const role = currentUser.role || "UNKNOWN";

        const roleLabel = document.getElementById("currentUserRole");
        const nameLabel = document.getElementById("currentUserName");
        const headerName = document.getElementById("headerUserName");
        const headerRole = document.getElementById("headerUserRole");
        const headerAvatar = document.getElementById("headerAvatar");

        if (roleLabel) {
            roleLabel.textContent = role;
        }

        if (nameLabel) {
            nameLabel.textContent = displayName;
        }

        if (headerName) {
            headerName.textContent = displayName;
        }

        if (headerRole) {
            headerRole.textContent = role;
        }

        if (headerAvatar) {
            headerAvatar.textContent =
                displayName.trim().charAt(0).toUpperCase() || "U";
            headerAvatar.setAttribute("aria-label", displayName);
        }

        if (uploadForm) {
            uploadForm.hidden = currentUser.role !== "OWNER";
        }

        const activitySection = document.getElementById("activity");
        if (activitySection) {
            activitySection.hidden = currentUser.role !== "ADMIN";
        }
    }

    function formatBytes(bytes) {
        if (
            bytes === null ||
            bytes === undefined ||
            !Number.isFinite(Number(bytes)) ||
            Number(bytes) < 0
        ) {
            return "Unknown size";
        }

        const value = Number(bytes);

        if (value === 0) return "0 B";

        const units = ["B", "KB", "MB", "GB", "TB"];
        const index = Math.min(
            Math.floor(Math.log(value) / Math.log(1024)),
            units.length - 1
        );

        return `${(value / Math.pow(1024, index)).toFixed(
            index === 0 ? 0 : 2
        )} ${units[index]}`;
    }

    function formatDate(value) {
        if (!value) return "Date unavailable";

        const date = new Date(value);

        return Number.isNaN(date.getTime())
            ? "Date unavailable"
            : date.toLocaleString();
    }

    function showMessage(element, message, type = "info") {
        if (!element) return;

        element.textContent = message;
        element.dataset.kind = type;
        element.classList.remove("success", "error", "info");
        element.classList.add(type);
    }

    function clearMessage(element) {
        if (!element) return;

        element.textContent = "";
        delete element.dataset.kind;
        element.classList.remove("success", "error", "info");
    }

    async function responseError(response) {
        let serverMessage = "";

        try {
            const contentType = response.headers.get("content-type") || "";

            if (contentType.includes("application/json")) {
                const body = await response.json();
                serverMessage =
                    body.message || body.detail || body.error || "";
            } else {
                const bodyText = await response.text();

                if (
                    bodyText &&
                    !bodyText.trimStart().startsWith("<") &&
                    bodyText.length < 500
                ) {
                    serverMessage = bodyText.trim();
                }
            }
        } catch (error) {
            console.warn("Could not read the error response.", error);
        }

        if (serverMessage) return serverMessage;

        switch (response.status) {
            case 400:
                return "The server rejected the request. Check your input.";
            case 401:
                return "Your session may have expired. Sign in again.";
            case 403:
                return "Access denied. Your account may not have permission.";
            case 404:
                return "The requested document or API endpoint was not found.";
            case 413:
                return "The file exceeds the server's configured upload limit.";
            case 415:
                return "The server does not support this request content type.";
            case 429:
                return "Too many requests. Wait a moment and try again.";
            default:
                return `Request failed with HTTP ${response.status}.`;
        }
    }

    async function getCsrfToken() {
        const response = await fetch("/api/csrf", {
            method: "GET",
            credentials: "same-origin",
            cache: "no-store",
            headers: { Accept: "application/json" }
        });

        if (!response.ok) {
            throw new Error(await responseError(response));
        }

        const data = await response.json();

        if (!data.token || !data.headerName) {
            throw new Error(
                "The server did not return a valid CSRF token. Refresh the page."
            );
        }

        return {
            token: data.token,
            headerName: data.headerName
        };
    }

    function createDocumentMeta(item) {
        const meta = document.createElement("p");
        meta.className = "document-meta";

        const details = [formatBytes(item.sizeBytes)];

        if (item.createdAt) details.push(formatDate(item.createdAt));

        if (item.contentType) details.push(item.contentType);

        meta.textContent = details.join(" · ");
        return meta;
    }


    // TRUSTVAULT_PERMISSION_UI_V1
    async function openPermissionManager(item) {
        if (!document.getElementById("trustVaultPermissionStyles")) {
            const style = document.createElement("style");
            style.id = "trustVaultPermissionStyles";
            style.textContent = `
                .tv-permission-dialog {
                    width: min(560px, calc(100% - 32px));
                    max-height: 85vh;
                    overflow: auto;
                    padding: 24px;
                    border: 1px solid #d8dce5;
                    border-radius: 16px;
                    color: #202536;
                    background: #fff;
                    box-shadow: 0 24px 80px #0003;
                }
                .tv-permission-dialog::backdrop {
                    background: #11182799;
                }
                .tv-permission-dialog h2 { margin-top: 0; }
                .tv-permission-dialog label {
                    display: block;
                    margin: 12px 0 6px;
                    font-weight: 600;
                }
                .tv-permission-dialog input,
                .tv-permission-dialog select {
                    box-sizing: border-box;
                    width: 100%;
                    min-height: 42px;
                    padding: 9px 11px;
                    border: 1px solid #cbd0dc;
                    border-radius: 8px;
                    font: inherit;
                }
                .tv-permission-dialog button {
                    cursor: pointer;
                    padding: 9px 13px;
                    border: 1px solid #d1d5df;
                    border-radius: 8px;
                    background: #f8fafc;
                    color: #202536;
                    font: inherit;
                }
                .tv-permission-dialog button:disabled {
                    cursor: wait;
                    opacity: .6;
                }
                .tv-permission-heading,
                .tv-permission-row,
                .tv-permission-actions {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 10px;
                }
                .tv-permission-heading { margin-bottom: 18px; }
                .tv-permission-row {
                    padding: 12px 0;
                    border-bottom: 1px solid #e5e7eb;
                    flex-wrap: wrap;
                }
                .tv-permission-email { overflow-wrap: anywhere; }
                .tv-permission-subtitle {
                    color: #647084;
                    font-size: 13px;
                }
                .tv-permission-status {
                    min-height: 22px;
                    margin: 12px 0;
                    overflow-wrap: anywhere;
                }
                .tv-permission-revoke {
                    color: #b42318 !important;
                }
                .tv-permission-grant {
                    margin-top: 14px;
                }
            `;
            document.head.appendChild(style);
        }

        const dialog = document.createElement("dialog");
        dialog.className = "tv-permission-dialog";
        dialog.setAttribute("aria-labelledby", "tvPermissionTitle");

        const heading = document.createElement("div");
        heading.className = "tv-permission-heading";

        const title = document.createElement("h2");
        title.id = "tvPermissionTitle";
        title.textContent = "Manage document access";

        const closeButton = document.createElement("button");
        closeButton.type = "button";
        closeButton.textContent = "Close";
        closeButton.addEventListener("click", () => dialog.close());
        heading.append(title, closeButton);

        const documentName = document.createElement("p");
        documentName.className = "tv-permission-subtitle";
        documentName.textContent =
            item.originalName || "Untitled document";

        const status = document.createElement("p");
        status.className = "tv-permission-status";
        status.setAttribute("role", "status");
        status.setAttribute("aria-live", "polite");

        const form = document.createElement("form");
        const emailLabel = document.createElement("label");
        emailLabel.textContent = "Registered user's email";

        const email = document.createElement("input");
        email.type = "email";
        email.required = true;
        email.maxLength = 320;
        email.autocomplete = "email";
        email.placeholder = "user@example.com";
        emailLabel.appendChild(email);

        const accessLabel = document.createElement("label");
        accessLabel.textContent = "Permission";

        const access = document.createElement("select");
        for (const [value, label] of [
            ["READ", "READ — view and download"],
            ["EDIT", "EDIT — modify access-controlled content"]
        ]) {
            const option = document.createElement("option");
            option.value = value;
            option.textContent = label;
            access.appendChild(option);
        }
        accessLabel.appendChild(access);

        const grantButton = document.createElement("button");
        grantButton.type = "submit";
        grantButton.className = "tv-permission-grant";
        grantButton.textContent = "Grant access";

        form.append(emailLabel, accessLabel, grantButton);

        const listHeading = document.createElement("h3");
        listHeading.textContent = "Current access";

        const permissionList = document.createElement("div");
        permissionList.setAttribute("aria-live", "polite");

        dialog.append(
            heading, documentName, status, form,
            listHeading, permissionList
        );
        document.body.appendChild(dialog);
        dialog.addEventListener("close", () => dialog.remove(), {
            once: true
        });
        dialog.showModal();

        const endpoint =
            `/api/documents/${encodeURIComponent(item.id)}/permissions`;

        async function refreshPermissions() {
            permissionList.replaceChildren();

            const response = await fetch(endpoint, {
                method: "GET",
                credentials: "same-origin",
                cache: "no-store",
                headers: { Accept: "application/json" }
            });

            if (!response.ok) {
                throw new Error(await responseError(response));
            }

            const permissions = await response.json();
            const active = permissions.filter(
                permission => !permission.revokedAt
            );

            if (active.length === 0) {
                const empty = document.createElement("p");
                empty.className = "tv-permission-subtitle";
                empty.textContent = "No active permission grants.";
                permissionList.appendChild(empty);
                return;
            }

            for (const permission of active) {
                const row = document.createElement("div");
                row.className = "tv-permission-row";

                const details = document.createElement("div");
                const recipient = document.createElement("div");
                recipient.className = "tv-permission-email";
                recipient.textContent =
                    permission.displayName
                        ? `${permission.displayName} (${permission.email})`
                        : permission.email;

                const level = document.createElement("div");
                level.className = "tv-permission-subtitle";
                level.textContent =
                    `${permission.permission} access`;

                details.append(recipient, level);

                const revokeButton = document.createElement("button");
                revokeButton.type = "button";
                revokeButton.className = "tv-permission-revoke";
                revokeButton.textContent = "Revoke";

                revokeButton.addEventListener("click", async () => {
                    if (!window.confirm(
                        `Revoke access for ${permission.email}?`
                    )) return;

                    revokeButton.disabled = true;
                    status.textContent = "Revoking access…";

                    try {
                        const csrf = await getCsrfToken();
                        const response = await fetch(
                            `${endpoint}/${encodeURIComponent(
                                permission.permissionId
                            )}`,
                            {
                                method: "DELETE",
                                credentials: "same-origin",
                                headers: {
                                    Accept: "application/json",
                                    [csrf.headerName]: csrf.token
                                }
                            }
                        );

                        if (!response.ok) {
                            throw new Error(await responseError(response));
                        }

                        status.textContent =
                            "Access revoked successfully.";
                        await refreshPermissions();
                    } catch (error) {
                        status.textContent =
                            error.message || "Could not revoke access.";
                        revokeButton.disabled = false;
                    }
                });

                row.append(details, revokeButton);
                permissionList.appendChild(row);
            }
        }

        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (!form.reportValidity()) return;

            grantButton.disabled = true;
            status.textContent = "Granting access…";

            try {
                const csrf = await getCsrfToken();
                const response = await fetch(endpoint, {
                    method: "POST",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        "Content-Type": "application/json",
                        [csrf.headerName]: csrf.token
                    },
                    body: JSON.stringify({
                        email: email.value.trim(),
                        permission: access.value
                    })
                });

                if (!response.ok) {
                    throw new Error(await responseError(response));
                }

                email.value = "";
                status.textContent = "Access granted successfully.";
                await refreshPermissions();
            } catch (error) {
                status.textContent =
                    error.message || "Could not grant access.";
            } finally {
                grantButton.disabled = false;
            }
        });

        try {
            await refreshPermissions();
        } catch (error) {
            status.textContent =
                error.message || "Could not load current permissions.";
        }
    }

    function createPermissionButton(item) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "document-share";
        button.textContent = "Manage access";
        button.setAttribute(
            "aria-label",
            `Manage access for ${item.originalName || "document"}`
        );

        button.addEventListener("click", () => {
            button.disabled = true;
            openPermissionManager(item)
                .catch(error => {
                    showMessage(
                        documentsMessage,
                        error.message || "Could not open access management.",
                        "error"
                    );
                })
                .finally(() => {
                    button.disabled = false;
                });
        });

        return button;
    }

    function makeDocumentRow(item) {
        const row = document.createElement("article");
        row.className = "document-row";
        row.dataset.documentId = item.id;

        const left = document.createElement("div");
        left.className = "document-left";

        const icon = document.createElement("div");
        icon.className = "document-file-icon";
        icon.setAttribute("aria-hidden", "true");
        icon.textContent = "▤";

        const info = document.createElement("div");
        info.className = "document-info";

        const name = document.createElement("h3");
        name.className = "document-name";
        name.textContent = item.originalName || "Untitled document";
        name.title = item.originalName || "Untitled document";

        info.append(name, createDocumentMeta(item));
        left.append(icon, info);

        const actions = document.createElement("div");
        actions.className = "document-actions";

        const download = document.createElement("a");
        download.className = "document-download";
        download.textContent = "↓ Download";
        download.href =
            `/api/documents/${encodeURIComponent(item.id)}/download`;
        download.setAttribute(
            "aria-label",
            `Download ${item.originalName || "document"}`
        );

        actions.appendChild(download);

        const canManageDocument =
            currentUser !== null
            && currentUser.role === "OWNER"
            && String(item.ownerId) === String(currentUser.id);

        if (canManageDocument) {
            actions.appendChild(createPermissionButton(item));

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "document-delete";
        deleteButton.textContent = "Delete";
        deleteButton.setAttribute(
            "aria-label",
            `Delete ${item.originalName || "document"}`
        );

        deleteButton.addEventListener("click", async () => {
            const documentName = item.originalName || "this document";

            if (!window.confirm(`Are you sure you want to delete "${documentName}"?`)) {
                return;
            }

            deleteButton.disabled = true;
            clearMessage(documentsMessage);

            try {
                const csrf = await getCsrfToken();

                const response = await fetch(
                    `/api/documents/${encodeURIComponent(item.id)}`,
                    {
                        method: "DELETE",
                        credentials: "same-origin",
                        headers: {
                            Accept: "application/json",
                            [csrf.headerName]: csrf.token
                        }
                    }
                );

                if (!response.ok) {
                    throw new Error(await responseError(response));
                }

                showMessage(
                    documentsMessage,
                    "Document deleted successfully.",
                    "success"
                );

                await loadDocuments();
            } catch (error) {
                console.error("Document deletion failed:", error);
                showMessage(
                    documentsMessage,
                    error.message || "Unable to delete the document.",
                    "error"
                );
            } finally {
                deleteButton.disabled = false;
            }
        });

        actions.appendChild(deleteButton);
        }

        row.append(left, actions);

        return row;
    }


    function renderDashboardInsights(documents) {
        const recent = document.getElementById("recentUploadsList");
        const types = document.getElementById("fileTypeSummary");

        if (recent) {
            recent.replaceChildren();
            const ordered = [...documents].sort((a, b) =>
                (Date.parse(b.createdAt || "") || 0) -
                (Date.parse(a.createdAt || "") || 0)
            ).slice(0, 5);

            if (ordered.length === 0) {
                const empty = document.createElement("p");
                empty.textContent = "No uploads yet.";
                empty.style.color = "#827895";
                empty.style.fontSize = "13px";
                recent.appendChild(empty);
            } else {
                for (const item of ordered) {
                    const row = document.createElement("div");
                    row.style.cssText =
                        "display:flex;justify-content:space-between;gap:12px;" +
                        "align-items:center;padding:9px 0;border-bottom:1px solid #f0ebf7;";

                    const info = document.createElement("div");
                    info.style.cssText = "min-width:0;";

                    const name = document.createElement("div");
                    name.textContent = item.originalName || "Untitled document";
                    name.title = name.textContent;
                    name.style.cssText =
                        "font-size:13px;font-weight:600;overflow-wrap:anywhere;";

                    const date = document.createElement("div");
                    date.textContent = formatDate(item.createdAt);
                    date.style.cssText = "font-size:11px;color:#827895;margin-top:4px;";

                    info.append(name, date);

                    const link = document.createElement("a");
                    link.href = "/api/documents/" + encodeURIComponent(item.id) + "/download";
                    link.textContent = "Download";
                    link.style.cssText = "font-size:12px;white-space:nowrap;color:#5b21b6;";
                    link.setAttribute("aria-label", "Download " + name.textContent);

                    row.append(info, link);
                    recent.appendChild(row);
                }
            }
        }

        if (types) {
            const counts = {
                "PDF": 0,
                "Word": 0,
                "Spreadsheets": 0,
                "Images": 0,
                "Other": 0
            };

            for (const item of documents) {
                const name = (item.originalName || "").toLowerCase();
                const mime = (item.contentType || "").toLowerCase();

                if (mime.includes("pdf") || name.endsWith(".pdf")) {
                    counts.PDF++;
                } else if (
                    mime.includes("word") || mime.includes("officedocument.word") ||
                    name.endsWith(".doc") || name.endsWith(".docx")
                ) {
                    counts.Word++;
                } else if (
                    mime.includes("spreadsheet") || mime.includes("excel") ||
                    name.endsWith(".xls") || name.endsWith(".xlsx") ||
                    name.endsWith(".csv")
                ) {
                    counts.Spreadsheets++;
                } else if (
                    mime.startsWith("image/") ||
                    /\.(png|jpe?g|gif|webp|svg|bmp|tiff?)$/.test(name)
                ) {
                    counts.Images++;
                } else {
                    counts.Other++;
                }
            }

            types.replaceChildren();

            for (const [label, count] of Object.entries(counts)) {
                const row = document.createElement("div");
                row.style.cssText =
                    "display:flex;justify-content:space-between;align-items:center;" +
                    "padding:9px 12px;background:#f8f6fc;border-radius:8px;font-size:13px;";

                const category = document.createElement("span");
                category.textContent = label;

                const number = document.createElement("strong");
                number.textContent = String(count);

                row.append(category, number);
                types.appendChild(row);
            }
        }
    }

    function updateStatistics(documents) {
        if (documentCount) {
            documentCount.textContent = String(documents.length);
        }

        let totalBytes = 0;
        let knownSizes = 0;

        for (const item of documents) {
            const size = Number(item.sizeBytes);

            if (Number.isFinite(size) && size >= 0) {
                totalBytes += size;
                knownSizes++;
            }
        }

        if (storageUsage) {
            storageUsage.textContent =
                knownSizes === documents.length
                    ? formatBytes(totalBytes)
                    : documents.length === 0
                        ? "0 B"
                        : `${formatBytes(totalBytes)}+`;
        }
    

        renderDashboardInsights(documents);
    }

    function getLocalDate(value) {
        if (!value) return "";

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) return "";

        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");

        return `${year}-${month}-${day}`;
    }

    function applyLocalFilters(documents) {
        const from = dateFrom?.value || "";
        const to = dateTo?.value || "";
        const sort = sortSelect?.value || "newest";

        let results = [...documents];

        if (from) {
            results = results.filter((item) => {
                const created = getLocalDate(item.createdAt);
                return created !== "" && created >= from;
            });
        }

        if (to) {
            results = results.filter((item) => {
                const created = getLocalDate(item.createdAt);
                return created !== "" && created <= to;
            });
        }

        results.sort((a, b) => {
            const nameA = (a.originalName || "").toLocaleLowerCase();
            const nameB = (b.originalName || "").toLocaleLowerCase();
            const dateA = Date.parse(a.createdAt || "") || 0;
            const dateB = Date.parse(b.createdAt || "") || 0;
            const sizeA = Number(a.sizeBytes) || 0;
            const sizeB = Number(b.sizeBytes) || 0;

            switch (sort) {
                case "oldest":
                    return dateA - dateB;
                case "name-asc":
                    return nameA.localeCompare(nameB);
                case "name-desc":
                    return nameB.localeCompare(nameA);
                case "largest":
                    return sizeB - sizeA;
                case "smallest":
                    return sizeA - sizeB;
                case "newest":
                default:
                    return dateB - dateA;
            }
        });

        return results;
    }

    function renderDocuments(documents) {
        if (!documentsList) return;

        const viewDocuments = documents.filter((item) => {
            if (documentView === "all") return true;
            if (!currentUser || !item.ownerId) return false;

            const ownedByCurrentUser =
                String(item.ownerId) === String(currentUser.id);

            return documentView === "mine"
                ? ownedByCurrentUser
                : !ownedByCurrentUser;
        });

        const results = applyLocalFilters(viewDocuments);
        const fragment = document.createDocumentFragment();

        for (const item of results) {
            fragment.appendChild(makeDocumentRow(item));
        }

        documentsList.replaceChildren(fragment);

        const hasActiveFilters =
            Boolean(searchName?.value.trim()) ||
            Boolean(searchOwner?.value.trim()) ||
            Boolean(dateFrom?.value) ||
            Boolean(dateTo?.value);

        if (documentsEmpty) {
            // Do not show the first-upload empty state when filters hide results.
            documentsEmpty.hidden =
                results.length > 0 ||
                hasActiveFilters ||
                viewDocuments.length > 0;
        }

        if (filterStatus) {
            filterStatus.textContent = hasActiveFilters
                ? `Showing ${results.length} matching document(s).`
                : `Showing ${results.length} document(s).`;
        }

        if (results.length === 0) {
            showMessage(
                documentsMessage,
                viewDocuments.length === 0
                    ? documentView === "shared"
                        ? "No documents have been shared with you yet."
                        : documentView === "mine"
                            ? "You have not uploaded any documents yet."
                            : "No documents yet. Upload a document to get started."
                    : "No documents match your search or filters.",
                "info"
            );
        } else {
            clearMessage(documentsMessage);
        }
    }

    async function loadDocuments() {
        if (!documentsList || isLoading) return;

        isLoading = true;

        if (refreshDocuments) refreshDocuments.disabled = true;

        clearMessage(documentsMessage);

        try {
            const response = await fetch("/api/documents", {
                method: "GET",
                credentials: "same-origin",
                cache: "no-store",
                headers: { Accept: "application/json" }
            });

            if (!response.ok) {
                throw new Error(await responseError(response));
            }

            const documents = await response.json();

            if (!Array.isArray(documents)) {
                throw new Error("The server returned an unexpected document list.");
            }

            allDocuments = documents;
            updateStatistics(allDocuments);

            if (workspaceStatus) {
                workspaceStatus.textContent = "Connected";
                workspaceStatus.classList.remove("error");
                workspaceStatus.classList.add("success");
            }

            // Re-run active server search, or render the full list.
            await searchDocuments();
        } catch (error) {
            console.error("Could not load documents:", error);

            showMessage(
                documentsMessage,
                error.message || "Unable to load documents. Please retry.",
                "error"
            );

            if (workspaceStatus) {
                workspaceStatus.textContent = "Connection issue";
                workspaceStatus.classList.remove("success");
                workspaceStatus.classList.add("error");
            }
        } finally {
            isLoading = false;
            if (refreshDocuments) refreshDocuments.disabled = false;
        }
    }

    async function searchDocuments() {
        const requestId = ++searchRequestId;
        const name = searchName?.value.trim() || "";
        const ownerEmail = searchOwner?.value.trim() || "";

        if (!name && !ownerEmail) {
            renderDocuments(allDocuments);
            return;
        }

        const params = new URLSearchParams();

        if (name) params.set("name", name);
        if (ownerEmail) params.set("ownerEmail", ownerEmail);

        if (filterStatus) filterStatus.textContent = "Searching documents…";

        try {
            const response = await fetch(
                `/api/documents/search?${params.toString()}`,
                {
                    method: "GET",
                    credentials: "same-origin",
                    cache: "no-store",
                    headers: { Accept: "application/json" }
                }
            );

            if (!response.ok) {
                throw new Error(await responseError(response));
            }

            const results = await response.json();

            if (!Array.isArray(results)) {
                throw new Error("The server returned unexpected search results.");
            }

            // Ignore older responses if a newer search has already started.
            if (requestId !== searchRequestId) return;

            renderDocuments(results);
        } catch (error) {
            if (requestId !== searchRequestId) return;

            console.error("Document search failed:", error);
            showMessage(
                documentsMessage,
                error.message || "Search failed. Please try again.",
                "error"
            );

            if (filterStatus) {
                filterStatus.textContent = "Search could not be completed.";
            }
        }
    }

    function scheduleSearch() {
        if (searchTimer) clearTimeout(searchTimer);

        searchTimer = setTimeout(() => {
            searchDocuments();
        }, 300);
    }

    if (searchName) {
        searchName.addEventListener("input", scheduleSearch);
    }

    if (searchOwner) {
        searchOwner.addEventListener("input", scheduleSearch);
    }

    if (dateFrom) {
        dateFrom.addEventListener("change", () => {
            if (dateFrom.value && dateTo?.value && dateFrom.value > dateTo.value) {
                showMessage(
                    documentsMessage,
                    "'Created from' cannot be later than 'Created to'.",
                    "error"
                );
                return;
            }

            searchDocuments();
        });
    }

    if (dateTo) {
        dateTo.addEventListener("change", () => {
            if (dateFrom?.value && dateTo.value && dateTo.value < dateFrom.value) {
                showMessage(
                    documentsMessage,
                    "'Created to' cannot be earlier than 'Created from'.",
                    "error"
                );
                return;
            }

            searchDocuments();
        });
    }

    if (sortSelect) {
        sortSelect.addEventListener("change", () => {
            searchDocuments();
        });
    }

    if (clearFilters) {
        clearFilters.addEventListener("click", () => {
            if (searchTimer) clearTimeout(searchTimer);

            if (searchName) searchName.value = "";
            if (searchOwner) searchOwner.value = "";
            if (dateFrom) dateFrom.value = "";
            if (dateTo) dateTo.value = "";
            if (sortSelect) sortSelect.value = "newest";

            clearMessage(documentsMessage);
            searchRequestId++;
            renderDocuments(allDocuments);
        });
    }

    if (refreshDocuments) {
        refreshDocuments.addEventListener("click", loadDocuments);
    }

    if (uploadForm) {
        uploadForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearMessage(uploadMessage);

            if (!documentFile || !documentFile.files) {
                showMessage(
                    uploadMessage,
                    "The file input could not be found. Refresh the page.",
                    "error"
                );
                return;
            }

            const file = documentFile.files[0];

            if (!file) {
                showMessage(uploadMessage, "Please select a file to upload.", "error");
                documentFile.focus();
                return;
            }

            if (file.size === 0) {
                showMessage(uploadMessage, "The selected file is empty.", "error");
                return;
            }

            if (file.size > MAX_FILE_BYTES) {
                showMessage(
                    uploadMessage,
                    "The selected file exceeds the 2 GB application limit.",
                    "error"
                );
                return;
            }

            const originalButtonHTML = uploadButton
                ? uploadButton.innerHTML
                : "";

            if (uploadButton) {
                uploadButton.disabled = true;
                uploadButton.textContent = "Uploading...";
            }

            documentFile.disabled = true;

            if (workspaceStatus) workspaceStatus.textContent = "Uploading";

            try {
                showMessage(uploadMessage, "Preparing secure upload...", "info");

                const csrf = await getCsrfToken();
                const formData = new FormData();
                formData.append("file", file, file.name);

                const response = await fetch("/api/documents", {
                    method: "POST",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        [csrf.headerName]: csrf.token
                    },
                    body: formData
                });

                if (!response.ok) {
                    throw new Error(await responseError(response));
                }

                let uploadedDocument = null;
                const contentType = response.headers.get("content-type") || "";

                if (contentType.includes("application/json")) {
                    uploadedDocument = await response.json();
                }

                showMessage(
                    uploadMessage,
                    uploadedDocument
                        ? `"${uploadedDocument.originalName || file.name}" uploaded successfully.`
                        : `"${file.name}" uploaded successfully.`,
                    "success"
                );

                uploadForm.reset();

                // Refresh the complete list and update the dashboard statistics.
                await loadDocuments();
            } catch (error) {
                console.error("Document upload error:", error);
                showMessage(
                    uploadMessage,
                    error.message || "Upload failed. Check the server logs and retry.",
                    "error"
                );
            } finally {
                if (uploadButton) {
                    uploadButton.disabled = false;
                    uploadButton.innerHTML =
                        originalButtonHTML || "↑ Upload document";
                }

                documentFile.disabled = false;

                if (workspaceStatus && workspaceStatus.textContent === "Uploading") {
                    workspaceStatus.textContent = "Connected";
                }
            }
        });
    }


    // TrustVault drag-and-drop upload.
    const uploadDropzone = document.getElementById("uploadDropzone");
    const uploadSelectedFile = document.getElementById("uploadSelectedFile");

    if (uploadDropzone && uploadForm && documentFile) {
        const maxDropFileBytes = 2 * 1024 * 1024 * 1024;

        function updateSelectedFileMessage(file) {
            if (!uploadSelectedFile) return;

            if (!file) {
                uploadSelectedFile.textContent = "No file selected";
                return;
            }

            const size = file.size < 1024 * 1024
                ? `${(file.size / 1024).toFixed(1)} KB`
                : `${(file.size / (1024 * 1024)).toFixed(2)} MB`;

            uploadSelectedFile.textContent =
                `Selected: ${file.name} (${size})`;
        }

        documentFile.addEventListener("change", () => {
            const file = documentFile.files?.[0] || null;
            updateSelectedFileMessage(file);

            if (file) {
                showMessage(
                    uploadMessage,
                    `"${file.name}" selected. Press Upload document to continue.`,
                    "info"
                );
            }
        });

        uploadDropzone.addEventListener("dragenter", (event) => {
            event.preventDefault();
            uploadDropzone.classList.add("is-dragging");
        });

        uploadDropzone.addEventListener("dragover", (event) => {
            event.preventDefault();

            if (event.dataTransfer) {
                event.dataTransfer.dropEffect = "copy";
            }

            uploadDropzone.classList.add("is-dragging");
        });

        uploadDropzone.addEventListener("dragleave", (event) => {
            if (!uploadDropzone.contains(event.relatedTarget)) {
                uploadDropzone.classList.remove("is-dragging");
            }
        });

        uploadDropzone.addEventListener("drop", (event) => {
            event.preventDefault();
            uploadDropzone.classList.remove("is-dragging");

            const files = event.dataTransfer?.files;

            if (!files || files.length === 0) return;

            if (files.length !== 1) {
                showMessage(
                    uploadMessage,
                    "Please drop one file at a time.",
                    "error"
                );
                return;
            }

            const file = files[0];

            if (file.size === 0) {
                showMessage(
                    uploadMessage,
                    "The selected file is empty.",
                    "error"
                );
                return;
            }

            if (file.size > maxDropFileBytes) {
                showMessage(
                    uploadMessage,
                    "The selected file exceeds the 2 GB application limit.",
                    "error"
                );
                return;
            }

            try {
                const transfer = new DataTransfer();
                transfer.items.add(file);
                documentFile.files = transfer.files;

                updateSelectedFileMessage(file);

                showMessage(
                    uploadMessage,
                    `"${file.name}" selected. Press Upload document to continue.`,
                    "info"
                );
            } catch (error) {
                console.error("Could not select the dropped file:", error);

                showMessage(
                    uploadMessage,
                    "Could not select that file. Please use Browse files instead.",
                    "error"
                );
            }
        });

        uploadDropzone.addEventListener("keydown", (event) => {
            if (event.target !== uploadDropzone) return;

            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                documentFile.click();
            }
        });

        uploadForm.addEventListener("reset", () => {
            updateSelectedFileMessage(null);
            uploadDropzone.classList.remove("is-dragging");
        });
    }

    loadCurrentUser()
        .then(() => loadDocuments())
        .catch(error => {
            console.error("Role initialization failed:", error);

            // Fail closed for owner/admin UI controls.
            if (uploadForm) uploadForm.hidden = true;

            const activitySection = document.getElementById("activity");
            if (activitySection) activitySection.hidden = true;

            currentUser = null;
            loadDocuments();
        });
});

// TRUSTVAULT_AUDIT_UI_V1
document.addEventListener("DOMContentLoaded", () => {
    const body = document.getElementById("auditEventsBody");
    if (!body) return;

    const message = document.getElementById("auditMessage");
    const pageInfo = document.getElementById("auditPageInfo");
    const searchInput = document.getElementById("auditSearch");
    const actionInput = document.getElementById("auditAction");
    const outcomeInput = document.getElementById("auditOutcome");
    const applyButton = document.getElementById("auditApply");
    const refreshButton = document.getElementById("auditRefresh");
    const previousButton = document.getElementById("auditPrevious");
    const nextButton = document.getElementById("auditNext");

    let currentPage = 0;
    const pageSize = 25;
    let requestNumber = 0;

    function setMessage(text) {
        message.textContent = text;
    }

    function addCell(row, value) {
        const cell = document.createElement("td");
        cell.style.padding = "11px";
        cell.style.borderBottom = "1px solid #f0ebf5";
        cell.style.verticalAlign = "top";
        cell.textContent =
            value === null || value === undefined || value === ""
                ? "—"
                : String(value);
        row.appendChild(cell);
    }

    function formatTime(value) {
        if (!value) return "—";
        const date = new Date(value);
        return Number.isNaN(date.getTime())
            ? String(value)
            : date.toLocaleString();
    }

    function showEmptyRow(text) {
        body.replaceChildren();
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 7;
        cell.style.padding = "20px 11px";
        cell.style.color = "#827895";
        cell.textContent = text;
        row.appendChild(cell);
        body.appendChild(row);
    }

    function renderEvents(events) {
        body.replaceChildren();

        if (!Array.isArray(events) || events.length === 0) {
            showEmptyRow("No audit events match these filters.");
            return;
        }

        for (const event of events) {
            const row = document.createElement("tr");
            addCell(row, formatTime(event.eventTime));
            addCell(row, event.actorEmail);
            addCell(row, event.action);
            addCell(row, event.outcome);

            const documentAndTarget = [
                event.documentName,
                event.targetEmail ? "Target: " + event.targetEmail : ""
            ].filter(Boolean).join("\n");
            addCell(row, documentAndTarget);

            addCell(row, event.reasonCode);
            addCell(row, event.eventNo);
            body.appendChild(row);
        }
    }

    async function loadAuditEvents() {
        const thisRequest = ++requestNumber;
        setMessage("Loading audit events…");
        applyButton.disabled = true;
        refreshButton.disabled = true;
        previousButton.disabled = true;
        nextButton.disabled = true;

        const params = new URLSearchParams({
            page: String(currentPage),
            size: String(pageSize)
        });

        const search = searchInput.value.trim();
        const action = actionInput.value.trim();
        const outcome = outcomeInput.value;

        if (search) params.set("search", search);
        if (action) params.set("action", action);
        if (outcome) params.set("outcome", outcome);

        try {
            const response = await fetch(
                "/api/admin/audit?" + params.toString(),
                {
                    method: "GET",
                    credentials: "same-origin",
                    headers: { "Accept": "application/json" }
                }
            );

            if (thisRequest !== requestNumber) return;

            if (response.status === 401) {
                showEmptyRow("Please sign in to view audit events.");
                setMessage("Authentication required.");
                pageInfo.textContent = "Access unavailable";
                return;
            }

            if (response.status === 403) {
                showEmptyRow("Audit events are restricted to administrators.");
                setMessage("Administrator access is required.");
                pageInfo.textContent = "Admin access only";
                return;
            }

            if (!response.ok) {
                throw new Error("Audit API returned HTTP " + response.status);
            }

            const result = await response.json();
            if (thisRequest !== requestNumber) return;

            renderEvents(result.events);

            const total = Number(result.totalElements) || 0;
            const page = Number(result.page) || 0;
            const size = Number(result.size) || pageSize;
            const first = total === 0 ? 0 : page * size + 1;
            const last = Math.min((page + 1) * size, total);

            pageInfo.textContent =
                total === 0
                    ? "0 events"
                    : first + "–" + last + " of " + total + " events";

            currentPage = page;
            previousButton.disabled = page <= 0;
            nextButton.disabled = (page + 1) * size >= total;
            setMessage("Audit events loaded from the server.");
        } catch (error) {
            if (thisRequest !== requestNumber) return;
            showEmptyRow("Audit events could not be loaded.");
            setMessage(
                error instanceof Error
                    ? error.message
                    : "Unable to load audit events."
            );
            pageInfo.textContent = "Unable to load events";
        } finally {
            if (thisRequest === requestNumber) {
                applyButton.disabled = false;
                refreshButton.disabled = false;
            }
        }
    }

    applyButton.addEventListener("click", () => {
        currentPage = 0;
        loadAuditEvents();
    });

    refreshButton.addEventListener("click", loadAuditEvents);

    for (const input of [searchInput, actionInput]) {
        input.addEventListener("keydown", event => {
            if (event.key === "Enter") {
                currentPage = 0;
                loadAuditEvents();
            }
        });
    }

    outcomeInput.addEventListener("change", () => {
        currentPage = 0;
        loadAuditEvents();
    });

    previousButton.addEventListener("click", () => {
        if (currentPage > 0) {
            currentPage--;
            loadAuditEvents();
        }
    });

    nextButton.addEventListener("click", () => {
        currentPage++;
        loadAuditEvents();
    });

    loadAuditEvents();
});
