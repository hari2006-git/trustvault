
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

        const results = applyLocalFilters(documents);
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
                allDocuments.length > 0;
        }

        if (filterStatus) {
            filterStatus.textContent = hasActiveFilters
                ? `Showing ${results.length} matching document(s).`
                : `Showing ${results.length} document(s).`;
        }

        if (results.length === 0) {
            showMessage(
                documentsMessage,
                allDocuments.length === 0
                    ? "No documents yet. Upload a document to get started."
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


    // TrustVault drag-and-drop upload. Dropping selects a file;
    // the user still presses Upload to submit it.
    if (uploadForm && documentFile) {
        uploadForm.addEventListener("dragover", (event) => {
            event.preventDefault();
            if (event.dataTransfer) event.dataTransfer.dropEffect = "copy";
            uploadForm.style.outline = "2px dashed #7c3aed";
            uploadForm.style.outlineOffset = "4px";
        });

        uploadForm.addEventListener("dragleave", (event) => {
            if (!uploadForm.contains(event.relatedTarget)) {
                uploadForm.style.outline = "";
                uploadForm.style.outlineOffset = "";
            }
        });

        uploadForm.addEventListener("drop", (event) => {
            event.preventDefault();
            uploadForm.style.outline = "";
            uploadForm.style.outlineOffset = "";

            const files = event.dataTransfer?.files;
            if (!files || files.length === 0) return;

            if (files.length !== 1) {
                showMessage(uploadMessage, "Please drop one file at a time.", "error");
                return;
            }

            const transfer = new DataTransfer();
            transfer.items.add(files[0]);
            documentFile.files = transfer.files;

            showMessage(
                uploadMessage,
                `"${files[0].name}" selected. Press Upload document to continue.`,
                "info"
            );
        });
    }

    loadDocuments();
});
