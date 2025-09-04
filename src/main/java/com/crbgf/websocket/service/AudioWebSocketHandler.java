package com.crbgf.websocket.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import javax.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.crbgf.websocket.beans.CloseMessage;
import com.crbgf.websocket.beans.ClosedMessage;
import com.crbgf.websocket.beans.MediaParameter;
import com.crbgf.websocket.beans.OpenMessage;
import com.crbgf.websocket.beans.OpenedMessage;
import com.crbgf.websocket.beans.OpenedParameters;
import com.crbgf.websocket.beans.PauseMessage;
import com.crbgf.websocket.beans.PausedMessage;
import com.crbgf.websocket.beans.PingMessage;
import com.crbgf.websocket.beans.PongMessage;
import com.crbgf.websocket.beans.RMQMetaDetails;
import com.crbgf.websocket.beans.ResumeMessage;
import com.crbgf.websocket.beans.ResumedMessage;
import com.crbgf.websocket.beans.UpdateMessage;
import com.crbgf.websocket.config.SessionManager;
import com.crbgf.websocket.config.SessionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import jakarta.websocket.CloseReason;


/**
 * The Class AudioWebSocketHandler.
 */
@Component
public class AudioWebSocketHandler implements WebSocketHandler {

	/** The Constant log. */
	private static final Logger log = LoggerFactory.getLogger(AudioWebSocketHandler.class);

	/** The Constant CLIENT_SECRET. */
	private static final String CLIENT_SECRET = "TXlTdXBlclNlY3JldEtleVRlbGxOby0xITJAMyM0JDU=";
	
	/** The Constant RABBITMQ_EXCHANGE_NAME. */
	private static final String RABBITMQ_EXCHANGE_NAME = "audio_chunks";
	
	/** The supported languages. */
	private final List<String> supportedLanguages = Arrays.asList("en-US", "en-GB", "ja-JP", "zh-CN", "zh-SG");

	/** The session manager. */
	// New class to manage the sessions and their metadata.
	private final SessionManager sessionManager = new SessionManager();

	/** The rabbit connection. */
	private Connection rabbitConnection;
	
	/** The rabbit channel. */
	private Channel rabbitChannel;

	/** The opened msg from server. */
	private OpenedMessage openedMsgFromServer;
	
	/** The open msg from client. */
	private OpenMessage openMsgFromClient;
	
	/** The closed msg from server. */
	private ClosedMessage closedMsgFromServer;
	
	/** The closed msg from client. */
	private ClosedMessage closedMsgFromClient;
	
	/** The close msg from client. */
	private CloseMessage closeMsgFromClient;
	
	/** The close msg from server. */
	private CloseMessage closeMsgFromServer;
	
	/** The paused message from server. */
	private PausedMessage pausedMessageFromServer;
	
	/** The paused message from client. */
	private PausedMessage pausedMessageFromClient;
	
	/** The pause message from server. */
	private PauseMessage pauseMessageFromServer;
	
	/** The pause message from client. */
	private PauseMessage pauseMessageFromClient;
	
	/** The resumed message from server. */
	private ResumedMessage resumedMessageFromServer;
	
	/** The resumed message from client. */
	private ResumedMessage resumedMessageFromClient;
	
	/** The resume message from client. */
	private ResumeMessage resumeMessageFromClient;
	
	/** The pong msg from server. */
	private PongMessage pongMsgFromServer;
	
	/** The ping msg from client. */
	private PingMessage pingMsgFromClient;
	
	/** The mapper. */
	ObjectMapper mapper = new ObjectMapper();
	
	/** The update message from client. */
	private UpdateMessage updateMessageFromClient;

	/** The active agents. */
	@Value("#{'${genesys.active.agents}'.split(',')}")
	private List<String> activeAgents;
	
	
	/** The meta data APIURI. */
	@Value("${rabbit.metadata.api.url}")
	private String metaDataAPIURI;
	
	/** The rest template. */
	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private TokenService tokenService;
	
	@Value("${genesys.api.base-url}")
	private String baseUrl;

	@Value("${genesys.api.participant-url-template}")
	private String participantUrlTemplate;



	/**
	 * Gets the header.
	 *
	 * @param headers the headers
	 * @param name the name
	 * @return the header
	 */
	private String getHeader(Map<String, List<String>> headers, String name) {
		List<String> values = headers.getOrDefault(name, Collections.emptyList());
		return values.isEmpty() ? null : values.get(0);
	}

