
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

    // This client-side limit must match the backend configuration.
    const MAX_FILE_BYTES = 2 * 1024 * 1024 * 1024;

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

        if (value === 0) {
            return "0 B";
        }

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
        if (!value) {
            return "Date unavailable";
        }

        const date = new Date(value);

        return Number.isNaN(date.getTime())
            ? "Date unavailable"
            : date.toLocaleString();
    }

    function showMessage(element, message, type = "info") {
        if (!element) {
            return;
        }

        element.textContent = message;
        element.dataset.kind = type;
        element.classList.remove("success", "error", "info");
        element.classList.add(type);
    }

    function clearMessage(element) {
        if (!element) {
            return;
        }

        element.textContent = "";
        delete element.dataset.kind;
        element.classList.remove("success", "error", "info");
    }

    async function responseError(response) {
        let serverMessage = "";

        try {
            const contentType =
                response.headers.get("content-type") || "";

            if (contentType.includes("application/json")) {
                const body = await response.json();

                serverMessage =
                    body.message ||
                    body.detail ||
                    body.error ||
                    "";
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

        if (serverMessage) {
            return serverMessage;
        }

        switch (response.status) {
            case 400:
                return "The server rejected the upload. Verify that the multipart field is named 'file' and inspect the server logs.";

            case 401:
                return "Your session may have expired. Sign in again and retry.";

            case 403:
                return "Access denied. Your account may not have upload permission, or the CSRF token was rejected.";

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
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(await responseError(response));
        }

        const data = await response.json();

        if (!data.token || !data.headerName) {
            throw new Error(
                "The server did not return a valid CSRF token. Refresh the page and try again."
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

        if (item.createdAt) {
            details.push(formatDate(item.createdAt));
        }

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

        // The backend must implement DELETE /api/documents/{id}.
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

            if (
                !window.confirm(
                    `Are you sure you want to delete "${documentName}"?`
                )
            ) {
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

    async function loadDocuments() {
        if (!documentsList) {
            return;
        }

        if (refreshDocuments) {
            refreshDocuments.disabled = true;
        }

        clearMessage(documentsMessage);

        try {
            const response = await fetch("/api/documents", {
                method: "GET",
                credentials: "same-origin",
                cache: "no-store",
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(await responseError(response));
            }

            const documents = await response.json();

            if (!Array.isArray(documents)) {
                throw new Error(
                    "The server returned an unexpected document list."
                );
            }

            documentsList.replaceChildren();

            let totalBytes = 0;
            let knownSizes = 0;

            for (const item of documents) {
                documentsList.appendChild(makeDocumentRow(item));

                const size = Number(item.sizeBytes);

                if (Number.isFinite(size) && size >= 0) {
                    totalBytes += size;
                    knownSizes++;
                }
            }

            if (documentCount) {
                documentCount.textContent = String(documents.length);
            }

            if (storageUsage) {
                storageUsage.textContent =
                    knownSizes === documents.length
                        ? formatBytes(totalBytes)
                        : documents.length === 0
                            ? "0 B"
                            : `${formatBytes(totalBytes)}+`;
            }

            if (documentsEmpty) {
                documentsEmpty.hidden = documents.length !== 0;
            }

            if (documents.length === 0) {
                showMessage(
                    documentsMessage,
                    "No documents yet. Upload a document to get started.",
                    "info"
                );
            } else {
                clearMessage(documentsMessage);
            }

            if (workspaceStatus) {
                workspaceStatus.textContent = "Connected";
                workspaceStatus.classList.remove("error");
                workspaceStatus.classList.add("success");
            }
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
            if (refreshDocuments) {
                refreshDocuments.disabled = false;
            }
        }
    }

    if (uploadForm) {
        uploadForm.addEventListener("submit", async (event) => {
            event.preventDefault();

            clearMessage(uploadMessage);

            if (!documentFile || !documentFile.files) {
                showMessage(
                    uploadMessage,
                    "The file input could not be found. Refresh the page and try again.",
                    "error"
                );
                return;
            }

            const file = documentFile.files[0];

            if (!file) {
                showMessage(
                    uploadMessage,
                    "Please select a file to upload.",
                    "error"
                );
                documentFile.focus();
                return;
            }

            if (file.size === 0) {
                showMessage(
                    uploadMessage,
                    "The selected file is empty.",
                    "error"
                );
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

            if (workspaceStatus) {
                workspaceStatus.textContent = "Uploading";
            }

            try {
                showMessage(
                    uploadMessage,
                    "Preparing secure upload...",
                    "info"
                );

                const csrf = await getCsrfToken();

                // Explicitly use the exact field name required by Spring:
                // @RequestParam("file") MultipartFile file
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
                    const message = await responseError(response);

                    console.error("Upload failed:", {
                        status: response.status,
                        statusText: response.statusText,
                        responseMessage: message,
                        fileName: file.name,
                        fileSize: file.size
                    });

                    throw new Error(message);
                }

                let uploadedDocument = null;
                const contentType =
                    response.headers.get("content-type") || "";

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

                await loadDocuments();
            } catch (error) {
                console.error("Document upload error:", error);

                showMessage(
                    uploadMessage,
                    error.message ||
                        "Upload failed. Check the server logs and try again.",
                    "error"
                );
            } finally {
                if (uploadButton) {
                    uploadButton.disabled = false;
                    uploadButton.innerHTML =
                        originalButtonHTML || "↑ Upload document";
                }

                documentFile.disabled = false;

                if (workspaceStatus) {
                    workspaceStatus.textContent = "Connected";
                }
            }
        });
    }

    if (refreshDocuments) {
        refreshDocuments.addEventListener("click", loadDocuments);
    }

    loadDocuments();
});
