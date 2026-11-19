package com.nadia.plugin.web.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory conversation store backing the Nadia Web Interface.
 *
 * <p>Maintains multiple independent chat sessions (mirroring Nadia's {@code ➕ New Chat} desktop
 * windows) plus a dedicated {@code 🕒 Scheduled Tasks} session that streams live background cron
 * executions.</p>
 */
public class WebChatSessionStore {

    /** Reserved session ID for the live Scheduled Tasks / Cron stream tab. */
    public static final String CRON_SESSION_ID = "scheduled-cron-stream";

    /** Standard greeting displayed at the start of interactive chat sessions. */
    public static final String DEFAULT_GREETING =
            "Hello! I am Nadia. Ask me anything, tag a specialist with `@`, or drag files here / click 📎 to attach.";

    /** Standard banner displayed at the start of the Scheduled Tasks stream session. */
    public static final String CRON_GREETING =
            "🤖 **Scheduled Tasks & Background Cron Stream**\n\n"
            + "This tab streams background cron jobs and scheduled tasks in real time. "
            + "You can also send follow-up prompts directly in this session.";

    /** Timestamp format matching Nadia's desktop {@code ChatWindowView}. */
    public static final DateTimeFormatter MESSAGE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * Immutable representation of a single chat message bubble.
     *
     * @param id          unique message identifier
     * @param sessionId   owning chat session identifier
     * @param isUser      {@code true} for user prompts, {@code false} for assistant/system messages
     * @param staffName   responding persona name (or {@code "User"})
     * @param markdown    raw Markdown content of the message
     * @param timestamp   formatted timestamp string ({@code yyyy-MM-dd HH:mm:ss z})
     * @param attachments list of attached filenames displayed on the user bubble
     */
    public record ChatMessage(
            String id,
            String sessionId,
            boolean isUser,
            String staffName,
            String markdown,
            String timestamp,
            List<String> attachments
    ) {
    }

    /**
     * Summary metadata for a chat session tab.
     *
     * @param id           unique session identifier
     * @param title        session tab title
     * @param cronSession  {@code true} if this is the dedicated Scheduled Tasks stream tab
     * @param thinking     {@code true} while an agent turn is actively executing in this session
     * @param messageCount current number of messages in the session
     */
    public record SessionSummary(
            String id,
            String title,
            boolean cronSession,
            boolean thinking,
            int messageCount
    ) {
    }

    /** Maximum messages retained per session before oldest entries are evicted. */
    public static final int MAX_MESSAGES_PER_SESSION = 500;
    /** Maximum interactive sessions retained concurrently. */
    public static final int MAX_INTERACTIVE_SESSIONS = 50;

    private static final class SessionData {
        final String id;
        volatile String title;
        final boolean cronSession;
        volatile boolean thinking;
        final java.util.Deque<ChatMessage> messages = new java.util.ArrayDeque<>();

        SessionData(String id, String title, boolean cronSession) {
            this.id = id;
            this.title = title;
            this.cronSession = cronSession;
        }

        void addBounded(ChatMessage msg) {
            while (messages.size() >= MAX_MESSAGES_PER_SESSION) {
                messages.pollFirst();
            }
            messages.addLast(msg);
        }
    }

    private final Map<String, SessionData> sessions = new LinkedHashMap<>();
    private int chatCounter = 1;

    /**
     * Constructs a {@code WebChatSessionStore} pre-populated with a primary chat session and
     * the {@code Scheduled Tasks} cron stream session.
     */
    public WebChatSessionStore() {
        createInitialSessions();
    }

    private synchronized void createInitialSessions() {
        String primaryId = "web-session-" + UUID.randomUUID().toString().substring(0, 8);
        SessionData primary = new SessionData(primaryId, "Chat 1", false);
        primary.messages.add(newMessage(primaryId, false, "Nadia", DEFAULT_GREETING, List.of()));
        sessions.put(primaryId, primary);

        SessionData cron = new SessionData(CRON_SESSION_ID, "🕒 Scheduled Tasks", true);
        cron.messages.add(newMessage(CRON_SESSION_ID, false, "Nadia", CRON_GREETING, List.of()));
        sessions.put(CRON_SESSION_ID, cron);
    }

    /**
     * Lists all active chat sessions, optionally filtering out the Scheduled Tasks tab when disabled.
     *
     * @param includeCron whether to include the {@link #CRON_SESSION_ID} session
     * @return ordered list of {@link SessionSummary} records
     */
    public synchronized List<SessionSummary> listSessions(boolean includeCron) {
        List<SessionSummary> result = new ArrayList<>();
        for (SessionData data : sessions.values()) {
            if (!includeCron && data.cronSession) {
                continue;
            }
            result.add(toSummary(data));
        }
        return result;
    }