	/**
	 * Validate signature.
	 *
	 * @param headers the headers
	 * @return true, if successful
	 * @throws NoSuchAlgorithmException the no such algorithm exception
	 * @throws InvalidKeyException the invalid key exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private boolean validateSignature(Map<String, List<String>> headers)
			throws NoSuchAlgorithmException, InvalidKeyException, IOException {

		String signatureInputHeader = getHeader(headers, "Signature-Input");
		String signatureHeader = getHeader(headers, "Signature");

		// Basic validation: Check for required headers
		if (signatureInputHeader == null || signatureHeader == null) {
			log.error("Validation failed: Signature-Input or Signature header missing.");
			return false;
		}

		// 1. Reconstruct the signing string based on the provided headers
		// The order and format of these lines are critical and must match the client
		// exactly.
		String signingString = "\"@request-target\": get /api/v1/voicebiometrics/ws\n" + "\"@authority\": "
				+ getHeader(headers, "Host") + "\n" + "\"audiohook-organization-id\": "
				+ getHeader(headers, "Audiohook-Organization-Id") + "\n" + "\"audiohook-correlation-id\": "
				+ getHeader(headers, "Audiohook-Correlation-Id") + "\n" + "\"audiohook-session-id\": "
				+ getHeader(headers, "Audiohook-Session-Id") + "\n" + "\"x-api-key\": "
				+ getHeader(headers, "X-API-KEY");

		// 2. Append the signature parameters line
		// We only need the part before the first semicolon
		String signatureParams = signatureInputHeader.split(";")[0];
		signingString += "\n@signature-params: " + signatureParams;

		log.info("Reconstructed Signing String:\n" + signingString); // For debugging purposes

		// 3. Compute the HMAC-SHA256 signature
		Mac hmacSha256 = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKey = new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSha256");
		hmacSha256.init(secretKey);

		byte[] hashBytes = hmacSha256.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
		String computedSignature = Base64.getEncoder().encodeToString(hashBytes);

		// 4. Extract the signature from the header
		String requestSignature = signatureHeader.split(":")[1]; // Get the part after 'sig1=:'

		// 5. Compare the signatures
		return computedSignature.equals(requestSignature);

	}

	/**
	 * After connection established.
	 *
	 * @param session the session
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws IOException {
		log.info("🔗 WebSocket connected: {}", session.getId());

		Map<String, List<String>> headers = session.getHandshakeHeaders();

		try {
			boolean isSignatureValid = validateSignature(headers);
			if (!isSignatureValid) {
				log.info("Signature validation failed. Rejecting connection.");
				session.close(new CloseStatus(CloseReason.CloseCodes.VIOLATED_POLICY.getCode(), "Invalid signature."));
				return;
			}

		} catch (Exception e) {
			log.error("Error during signature validation: " + e.getMessage());
			session.close(
					new CloseStatus(CloseReason.CloseCodes.UNEXPECTED_CONDITION.getCode(), "Internal server error."));
			return;
		}

		log.info("Connection established and signature validated successfully.");
 	}

	/**
	 * Handle message.
	 *
	 * @param session the session
	 * @param message the message
	 * @throws Exception the exception
	 */
	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
		if (message instanceof TextMessage) {
			handleTextMessage(session, (TextMessage) message);
		} else if (message instanceof BinaryMessage) {
			handleBinaryMessage(session, (BinaryMessage) message);
		}
	}

	/**
	 * Handle text message.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws Exception {

		JSONObject response = new JSONObject(message.getPayload().toString());
		String type = response.optString("type");
		switch (type) {
		case "open" -> handleOpen(ws, message);
		case "ping" -> handlePing(ws, message);
		case "close" -> handleClose(ws, message);
		case "closed" -> handleClosed(ws, message);
		case "pause" -> handlePause(ws, message);
		case "paused" -> handlePaused(ws, message);
		case "resume" -> handleResume(ws, message);
		case "resumed" -> handleResumed(ws, message);
		case "update" -> handleUpdate(ws, message);
		default -> log.warn("Unknown message type: {}", type);
		}
	}

	/**
	 * Handle resumed.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handleResumed(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("Resumed Event received for sessionId={}", ws.getAttributes().get("sessionId"));

		resumedMessageFromClient = mapper.readValue(message.getPayload(), ResumedMessage.class);
		String jsonString = mapper.writeValueAsString(resumedMessageFromClient);

		log.info("Closed JSON: " + jsonString);
	}

	/**
	 * Handle closed.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handleClosed(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("Closed Event received for sessionId={}", ws.getAttributes().get("sessionId"));

		closedMsgFromClient = mapper.readValue(message.getPayload(), ClosedMessage.class);
		String jsonString = mapper.writeValueAsString(closedMsgFromClient);

		log.info("Closed JSON: " + jsonString);
	}

	/**
	 * Handle paused.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handlePaused(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("⏸️ Paused Event received for sessionId={}", ws.getAttributes().get("sessionId"));

		pausedMessageFromClient = mapper.readValue(message.getPayload(), PausedMessage.class);
		String jsonString = mapper.writeValueAsString(pausedMessageFromClient);

		log.info("Paused JSON: " + jsonString);

	}

	/**
	 * Handle update.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handleUpdate(WebSocketSession ws, TextMessage message) throws Exception {
		updateMessageFromClient = mapper.readValue(message.getPayload(), UpdateMessage.class);
		log.info("Update for sessionId={}", ws.getAttributes().get("sessionId"));

		String jsonString;
		if (updateMessageFromClient != null && updateMessageFromClient.getParameters() != null
				&& updateMessageFromClient.getParameters().getLanguage() != null) {
			String language = updateMessageFromClient.getParameters().getLanguage();
			if (supportedLanguages.contains(language)) {
				pauseMessageFromServer = new PauseMessage();
				pauseMessageFromServer.setVersion("2");
				pauseMessageFromServer.setType("pause");
				pauseMessageFromServer.setSeq(updateMessageFromClient.getServerseq() + 1);
				pauseMessageFromServer.setClientseq(updateMessageFromClient.getSeq());
				pauseMessageFromServer.setId(updateMessageFromClient.getId());

				jsonString = mapper.writeValueAsString(pauseMessageFromServer);
				log.info("Sending Pause JSON: " + jsonString);

			} else {
				closeMsgFromServer = new CloseMessage();
				closeMsgFromServer.setVersion("2");
				closeMsgFromServer.setType("close");
				closeMsgFromServer.setSeq(updateMessageFromClient.getServerseq() + 1);
				closeMsgFromServer.setClientseq(updateMessageFromClient.getSeq());
				closeMsgFromServer.setId(updateMessageFromClient.getId());

				jsonString = mapper.writeValueAsString(closeMsgFromServer);
				log.info("Sending Close JSON: " + jsonString);

			}
			ws.sendMessage(new TextMessage(jsonString));
		}

	}

	/**
	 * Handle ping.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handlePing(WebSocketSession ws, TextMessage message) throws Exception {

		pingMsgFromClient = mapper.readValue(message.getPayload(), PingMessage.class);

		log.info("⏸️ Ping for sessionId={}", ws.getAttributes().get("sessionId"));

		try {
			pongMsgFromServer = new PongMessage();
			pongMsgFromServer.setId(pingMsgFromClient.getId());
			pongMsgFromServer.setVersion(pingMsgFromClient.getVersion());
			pongMsgFromServer.setSeq(pingMsgFromClient.getServerseq() + 1);
			pongMsgFromServer.setClientseq(pingMsgFromClient.getSeq());
			pongMsgFromServer.setType("pong");

			String jsonString = mapper.writeValueAsString(pongMsgFromServer);

			log.info("Sending Pong JSON: " + jsonString);

			ws.sendMessage(new TextMessage(jsonString));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handle pause.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handlePause(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("⏸️ Pause requested for sessionId={}", ws.getAttributes().get("sessionId"));

		pauseMessageFromClient = mapper.readValue(message.getPayload(), PauseMessage.class);

		try {
			pausedMessageFromServer = new PausedMessage();
			pausedMessageFromServer.setId(pauseMessageFromClient.getId());
			pausedMessageFromServer.setVersion(pauseMessageFromClient.getVersion());
			pausedMessageFromServer.setSeq(pauseMessageFromClient.getServerseq() + 1);
			pausedMessageFromServer.setClientseq(pauseMessageFromClient.getSeq());
			pausedMessageFromServer.setPosition("");
			pausedMessageFromServer.setType("paused");

			String jsonString = mapper.writeValueAsString(pausedMessageFromServer);
			log.info("Sending Paused JSON: " + jsonString);

			ws.sendMessage(new TextMessage(jsonString));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handle resume.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handleResume(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("Resume requested for sessionId={}", ws.getAttributes().get("sessionId"));
		resumeMessageFromClient = mapper.readValue(message.getPayload(), ResumeMessage.class);

		try {
			resumedMessageFromServer = new ResumedMessage();
			resumedMessageFromServer.setId(resumeMessageFromClient.getId());
			resumedMessageFromServer.setVersion(resumeMessageFromClient.getVersion());
			resumedMessageFromServer.setSeq(resumeMessageFromClient.getServerseq() + 1);
			resumedMessageFromServer.setClientseq(resumeMessageFromClient.getSeq());
			resumedMessageFromServer.setPosition("");
			resumedMessageFromServer.setType("resumed");

			String jsonString = mapper.writeValueAsString(resumedMessageFromServer);
			log.info("Sending Resumed JSON: " + jsonString);

			ws.sendMessage(new TextMessage(jsonString));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handle open.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handleOpen(WebSocketSession ws, TextMessage message) throws Exception {

		try {
			openMsgFromClient = mapper.readValue(message.getPayload(), OpenMessage.class);
			log.info(openMsgFromClient.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		String sessionId = openMsgFromClient.getId();
		String conversationId = openMsgFromClient.getParameters().getConversationId();
		String participantId = openMsgFromClient.getParameters().getParticipant().getId();
		log.info("📡 Received 'open' event for sessionId={}, participantId={}", sessionId, participantId);
		log.info("Active Agent List:" + activeAgents.toString());
		
		String agentId = fetchAgentDetails(conversationId,participantId);
		if (!activeAgents.contains(agentId)) {
			ws.close(new CloseStatus(CloseReason.CloseCodes.VIOLATED_POLICY.getCode(), "Agent not active"));
			return;
		}
		log.info("Participant Id Verified" + participantId);
		
		// ⭐ Core change: Store the conversation and participant IDs for this session
		sessionManager.storeSessionMetadata(ws.getId(), conversationId, participantId, agentId);

		String jsonString = prepareOpenedMessage(sessionId);

		ws.sendMessage(new TextMessage(jsonString));

	}

	/**
	 * **************************************** This method is used to fetch the
	 * agentId from the genesis API based on the participant id.
	 *
	 * @param conversationId the conversation id
	 * @param participantId  the participant id
	 * @return ****************************************
	 */
	public String fetchAgentDetails(String conversationId, String participantId) {
	    String path = participantUrlTemplate
	        .replace("{conversationId}", conversationId)
	        .replace("{participantId}", participantId);

	    String url = baseUrl + path;
	    return callParticipantApi(url, false);
	}

	private String callParticipantApi(String url, boolean retrying) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenService.getAccessToken());
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));

		HttpEntity<Void> entity = new HttpEntity<>(headers);

		ResponseEntity<String> response = null;
		try {
			response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
		} catch (HttpClientErrorException.Unauthorized ex) {
			if (!retrying) {
				System.out.println("Token expired. Fetching new token...");
				tokenService.refreshToken();
				return callParticipantApi(url, true); // retry once
			} else {
				throw new RuntimeException("Retried but still unauthorized.");
			}
		}
		ObjectMapper objectMapper = new ObjectMapper();
		if (response.getStatusCode().is2xxSuccessful()) {
			try {
				JsonNode json = objectMapper.readTree(response.getBody());
				return json.path("userId").asText(); // 🎯 agentId
			} catch (Exception e) {
				throw new RuntimeException("Failed to parse JSON", e);
			}
		} else {
			throw new RuntimeException("API Error: " + response.getStatusCode());
		}
	}


	/**
	 * Prepare opened message.
	 *
	 * @param sessionId the session id
	 * @return the string
	 * @throws JsonProcessingException the json processing exception
	 */
	private String prepareOpenedMessage(String sessionId) throws JsonProcessingException {
		OpenedParameters opendParameter = new OpenedParameters();
		openedMsgFromServer = new OpenedMessage();
		openedMsgFromServer.setVersion("2");
		openedMsgFromServer.setType("opened");
		openedMsgFromServer.setSeq(openMsgFromClient.getServerseq() + 1);
		openedMsgFromServer.setClientseq(openMsgFromClient.getSeq());
		openedMsgFromServer.setId(sessionId);
		opendParameter.setStartPaused(false);
		List<MediaParameter> mediaParameter = new ArrayList<>();
		MediaParameter mP = new MediaParameter();
		mP.setType("audio");
		mP.setFormat("PCMU");
		mP.setChannels(Arrays.asList("external", "internal"));
		mP.setRate(8000);
		mediaParameter.add(mP);
		opendParameter.setMedia(mediaParameter);
		openedMsgFromServer.setParameters(opendParameter);
		if (openMsgFromClient.getParameters().getSupportedLanguages() != null
				&& openMsgFromClient.getParameters().getSupportedLanguages()) {
			openedMsgFromServer.getParameters().setSupportedLanguages(supportedLanguages);
		}

		String jsonString = mapper.writeValueAsString(openedMsgFromServer);
		log.info("Sending Opened JSON: " + jsonString);
		return jsonString;
	}

	/**
	 * Handle close.
	 *
	 * @param ws the ws
	 * @param message the message
	 * @throws Exception the exception
	 */
	private void handleClose(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("📴 Received 'close' event for session: {}", ws.getId());

		closeMsgFromClient = mapper.readValue(message.getPayload(), CloseMessage.class);

		closedMsgFromServer = new ClosedMessage();
		closedMsgFromServer.setType("closed");
		closedMsgFromServer.setVersion(closeMsgFromClient.getVersion());
		closedMsgFromServer.setClientseq(closeMsgFromClient.getSeq());
		closedMsgFromServer.setSeq(closeMsgFromClient.getServerseq() + 1);
		closedMsgFromServer.setId(closeMsgFromClient.getId());

		String jsonString = mapper.writeValueAsString(closedMsgFromServer);

		log.info("Sending Closed JSON: {}", jsonString);

		ws.sendMessage(new TextMessage(jsonString));
		stopSession(ws);
	}

	/**
	 * Handle binary message.
	 *
	 * @param session the session
	 * @param message the message
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {

		SessionMetadata metadata = sessionManager.getMetadata(session.getId());

		if (metadata == null) {
			log.warn("❌ Received binary audio for a session without metadata. Discarding message.");
			return;
		}

		log.debug("🔊 Received binary audio: {} bytes", message.getPayloadLength());
		byte[] audio = message.getPayload().array();

		JSONObject audioMessage = new JSONObject();
		audioMessage.put("sessionId", session.getId());
		audioMessage.put("conversationId", metadata.getConversationId());
		audioMessage.put("participantId", metadata.getParticipantId());
		audioMessage.put("audioChunk", Base64.getEncoder().encodeToString(audio));

		String routingKey = metadata.getConversationId() + "." + metadata.getParticipantId();
		
		publishRMQDetailsTOAPI(metadata.getParticipantId(), metadata.getAgentId());

		rabbitChannel.basicPublish(RABBITMQ_EXCHANGE_NAME, routingKey, null,
				audioMessage.toString().getBytes(StandardCharsets.UTF_8));
		log.info("Published " + audio.length + " bytes to RabbitMQ with routing key: " + routingKey);

	}

	/**
	 * ************************************************
	 * This method will be used to pass on the agent
	 * id, exchange details, participent id to internal 
	 * API.
	 *
	 * @param participantId the participant id
	 * @param agentId ************************************************
	 */
	private void publishRMQDetailsTOAPI(String participantId, String agentId) {
		try {
			HttpHeaders headers = new HttpHeaders();
			HttpEntity<RMQMetaDetails> request = new HttpEntity<>(new RMQMetaDetails(agentId, participantId, RABBITMQ_EXCHANGE_NAME), headers);
			restTemplate.postForObject(metaDataAPIURI, request, String.class);
		} catch (Exception e) {
			log.error("error occured while connecting to metaRMQ api");
		}
		
	}

	/**
	 * Handle transport error.
	 *
	 * @param session the session
	 * @param exception the exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
		log.error("❌ Transport error on session {}: {}", session.getId(), exception.getMessage(), exception);
		stopSession(session);
	}

	/**
	 * After connection closed.
	 *
	 * @param session the session
	 * @param closeStatus the close status
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws IOException {
		log.info("🔌 WebSocket closed: {}, reason={}", session.getId(), closeStatus.getReason());
		stopSession(session);
	}

	/**
	 * Supports partial messages.
	 *
	 * @return true, if successful
	 */
	@Override
	public boolean supportsPartialMessages() {
		return false;
	}

	/**
	 * Stop session.
	 *
	 * @param session the session
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void stopSession(WebSocketSession session) throws IOException {
		log.info("🛑 Transcription session stopped for WebSocket {}", session.getId());
		sessionManager.removeSessionMetadata(session.getId());
		session.close(new CloseStatus(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), "Closing session"));
	}

	/**
	 * Shutdown.
	 */
	@PreDestroy
	public void shutdown() {
		log.info("🔻 Shutting down all active WebSocket transcription sessions...");
		sessionManager.clearAll();
		try {
			if (rabbitChannel != null && rabbitChannel.isOpen()) {
				rabbitChannel.close();
			}
			if (rabbitConnection != null && rabbitConnection.isOpen()) {
				rabbitConnection.close();
			}
		} catch (IOException | TimeoutException e) {
			log.error("Error shutting down RabbitMQ connection: {}", e.getMessage(), e);
		}
	}
}