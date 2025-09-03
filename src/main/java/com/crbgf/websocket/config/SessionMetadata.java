package com.crbgf.websocket.config;

/**
 * A simple data class to hold the metadata for a WebSocket session.
 */
public class SessionMetadata {
	private final String conversationId;
	private final String participantId;
	private final String agentId;

	public SessionMetadata(String conversationId, String participantId, String agentId) {
		this.conversationId = conversationId;
		this.participantId = participantId;
		this.agentId = agentId;
	}

	public String getConversationId() {
		return conversationId;
	}

	public String getParticipantId() {
		return participantId;
	}

	public String getAgentId() {
		return agentId;
	}
}
