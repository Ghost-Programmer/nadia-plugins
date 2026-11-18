(() => {
    "use strict";

    const TOKEN_STORAGE_KEY = "nadia_web_auth_token";
    const THEME_STORAGE_KEY = "nadia_web_theme";

    const state = {
        authToken: sessionStorage.getItem(TOKEN_STORAGE_KEY) || "",
        config: {
            title: "Nadia AI Assistant",
            authRequired: false,
            allowUploads: true,
            maxUploadMb: 25,
            enableCronStream: true
        },
        staffList: [],
        filteredStaff: [],
        selectedStaffIndex: 0,
        sessions: [],
        activeSessionId: null,
        messagesById: new Map(),
        pendingAttachments: [], // [{ attachmentId, fileName, sizeBytes }]
        eventSource: null,
        contextTargetMsg: null,
        contextTargetElement: null,
        detailTargetMsg: null
    };

    // DOM References
    const el = {
        pageTitle: document.getElementById("page-title"),
        connPill: document.getElementById("conn-pill"),
        newChatBtn: document.getElementById("new-chat-btn"),
        clearChatBtn: document.getElementById("clear-chat-btn"),
        themeToggleBtn: document.getElementById("theme-toggle-btn"),
        logoutBtn: document.getElementById("logout-btn"),
        sessionTabs: document.getElementById("session-tabs"),
        messagesScroll: document.getElementById("messages-scroll"),
        messagesContainer: document.getElementById("messages-container"),
        thinkingRow: document.getElementById("thinking-row"),
        attachmentsBar: document.getElementById("attachments-bar"),
        staffPopup: document.getElementById("staff-popup"),
        staffPopupList: document.getElementById("staff-popup-list"),
        fileInput: document.getElementById("file-input"),
        attachBtn: document.getElementById("attach-btn"),
        chatInput: document.getElementById("chat-input"),
        sendBtn: document.getElementById("send-btn"),
        dropOverlay: document.getElementById("drop-overlay"),
        authOverlay: document.getElementById("auth-overlay"),
        authForm: document.getElementById("auth-form"),
        authTokenInput: document.getElementById("auth-token-input"),
        authError: document.getElementById("auth-error"),
        contextMenu: document.getElementById("bubble-context-menu"),
        detailModal: document.getElementById("detail-modal"),
        detailTitle: document.getElementById("detail-title"),
        detailTimestamp: document.getElementById("detail-timestamp"),
        detailBody: document.getElementById("detail-body"),
        detailCloseBtn: document.getElementById("detail-close-btn"),
        detailCopyBtn: document.getElementById("detail-copy-btn"),
        detailPrintBtn: document.getElementById("detail-print-btn"),
        detailSaveBtn: document.getElementById("detail-save-btn"),
        detailRagBtn: document.getElementById("detail-rag-btn"),
        toastContainer: document.getElementById("toast-container")
    };

    // -------------------------------------------------------------------------
    // Initialization & Theme
    // -------------------------------------------------------------------------

    function initTheme() {
        const saved = localStorage.getItem(THEME_STORAGE_KEY) || "dark";
        applyTheme(saved);
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-theme", theme);
        localStorage.setItem(THEME_STORAGE_KEY, theme);
        el.themeToggleBtn.textContent = theme === "dark" ? "☀️" : "🌙";
        if (window.mermaid) {
            try {
                window.mermaid.initialize({ startOnLoad: false, theme: theme === "dark" ? "dark" : "default" });
            } catch (_) {}
        }
    }

    async function apiFetch(path, options = {}) {
        const headers = Object.assign({}, options.headers || {});
        if (state.authToken) {
            headers["X-Nadia-Auth"] = state.authToken;
        }
        const resp = await fetch(path, Object.assign({}, options, { headers }));
        if (resp.status === 401) {
            showAuthLock(true);
            throw new Error("Unauthorized");
        }
        return resp;
    }

    async function bootstrap() {
        initTheme();
        bindEvents();

        try {
            const cfgResp = await fetch("/api/config", {
                headers: state.authToken ? { "X-Nadia-Auth": state.authToken } : {}
            });
            const cfg = await cfgResp.json();
            applyServerConfig(cfg);

            if (cfg.authRequired && !cfg.authenticated) {
                showAuthLock(false);
                return;
            }
            await initializeAuthenticatedApp();
        } catch (err) {
            setConnectionState("disconnected", "● Offline");
            showToast("Could not connect to Nadia Web Interface server.");
        }
    }

    function applyServerConfig(cfg) {
        state.config = Object.assign(state.config, cfg || {});
        document.title = state.config.title || "Nadia AI Assistant";
        el.pageTitle.textContent = state.config.title || "Nadia AI Assistant";
        el.attachBtn.classList.toggle("hidden", !state.config.allowUploads);
        el.logoutBtn.classList.toggle("hidden", !state.config.authRequired);
    }

    async function initializeAuthenticatedApp() {
        el.authOverlay.classList.add("hidden");
        await Promise.all([loadStaff(), loadSessions()]);
        connectSseStream();
    }

    function showAuthLock(showInvalidMsg) {
        if (state.eventSource) {
            state.eventSource.close();
            state.eventSource = null;
        }
        el.authError.classList.toggle("hidden", !showInvalidMsg);
        el.authOverlay.classList.remove("hidden");
        el.authTokenInput.focus();
    }

    // -------------------------------------------------------------------------
    // Staff & Sessions Loading
    // -------------------------------------------------------------------------

    async function loadStaff() {
        const resp = await apiFetch("/api/staff");
        const data = await resp.json();
        state.staffList = Array.isArray(data.staff) ? data.staff : [];
    }

    async function loadSessions() {
        const resp = await apiFetch("/api/sessions");
        const data = await resp.json();
        state.sessions = Array.isArray(data.sessions) ? data.sessions : [];
        if (!state.activeSessionId || !state.sessions.some(s => s.id === state.activeSessionId)) {
            state.activeSessionId = state.sessions.length > 0 ? state.sessions[0].id : null;
        }
        renderSessionTabs();
        if (state.activeSessionId) {
            await selectSession(state.activeSessionId);
        }
    }

    function renderSessionTabs() {
        el.sessionTabs.innerHTML = "";
        const interactiveCount = state.sessions.filter(s => !s.cronSession).length;

        for (const session of state.sessions) {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.className = "session-tab" + (session.id === state.activeSessionId ? " active" : "");
            btn.addEventListener("click", () => selectSession(session.id));

            const label = document.createElement("span");
            label.textContent = session.title + (session.thinking ? " ⏳" : "");
            btn.appendChild(label);

            if (!session.cronSession && interactiveCount > 1) {
                const closeSpan = document.createElement("span");
                closeSpan.className = "session-tab-close";
                closeSpan.textContent = "✕";
                closeSpan.title = "Close chat session";
                closeSpan.addEventListener("click", async (ev) => {
                    ev.stopPropagation();
                    await closeSession(session.id);
                });
                btn.appendChild(closeSpan);
            }
            el.sessionTabs.appendChild(btn);
        }
    }

    async function selectSession(sessionId) {
        state.activeSessionId = sessionId;
        renderSessionTabs();

        const resp = await apiFetch("/api/sessions/" + encodeURIComponent(sessionId));
        const data = await resp.json();
        const messages = Array.isArray(data.messages) ? data.messages : [];
        const isCron = Boolean(data.session && data.session.cronSession);

        el.chatInput.placeholder = isCron
            ? "Send prompt or follow-up to background scheduled session..."
            : "Ask Nadia anything, or type @ to mention a specialist...";

        el.messagesContainer.innerHTML = "";
        state.messagesById.clear();
        for (const msg of messages) {
            appendMessageBubbleDom(msg, false);
        }
        const isThinking = Boolean(data.session && data.session.thinking);
        el.thinkingRow.classList.toggle("hidden", !isThinking);
        scrollToBottom();
    }

    async function createNewChat() {
        const resp = await apiFetch("/api/sessions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({})
        });
        const data = await resp.json();
        if (data.session) {
            await loadSessions();
            await selectSession(data.session.id);
            el.chatInput.focus();
        }
    }

    async function clearCurrentSession() {
        if (!state.activeSessionId) return;
        const resp = await apiFetch("/api/sessions/" + encodeURIComponent(state.activeSessionId) + "/clear", {
            method: "POST"
        });
        const data = await resp.json();
        if (Array.isArray(data.messages)) {
            el.messagesContainer.innerHTML = "";
            state.messagesById.clear();
            for (const m of data.messages) {
                appendMessageBubbleDom(m, false);
            }
            scrollToBottom();
        }
    }

    async function closeSession(sessionId) {
        const resp = await apiFetch("/api/sessions/" + encodeURIComponent(sessionId), { method: "DELETE" });
        if (resp.ok) {
            if (state.activeSessionId === sessionId) {
                state.activeSessionId = null;
            }
            await loadSessions();
        }
    }

    // -------------------------------------------------------------------------
    // Real-Time Server-Sent Events (SSE)
    // -------------------------------------------------------------------------

    function connectSseStream() {
        if (state.eventSource) {
            state.eventSource.close();
        }
        setConnectionState("connecting", "● Connecting...");
        const url = state.authToken
            ? "/api/events?token=" + encodeURIComponent(state.authToken)
            : "/api/events";
        const es = new EventSource(url);
        state.eventSource = es;

        es.addEventListener("connected", () => {
            setConnectionState("connected", "● Live");
        });

        es.addEventListener("message_added", (ev) => {
            const msg = JSON.parse(ev.data);
            if (msg.sessionId === state.activeSessionId && !state.messagesById.has(msg.id)) {
                appendMessageBubbleDom(msg, true);
            }
        });

        es.addEventListener("thinking_changed", (ev) => {
            const data = JSON.parse(ev.data);
            const target = state.sessions.find(s => s.id === data.sessionId);
            if (target) {
                target.thinking = Boolean(data.thinking);
                renderSessionTabs();
            }
            if (data.sessionId === state.activeSessionId) {
                el.thinkingRow.classList.toggle("hidden", !data.thinking);
                if (data.thinking) scrollToBottom();
            }
        });

        es.addEventListener("sessions_updated", (ev) => {
            const data = JSON.parse(ev.data);
            if (Array.isArray(data.sessions)) {
                state.sessions = data.sessions;
                renderSessionTabs();
            }
        });

        es.addEventListener("session_cleared", (ev) => {
            const data = JSON.parse(ev.data);
            if (data.sessionId === state.activeSessionId && Array.isArray(data.messages)) {
                el.messagesContainer.innerHTML = "";
                state.messagesById.clear();
                for (const m of data.messages) {
                    appendMessageBubbleDom(m, false);
                }
            }
        });

        es.addEventListener("config_updated", (ev) => {
            applyServerConfig(JSON.parse(ev.data));
            loadSessions();
        });

        es.onerror = () => {
            setConnectionState("disconnected", "● Reconnecting...");
        };
    }

    function setConnectionState(cls, label) {
        el.connPill.className = "conn-pill " + cls;
        el.connPill.textContent = label;
    }

    // -------------------------------------------------------------------------
    // Markdown + Callout + Code + Mermaid Rendering (with Full Offline Fallback)
    // -------------------------------------------------------------------------

    function escapeHtml(str) {
        return String(str || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function renderMarkdownHtml(markdown) {
        const src = String(markdown || "");
        let rawHtml;

        if (window.marked && typeof window.marked.parse === "function") {
            const renderer = new window.marked.Renderer();
            renderer.code = function (codeObjOrText, infostring) {
                const text = typeof codeObjOrText === "object" ? codeObjOrText.text : codeObjOrText;
                const lang = (typeof codeObjOrText === "object" ? codeObjOrText.lang : infostring) || "";
                const cleanLang = lang.trim().toLowerCase();
                if (cleanLang === "mermaid") {
                    return `<div class="mermaid-container"><div class="mermaid">${escapeHtml(text)}</div></div>`;
                }
                let highlighted = escapeHtml(text);
                if (window.hljs) {
                    try {
                        highlighted = cleanLang && window.hljs.getLanguage(cleanLang)
                            ? window.hljs.highlight(text, { language: cleanLang }).value
                            : window.hljs.highlightAuto(text).value;
                    } catch (_) {}
                }
                return `<div class="code-block-wrapper"><div class="code-block-header"><span>${escapeHtml(cleanLang || "code")}</span><button type="button" class="copy-code-btn">📋 Copy</button></div><pre><code>${highlighted}</code></pre></div>`;
            };
            rawHtml = window.marked.parse(src, { gfm: true, breaks: true, renderer });
        } else {
            rawHtml = fallbackMarkdownRender(src);
        }

        return transformGithubAlerts(sanitizeRenderedHtml(rawHtml));
    }

    function fallbackMarkdownRender(md) {
        // Offline standard GFM fallback (code fences, headings, tables, lists, inline formatting)
        const fences = [];
        let working = md.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (_, lang, code) => {
            const idx = fences.length;
            fences.push(`<div class="code-block-wrapper"><div class="code-block-header"><span>${escapeHtml(lang || "code")}</span><button type="button" class="copy-code-btn">📋 Copy</button></div><pre><code>${escapeHtml(code)}</code></pre></div>`);
            return `\u0000FENCE_${idx}\u0000`;
        });

        working = escapeHtml(working);
        working = working
            .replace(/^### (.+)$/gm, "<h3>$1</h3>")
            .replace(/^## (.+)$/gm, "<h2>$1</h2>")
            .replace(/^# (.+)$/gm, "<h1>$1</h1>")
            .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
            .replace(/\*(.+?)\*/g, "<em>$1</em>")
            .replace(/~~(.+?)~~/g, "<del>$1</del>")
            .replace(/`([^`\n]+)`/g, "<code>$1</code>")
            .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');

        const paragraphs = working.split(/\n{2,}/).map(block => {
            if (block.startsWith("\u0000FENCE_") || block.startsWith("<h")) return block;
            if (block.startsWith("&gt;")) {
                return "<blockquote>" + block.replace(/^&gt;\s?/gm, "").replace(/\n/g, "<br/>") + "</blockquote>";
            }
            return "<p>" + block.replace(/\n/g, "<br/>") + "</p>";
        });

        let joined = paragraphs.join("\n");
        fences.forEach((html, idx) => {
            joined = joined.replace(`\u0000FENCE_${idx}\u0000`, html);
        });
        return joined;
    }

    function sanitizeRenderedHtml(html) {
        const tpl = document.createElement("template");
        tpl.innerHTML = html;
        tpl.content.querySelectorAll("script, iframe, object, embed, form").forEach(n => n.remove());
        tpl.content.querySelectorAll("*").forEach(node => {
            for (const attr of Array.from(node.attributes)) {
                if (attr.name.toLowerCase().startsWith("on")) {
                    node.removeAttribute(attr.name);
                }
                if ((attr.name === "href" || attr.name === "src") && /^\s*javascript:/i.test(attr.value)) {
                    node.removeAttribute(attr.name);
                }
            }
            if (node.tagName === "A") {
                node.setAttribute("target", "_blank");
                node.setAttribute("rel", "noopener noreferrer");
            }
        });
        return tpl.innerHTML;
    }

    function transformGithubAlerts(html) {
        const alerts = {
            NOTE: { cls: "callout-note", label: "ℹ️ Note" },
            TIP: { cls: "callout-tip", label: "💡 Tip" },
            IMPORTANT: { cls: "callout-important", label: "📌 Important" },
            WARNING: { cls: "callout-warning", label: "⚠️ Warning" },
            CAUTION: { cls: "callout-caution", label: "🚨 Caution" }
        };
        return html.replace(/<blockquote>\s*<p>\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*(?:<br\s*\/?>)?/gi, (_, kind) => {
            const cfg = alerts[kind.toUpperCase()];
            return `<blockquote class="${cfg.cls}"><div class="callout-title">${cfg.label}</div><p>`;
        });
    }

    function activateInteractiveBlocks(container) {
        container.querySelectorAll(".copy-code-btn").forEach(btn => {
            btn.addEventListener("click", (ev) => {
                ev.stopPropagation();
                const codeEl = btn.closest(".code-block-wrapper")?.querySelector("code");
                if (codeEl) {
                    copyText(codeEl.innerText);
                    btn.textContent = "✓ Copied";
                    setTimeout(() => { btn.textContent = "📋 Copy"; }, 1400);
                }
            });
        });

        if (window.mermaid) {
            const mermaidNodes = container.querySelectorAll(".mermaid");
            if (mermaidNodes.length > 0) {
                try {
                    window.mermaid.run({ nodes: mermaidNodes });
                } catch (_) {}
            }
        }
    }

    function appendMessageBubbleDom(msg, autoScroll) {
        state.messagesById.set(msg.id, msg);

        const row = document.createElement("div");
        row.className = "message-row " + (msg.isUser ? "user" : "assistant");
        row.dataset.messageId = msg.id;

        const block = document.createElement("div");
        block.className = "message-block";

        const timeEl = document.createElement("div");
        timeEl.className = "message-timestamp";
        timeEl.textContent = msg.timestamp || "";

        const bubble = document.createElement("div");
        bubble.className = "message-bubble " + (msg.isUser ? "user" : "assistant");

        const mdContainer = document.createElement("div");
        mdContainer.className = "markdown-body";
        mdContainer.innerHTML = renderMarkdownHtml(msg.markdown);
        activateInteractiveBlocks(mdContainer);

        bubble.appendChild(mdContainer);

        // Right-click Context Menu (parity with ChatWindowView)
        bubble.addEventListener("contextmenu", (ev) => {
            ev.preventDefault();
            openContextMenu(ev.clientX, ev.clientY, msg, mdContainer);
        });

        // Double-click opens Message Detail Modal (parity with ChatWindowView)
        bubble.addEventListener("dblclick", () => {
            openDetailModal(msg);
        });

        block.appendChild(timeEl);
        block.appendChild(bubble);
        row.appendChild(block);
        el.messagesContainer.appendChild(row);

        if (autoScroll) {
            scrollToBottom();
        }
    }

    function scrollToBottom() {
        requestAnimationFrame(() => {
            el.messagesScroll.scrollTop = el.messagesScroll.scrollHeight;
        });
    }

    // -------------------------------------------------------------------------
    // File Attachments & Message Submission
    // -------------------------------------------------------------------------

    async function uploadFiles(fileList) {
        if (!state.config.allowUploads) {
            showToast("File attachments are disabled in Web Interface Settings.");
            return;
        }
        for (const file of Array.from(fileList)) {
            if (file.size > state.config.maxUploadMb * 1024 * 1024) {
                showToast(`"${file.name}" exceeds ${state.config.maxUploadMb} MB upload limit.`);
                continue;
            }
            try {
                const resp = await apiFetch("/api/upload", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/octet-stream",
                        "X-File-Name": encodeURIComponent(file.name)
                    },
                    body: file
                });
                if (!resp.ok) {
                    const err = await resp.json().catch(() => ({}));
                    showToast(err.error || `Failed to upload ${file.name}`);
                    continue;
                }
                const uploaded = await resp.json();
                state.pendingAttachments.push(uploaded);
                renderAttachmentChips();
            } catch (_) {
                showToast(`Upload failed for ${file.name}`);
            }
        }
    }

    function renderAttachmentChips() {
        el.attachmentsBar.innerHTML = "";
        if (state.pendingAttachments.length === 0) {
            el.attachmentsBar.classList.add("hidden");
            return;
        }
        el.attachmentsBar.classList.remove("hidden");
        state.pendingAttachments.forEach((att, idx) => {
            const chip = document.createElement("div");
            chip.className = "attachment-chip";
            chip.innerHTML = `<span>📄 ${escapeHtml(att.fileName)}</span>`;
            const rm = document.createElement("button");
            rm.type = "button";
            rm.className = "chip-remove";
            rm.textContent = "✕";
            rm.title = "Remove attached file";
            rm.addEventListener("click", () => {
                state.pendingAttachments.splice(idx, 1);
                renderAttachmentChips();
            });
            chip.appendChild(rm);
            el.attachmentsBar.appendChild(chip);
        });
    }

    async function sendChatMessage() {
        const text = el.chatInput.value.trim();
        if (!text && state.pendingAttachments.length === 0) {
            return;
        }
        hideStaffPopup();

        const attachmentIds = state.pendingAttachments.map(a => a.attachmentId);
        el.chatInput.value = "";
        autoResizeTextarea();
        state.pendingAttachments = [];
        renderAttachmentChips();

        try {
            const resp = await apiFetch("/api/chat/send", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    sessionId: state.activeSessionId,
                    text,
                    attachmentIds
                })
            });
            if (resp.ok) {
                const ack = await resp.json();
                if (ack.userMessage && !state.messagesById.has(ack.userMessage.id)) {
                    appendMessageBubbleDom(ack.userMessage, true);
                }
            } else {
                const err = await resp.json().catch(() => ({}));
                showToast(err.error || "Failed to send message.");
            }
        } catch (_) {
            showToast("Network error sending message.");
        }
    }

    // -------------------------------------------------------------------------
    // @Staff Mention Autocomplete (Parity with ChatWindowView)
    // -------------------------------------------------------------------------

    function updateStaffAutocomplete() {
        const text = el.chatInput.value;
        const caret = el.chatInput.selectionStart ?? text.length;

        let atIndex = -1;
        for (let i = caret - 1; i >= 0; i--) {
            const ch = text[i];
            if (ch === "@") {
                if (i === 0 || /\s/.test(text[i - 1])) {
                    atIndex = i;
                }
                break;
            }
            if (/\s/.test(ch)) {
                break;
            }
        }

        if (atIndex === -1) {
            hideStaffPopup();
            return;
        }

        const query = text.slice(atIndex + 1, caret).trim().toLowerCase();
        state.filteredStaff = state.staffList.filter(s =>
            !query || s.name.toLowerCase().includes(query)
        );

        if (state.filteredStaff.length === 0) {
            hideStaffPopup();
            return;
        }

        state.selectedStaffIndex = 0;
        renderStaffPopup();
        el.staffPopup.classList.remove("hidden");
    }

    function renderStaffPopup() {
        el.staffPopupList.innerHTML = "";
        state.filteredStaff.forEach((staff, idx) => {
            const item = document.createElement("div");
            item.className = "staff-option" + (idx === state.selectedStaffIndex ? " selected" : "");
            item.innerHTML = `
                <span class="staff-emoji">${escapeHtml(staff.emoji || "👤")}</span>
                <div class="staff-meta">
                    <div class="staff-name">${escapeHtml(staff.name)}</div>
                    <div class="staff-subtitle">${escapeHtml(staff.subtitle || "")}</div>
                </div>
            `;
            item.addEventListener("mousedown", (ev) => {
                ev.preventDefault();
                insertStaffMention(staff.name);
            });
            el.staffPopupList.appendChild(item);
        });
    }

    function insertStaffMention(staffName) {
        const text = el.chatInput.value;
        const caret = el.chatInput.selectionStart ?? text.length;
        let atIndex = -1;
        for (let i = caret - 1; i >= 0; i--) {
            if (text[i] === "@" && (i === 0 || /\s/.test(text[i - 1]))) {
                atIndex = i;
                break;
            }
            if (/\s/.test(text[i])) break;
        }

        const before = atIndex !== -1 ? text.slice(0, atIndex) : text.slice(0, caret);
        const after = text.slice(caret);
        const mention = "@" + staffName.trim() + " ";
        el.chatInput.value = before + mention + after;
        const nextCaret = before.length + mention.length;
        el.chatInput.setSelectionRange(nextCaret, nextCaret);
        hideStaffPopup();
        el.chatInput.focus();
    }

    function hideStaffPopup() {
        el.staffPopup.classList.add("hidden");
    }

    // -------------------------------------------------------------------------
    // Context Menu, Message Detail Modal, Print, Save As & RAG Ingestion
    // -------------------------------------------------------------------------

    function getSelectedTextWithin(container) {
        const sel = window.getSelection();
        if (!sel || sel.isCollapsed || sel.rangeCount === 0) return "";
        const range = sel.getRangeAt(0);
        if (!container.contains(range.commonAncestorContainer)) return "";
        return sel.toString();
    }

    function openContextMenu(x, y, msg, mdElement) {
        state.contextTargetMsg = msg;
        state.contextTargetElement = mdElement;

        const hasSelection = getSelectedTextWithin(mdElement).length > 0;
        const copySelBtn = el.contextMenu.querySelector('[data-action="copy-selection"]');
        if (copySelBtn) copySelBtn.disabled = !hasSelection;

        el.contextMenu.classList.remove("hidden");
        const rect = el.contextMenu.getBoundingClientRect();
        const posX = Math.min(x, window.innerWidth - rect.width - 10);
        const posY = Math.min(y, window.innerHeight - rect.height - 10);
        el.contextMenu.style.left = Math.max(8, posX) + "px";
        el.contextMenu.style.top = Math.max(8, posY) + "px";
    }

    function closeContextMenu() {
        el.contextMenu.classList.add("hidden");
    }

    function openDetailModal(msg) {
        state.detailTargetMsg = msg;
        el.detailTitle.textContent = (msg.isUser ? "User Message" : `${msg.staffName || "Nadia"} Response`) + " — Markdown Detail";
        el.detailTimestamp.textContent = msg.timestamp || "";
        el.detailBody.innerHTML = renderMarkdownHtml(msg.markdown);
        activateInteractiveBlocks(el.detailBody);
        el.detailModal.classList.remove("hidden");
    }

    function printMarkdownMessage(title, timestamp, markdown) {
        const htmlContent = renderMarkdownHtml(markdown);
        const w = window.open("", "_blank", "width=860,height=700");
        if (!w) {
            showToast("Pop-up blocked—allow pop-ups to print messages.");
            return;
        }
        w.document.write(`<!DOCTYPE html><html><head><meta charset="UTF-8"/><title>${escapeHtml(title)}</title>
            <style>
                body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; padding: 32px; color: #0f172a; line-height: 1.6; }
                .meta { color: #64748b; font-size: 12px; border-bottom: 1px solid #cbd5e1; padding-bottom: 10px; margin-bottom: 20px; }
                pre { background: #f1f5f9; padding: 12px; border-radius: 6px; overflow-x: auto; }
                code { font-family: Consolas, monospace; font-size: 0.9em; }
                table { border-collapse: collapse; width: 100%; margin: 12px 0; }
                th, td { border: 1px solid #cbd5e1; padding: 6px 10px; text-align: left; }
                blockquote { border-left: 4px solid #2563eb; margin: 10px 0; padding: 6px 12px; background: #f8fafc; }
                .copy-code-btn { display: none; }
            </style></head><body>
            <h2>${escapeHtml(title)}</h2>
            <div class="meta">${escapeHtml(timestamp || "")}</div>
            <div>${htmlContent}</div>
            </body></html>`);
        w.document.close();
        w.focus();
        setTimeout(() => { w.print(); }, 250);
    }

    function downloadMarkdownFile(markdown) {
        const blob = new Blob([markdown || ""], { type: "text/markdown;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `message_detail_${Date.now()}.md`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    async function sendMessageToRag(markdown) {
        try {
            const resp = await apiFetch("/api/chat/rag", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ markdown })
            });
            const data = await resp.json();
            if (resp.ok && data.success) {
                showToast("🧠 Sent message to Nadia RAG vector database.");
            } else {
                showToast("⚠️ " + (data.error || "RAG Vector Store unavailable."));
            }
        } catch (_) {
            showToast("⚠️ Failed to ingest into RAG vector database.");
        }
    }

    async function copyText(text) {
        try {
            await navigator.clipboard.writeText(text || "");
            showToast("📋 Copied to clipboard.");
        } catch (_) {
            const ta = document.createElement("textarea");
            ta.value = text || "";
            document.body.appendChild(ta);
            ta.select();
            document.execCommand("copy");
            ta.remove();
            showToast("📋 Copied to clipboard.");
        }
    }

    function selectElementContents(element) {
        const range = document.createRange();
        range.selectNodeContents(element);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
    }

    function showToast(message) {
        const t = document.createElement("div");
        t.className = "toast";
        t.textContent = message;
        el.toastContainer.appendChild(t);
        setTimeout(() => t.remove(), 3200);
    }

    function autoResizeTextarea() {
        el.chatInput.style.height = "auto";
        el.chatInput.style.height = Math.min(180, el.chatInput.scrollHeight) + "px";
    }

    // -------------------------------------------------------------------------
    // Event Listeners
    // -------------------------------------------------------------------------

    function bindEvents() {
        el.themeToggleBtn.addEventListener("click", () => {
            const cur = document.documentElement.getAttribute("data-theme") || "dark";
            applyTheme(cur === "dark" ? "light" : "dark");
        });

        el.newChatBtn.addEventListener("click", createNewChat);
        el.clearChatBtn.addEventListener("click", clearCurrentSession);

        el.logoutBtn.addEventListener("click", () => {
            state.authToken = "";
            sessionStorage.removeItem(TOKEN_STORAGE_KEY);
            showAuthLock(false);
        });

        el.authForm.addEventListener("submit", async (ev) => {
            ev.preventDefault();
            const candidate = el.authTokenInput.value.trim();
            const resp = await fetch("/api/config", { headers: { "X-Nadia-Auth": candidate } });
            const cfg = await resp.json();
            if (cfg.authenticated) {
                state.authToken = candidate;
                sessionStorage.setItem(TOKEN_STORAGE_KEY, candidate);
                el.authTokenInput.value = "";
                await initializeAuthenticatedApp();
            } else {
                showAuthLock(true);
            }
        });

        el.attachBtn.addEventListener("click", () => el.fileInput.click());
        el.fileInput.addEventListener("change", () => {
            if (el.fileInput.files?.length) {
                uploadFiles(el.fileInput.files);
                el.fileInput.value = "";
            }
        });

        el.sendBtn.addEventListener("click", sendChatMessage);

        el.chatInput.addEventListener("input", () => {
            autoResizeTextarea();
            updateStaffAutocomplete();
        });

        el.chatInput.addEventListener("click", updateStaffAutocomplete);

        el.chatInput.addEventListener("keydown", (ev) => {
            const popupOpen = !el.staffPopup.classList.contains("hidden");
            if (popupOpen) {
                if (ev.key === "ArrowDown") {
                    ev.preventDefault();
                    state.selectedStaffIndex = Math.min(state.filteredStaff.length - 1, state.selectedStaffIndex + 1);
                    renderStaffPopup();
                    return;
                }
                if (ev.key === "ArrowUp") {
                    ev.preventDefault();
                    state.selectedStaffIndex = Math.max(0, state.selectedStaffIndex - 1);
                    renderStaffPopup();
                    return;
                }
                if (ev.key === "Enter" || ev.key === "Tab") {
                    ev.preventDefault();
                    const picked = state.filteredStaff[state.selectedStaffIndex];
                    if (picked) insertStaffMention(picked.name);
                    return;
                }
                if (ev.key === "Escape") {
                    ev.preventDefault();
                    hideStaffPopup();
                    return;
                }
            }

            if (ev.key === "Enter" && !ev.shiftKey) {
                ev.preventDefault();
                sendChatMessage();
            }
        });

        // Window-wide Drag & Drop
        let dragDepth = 0;
        window.addEventListener("dragenter", (ev) => {
            if (ev.dataTransfer?.types?.includes("Files")) {
                ev.preventDefault();
                dragDepth++;
                el.dropOverlay.classList.remove("hidden");
            }
        });
        window.addEventListener("dragover", (ev) => {
            if (ev.dataTransfer?.types?.includes("Files")) {
                ev.preventDefault();
            }
        });
        window.addEventListener("dragleave", () => {
            dragDepth = Math.max(0, dragDepth - 1);
            if (dragDepth === 0) el.dropOverlay.classList.add("hidden");
        });
        window.addEventListener("drop", (ev) => {
            ev.preventDefault();
            dragDepth = 0;
            el.dropOverlay.classList.add("hidden");
            if (ev.dataTransfer?.files?.length) {
                uploadFiles(ev.dataTransfer.files);
            }
        });

        // Context Menu Actions
        el.contextMenu.addEventListener("click", (ev) => {
            const btn = ev.target.closest("[data-action]");
            if (!btn || btn.disabled || !state.contextTargetMsg) return;
            const action = btn.dataset.action;
            const msg = state.contextTargetMsg;
            const domEl = state.contextTargetElement;
            closeContextMenu();

            if (action === "copy-selection" && domEl) {
                copyText(getSelectedTextWithin(domEl));
            } else if (action === "copy-markdown") {
                copyText(msg.markdown);
            } else if (action === "copy-plain" && domEl) {
                copyText(domEl.innerText);
            } else if (action === "select-all" && domEl) {
                selectElementContents(domEl);
            } else if (action === "print") {
                printMarkdownMessage(msg.isUser ? "User Message" : "Nadia Assistant Message", msg.timestamp, msg.markdown);
            } else if (action === "open-detail") {
                openDetailModal(msg);
            }
        });

        document.addEventListener("click", (ev) => {
            if (!el.contextMenu.contains(ev.target)) closeContextMenu();
            if (!el.staffPopup.contains(ev.target) && ev.target !== el.chatInput) hideStaffPopup();
        });

        // Detail Modal Actions
        el.detailCloseBtn.addEventListener("click", () => el.detailModal.classList.add("hidden"));
        el.detailModal.addEventListener("click", (ev) => {
            if (ev.target === el.detailModal) el.detailModal.classList.add("hidden");
        });
        el.detailCopyBtn.addEventListener("click", () => {
            const sel = getSelectedTextWithin(el.detailBody);
            copyText(sel || el.detailBody.innerText);
        });
        el.detailPrintBtn.addEventListener("click", () => {
            if (!state.detailTargetMsg) return;
            printMarkdownMessage("Message Detail", state.detailTargetMsg.timestamp, state.detailTargetMsg.markdown);
        });
        el.detailSaveBtn.addEventListener("click", () => {
            if (state.detailTargetMsg) downloadMarkdownFile(state.detailTargetMsg.markdown);
        });
        el.detailRagBtn.addEventListener("click", () => {
            if (state.detailTargetMsg) sendMessageToRag(state.detailTargetMsg.markdown);
        });

        document.addEventListener("keydown", (ev) => {
            if (ev.key === "Escape") {
                closeContextMenu();
                hideStaffPopup();
                el.detailModal.classList.add("hidden");
            }
        });
    }

    document.addEventListener("DOMContentLoaded", bootstrap);
})();