    /**
     * Creates and registers a new interactive chat session ({@code ➕ New Chat}).
     *
     * @param customTitle optional session title (auto-numbered when blank)
     * @return {@link SessionSummary} for the newly created session
     */
    public synchronized SessionSummary createSession(String customTitle) {
        chatCounter++;
        String id = "web-session-" + UUID.randomUUID().toString().substring(0, 8);
        String title = (customTitle != null && !customTitle.isBlank())
                ? customTitle.trim()
                : "Chat " + chatCounter;
        SessionData session = new SessionData(id, title, false);
        session.messages.add(newMessage(id, false, "Nadia", DEFAULT_GREETING, List.of()));

        // Evict oldest interactive session if capacity is reached
        long interactiveCount = sessions.values().stream().filter(s -> !s.cronSession).count();
        if (interactiveCount >= MAX_INTERACTIVE_SESSIONS) {
            String oldestKey = null;
            for (SessionData existing : sessions.values()) {
                if (!existing.cronSession) {
                    oldestKey = existing.id;
                    break;
                }
            }
            if (oldestKey != null) {
                sessions.remove(oldestKey);
            }
        }

        // Insert interactive chat tabs before the Scheduled Tasks tab so Scheduled Tasks stays anchored
        SessionData cron = sessions.remove(CRON_SESSION_ID);
        sessions.put(id, session);
        if (cron != null) {
            sessions.put(CRON_SESSION_ID, cron);
        }
        return toSummary(session);
    }

    /**
     * Retrieves the summary for a specific session ID.
     *
     * @param sessionId target session identifier
     * @return optional {@link SessionSummary}
     */
    public synchronized Optional<SessionSummary> getSessionSummary(String sessionId) {
        SessionData data = sessions.get(sessionId);
        return data != null ? Optional.of(toSummary(data)) : Optional.empty();
    }

    /**
     * Retrieves all messages in the specified session, creating the session on demand if unknown.
     *
     * @param sessionId target session identifier
     * @return immutable snapshot of {@link ChatMessage} items
     */
    public synchronized List<ChatMessage> getMessages(String sessionId) {
        SessionData data = getOrCreateSession(sessionId);
        return List.copyOf(data.messages);
    }

    /**
     * Appends a message bubble to the target session.
     *
     * @param sessionId   target session identifier
     * @param isUser      whether authored by the user
     * @param staffName   authoring staff name (or {@code "User"})
     * @param markdown    Markdown message body
     * @param attachments optional list of attached filenames
     * @return the created {@link ChatMessage}
     */
    public synchronized ChatMessage appendMessage(
            String sessionId,
            boolean isUser,
            String staffName,
            String markdown,
            List<String> attachments) {
        SessionData data = getOrCreateSession(sessionId);
        ChatMessage msg = newMessage(
                data.id,
                isUser,
                staffName,
                markdown,
                attachments != null ? List.copyOf(attachments) : Collections.emptyList()
        );
        data.addBounded(msg);
        return msg;
    }

    /**
     * Updates whether an agent turn is currently executing ("Thinking...") in a session.
     *
     * @param sessionId target session identifier
     * @param thinking  {@code true} while thinking, {@code false} when idle
     */
    public synchronized void setThinking(String sessionId, boolean thinking) {
        SessionData data = sessions.get(sessionId);
        if (data != null) {
            data.thinking = thinking;
        }
    }

    /**
     * Clears all messages in a session and restores its initial greeting bubble ({@code 🧹 Clear}).
     *
     * @param sessionId target session identifier
     * @return updated list of messages containing the restored greeting
     */
    public synchronized List<ChatMessage> clearSession(String sessionId) {
        SessionData data = getOrCreateSession(sessionId);
        data.messages.clear();
        String greeting = data.cronSession
                ? "🕒 Background Scheduled Tasks log cleared. Incoming cron job runs will continue to stream here."
                : DEFAULT_GREETING;
        data.messages.add(newMessage(data.id, false, "Nadia", greeting, List.of()));
        return List.copyOf(data.messages);
    }

    /**
     * Deletes an interactive chat session (so long as it is not the Scheduled Tasks stream and at
     * least one interactive session remains).
     *
     * @param sessionId session identifier to delete
     * @return {@code true} if deleted, {@code false} if protected or not found
     */
    public synchronized boolean deleteSession(String sessionId) {
        if (sessionId == null || CRON_SESSION_ID.equals(sessionId)) {
            return false;
        }
        long interactiveCount = sessions.values().stream().filter(s -> !s.cronSession).count();
        if (interactiveCount <= 1) {
            return false;
        }
        return sessions.remove(sessionId) != null;
    }

    private SessionData getOrCreateSession(String sessionId) {
        String effectiveId = (sessionId == null || sessionId.isBlank())
                ? sessions.keySet().iterator().next()
                : sessionId.trim();
        SessionData existing = sessions.get(effectiveId);
        if (existing != null) {
            return existing;
        }
        chatCounter++;
        SessionData created = new SessionData(effectiveId, "Chat " + chatCounter, false);
        created.messages.add(newMessage(effectiveId, false, "Nadia", DEFAULT_GREETING, List.of()));
        SessionData cron = sessions.remove(CRON_SESSION_ID);
        sessions.put(effectiveId, created);
        if (cron != null) {
            sessions.put(CRON_SESSION_ID, cron);
        }
        return created;
    }

    private static SessionSummary toSummary(SessionData data) {
        return new SessionSummary(
                data.id,
                data.title,
                data.cronSession,
                data.thinking,
                data.messages.size()
        );
    }

    private static ChatMessage newMessage(
            String sessionId,
            boolean isUser,
            String staffName,
            String markdown,
            List<String> attachments) {
        return new ChatMessage(
                "msg-" + UUID.randomUUID().toString().substring(0, 10),
                sessionId,
                isUser,
                staffName != null && !staffName.isBlank() ? staffName : (isUser ? "User" : "Nadia"),
                markdown != null ? markdown : "",
                ZonedDateTime.now().format(MESSAGE_TIME_FORMATTER),
                attachments != null ? attachments : List.of()
        );
    }
}

