# Web Interface Plugin (`web-interface`)

Browser-accessible **Web Chat Interface** PF4J plugin for the **Nadia Cognitive Agent Platform**.

## Features

- **Full Desktop `ChatWindowView` Parity in Any Browser**:
  - **Multi-Session Tabs**: Open multiple independent chat sessions (`➕ New Chat`), switch or close tabs, clear history (`🧹 Clear`), and monitor live background jobs in the dedicated **`🕒 Scheduled Tasks`** cron stream tab.
  - **`@Staff` Specialist Autocomplete & Multi-Agent Routing**: Type `@` to open the floating specialist selector with domain emojis and descriptions; supports `↑`/`↓`/`Enter`/`Tab`/`Escape` and streams multi-agent handoff turns live via Server-Sent Events (SSE).
  - **File Attachments & Drag-and-Drop (`📎`)**: Drag files anywhere onto the browser window or click `📎` to attach code, Markdown, PDFs, GEDCOM files, or media with removable chips.
  - **Rich Markdown, Code, Tables, Callouts & Mermaid**: Renders GitHub-Flavored Markdown, syntax-highlighted code blocks with 1-click `📋 Copy`, GFM tables, GitHub alert blocks (`[!NOTE]`, `[!TIP]`, `[!IMPORTANT]`, `[!WARNING]`, `[!CAUTION]`), and inline `mermaid` diagrams (with a built-in offline fallback renderer).
  - **Bubble Context Menu & Detail Modal**: Right-click any message bubble for *Copy Selection*, *Copy Message*, *Copy Plain Text*, *Select All*, *Print Message...*, or *Open in Detail View* (also on double-click), complete with **Save As (`.md`)** and **`🧠 Send to RAG`** vector database ingestion.
  - **Dark & Light Themes** + optional **Access Token Authentication** lock screen.
- **Dedicated `🌐 Web Interface` Settings Tab in Nadia**:
  - Live server status, connected browser tab counter, LAN URL discovery, and **Start / Stop / Restart / Open in Browser / Copy URL** controls.
  - Configurable startup auto-launch, bind address (`127.0.0.1` Localhost vs. `0.0.0.0` LAN), HTTP port (default `8088`), page title, Access Token generator, upload toggle/size limit, and Cron stream toggle.

## Configuration Properties (`~/.nadia/nadia.properties`)

| Property Key | Default | Description |
| :--- | :--- | :--- |
| `nadia.prop.web.enabled` | `true` | Automatically start the Web Interface HTTP server on plugin startup |
| `nadia.prop.web.host` | `127.0.0.1` | Bind address (`127.0.0.1` localhost or `0.0.0.0` all interfaces) |
| `nadia.prop.web.port` | `8088` | HTTP server TCP port (`1024`–`65535`) |
| `nadia.prop.web.authToken` | *(empty)* | Optional access token required by browser clients |
| `nadia.prop.web.title` | `Nadia AI Assistant` | Browser window and top header title |
| `nadia.prop.web.allowUploads` | `true` | Enable `📎` file attachments and drag-and-drop uploads |
| `nadia.prop.web.maxUploadMb` | `25` | Maximum upload size per file in MB (`1`–`200`) |
| `nadia.prop.web.enableCronStream` | `true` | Stream background scheduled cron executions live to browsers |

