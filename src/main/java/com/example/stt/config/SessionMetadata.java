package com.example.stt.config;

/**
 * A simple data class to hold the metadata for a WebSocket session.
 */
public class SessionMetadata {
    private final String conversationId;
    private final String participantId;

    public SessionMetadata(String conversationId, String participantId) {
        this.conversationId = conversationId;
        this.participantId = participantId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getParticipantId() {
        return participantId;
    }
}
