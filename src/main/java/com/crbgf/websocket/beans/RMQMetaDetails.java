package com.crbgf.websocket.beans;

public class RMQMetaDetails {

	private String agentId;
	private String participantId;
	private String exchangeName;

	public RMQMetaDetails(String agentId, String participantId, String exchangeName) {
		super();
		this.agentId = agentId;
		this.participantId = participantId;
		this.exchangeName = exchangeName;
	}

}
