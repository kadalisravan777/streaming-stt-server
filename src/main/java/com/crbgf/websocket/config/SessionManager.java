package com.crbgf.websocket.config;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the mapping between WebSocket session IDs and AudioHook metadata.
 * This class is thread-safe and is crucial for associating incoming binary
 * messages with the correct conversation and participant.
 */
public class SessionManager {

    // A thread-safe map to store the metadata for each active session.
    private final ConcurrentHashMap<String, SessionMetadata> sessionMap = new ConcurrentHashMap<>();

    /**
     * Stores the conversation and participant IDs for a given WebSocket session.
     * @param sessionId The unique ID of the WebSocket session.
     * @param conversationId The ID of the Genesys conversation.
     * @param participantId The ID of the Genesys participant.
     */
    public void storeSessionMetadata(String sessionId, String conversationId, String participantId, String agentId) {
        sessionMap.put(sessionId, new SessionMetadata(conversationId, participantId, agentId));
    }

    /**
     * Retrieves the metadata for a given WebSocket session.
     * @param sessionId The unique ID of the WebSocket session.
     * @return The SessionMetadata object, or null if no data is found.
     */
    public SessionMetadata getMetadata(String sessionId) {
        return sessionMap.get(sessionId);
    }

    /**
     * Removes the metadata for a closed or errored session.
     * @param sessionId The unique ID of the WebSocket session to remove.
     */
    public void removeSessionMetadata(String sessionId) {
        sessionMap.remove(sessionId);
    }

    /**
     * Clears all session data from the map.
     */
    public void clearAll() {
        sessionMap.clear();
    }
}