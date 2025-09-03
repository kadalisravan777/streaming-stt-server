package com.example.stt.service;

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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.stt.config.SessionManager;
import com.example.stt.config.SessionMetadata;
import com.example.stt.dto.CloseMessage;
import com.example.stt.dto.ClosedMessage;
import com.example.stt.dto.MediaParameter;
import com.example.stt.dto.OpenMessage;
import com.example.stt.dto.OpenedMessage;
import com.example.stt.dto.OpenedParameters;
import com.example.stt.dto.PauseMessage;
import com.example.stt.dto.PausedMessage;
import com.example.stt.dto.PingMessage;
import com.example.stt.dto.PongMessage;
import com.example.stt.dto.ResumeMessage;
import com.example.stt.dto.ResumedMessage;
import com.example.stt.dto.UpdateMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import jakarta.websocket.CloseReason;

@Component
public class AudioWebSocketHandler implements WebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(AudioWebSocketHandler.class);

	private static final String CLIENT_SECRET = "TXlTdXBlclNlY3JldEtleVRlbGxOby0xITJAMyM0JDU=";
	private static final String RABBITMQ_EXCHANGE_NAME = "audio_exchange";
	private final List<String> supportedLanguages = Arrays.asList("en-US", "en-GB", "ja-JP", "zh-CN", "zh-SG");

	// New class to manage the sessions and their metadata.
	private final SessionManager sessionManager = new SessionManager();

	private Connection rabbitConnection;
	private Channel rabbitChannel;

	private OpenedMessage openedMsgFromServer;
	private OpenMessage openMsgFromClient;
	private ClosedMessage closedMsgFromServer;
	private ClosedMessage closedMsgFromClient;
	private CloseMessage closeMsgFromClient;
	private CloseMessage closeMsgFromServer;
	private PausedMessage pausedMessageFromServer;
	private PausedMessage pausedMessageFromClient;
	private PauseMessage pauseMessageFromServer;
	private PauseMessage pauseMessageFromClient;
	private ResumedMessage resumedMessageFromServer;
	private ResumedMessage resumedMessageFromClient;
	private ResumeMessage resumeMessageFromServer;
	private ResumeMessage resumeMessageFromClient;
	private PongMessage pongMsgFromServer;
	private PingMessage pingMsgFromClient;
	ObjectMapper mapper = new ObjectMapper();
	private UpdateMessage updateMessageFromClient;

	@Value("#{'${genesys.active.agents}'.split(',')}")
	private List<String> activeAgents;
	
	@Autowired
	private RestTemplate restTemplate;

	private String getHeader(Map<String, List<String>> headers, String name) {
		List<String> values = headers.getOrDefault(name, Collections.emptyList());
		return values.isEmpty() ? null : values.get(0);
	}

	private boolean validateSignature(Map<String, List<String>> headers)
			throws NoSuchAlgorithmException, InvalidKeyException, IOException {

		String signatureInputHeader = getHeader(headers, "Signature-Input");
		String signatureHeader = getHeader(headers, "Signature");

		// Basic validation: Check for required headers
		if (signatureInputHeader == null || signatureHeader == null) {
			System.err.println("Validation failed: Signature-Input or Signature header missing.");
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

		System.out.println("Reconstructed Signing String:\n" + signingString); // For debugging purposes

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

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws IOException {
		log.info("🔗 WebSocket connected: {}", session.getId());

		Map<String, List<String>> headers = session.getHandshakeHeaders();

		try {
			boolean isSignatureValid = validateSignature(headers);
			if (!isSignatureValid) {
				System.out.println("Signature validation failed. Rejecting connection.");
				session.close(new CloseStatus(CloseReason.CloseCodes.VIOLATED_POLICY.getCode(), "Invalid signature."));
				return;
			}

		} catch (Exception e) {
			System.err.println("Error during signature validation: " + e.getMessage());
			session.close(
					new CloseStatus(CloseReason.CloseCodes.UNEXPECTED_CONDITION.getCode(), "Internal server error."));
			return;
		}

		System.out.println("Connection established and signature validated successfully.");

//		 try {
//			connectToMQ(session, headers);
//		} catch (KeyManagementException | NoSuchAlgorithmException | IOException | URISyntaxException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

	}

//	private void connectToMQ(WebSocketSession session, Map<String, List<String>> headers)
//			throws IOException, KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
//		try {
//			ConnectionFactory factory = new ConnectionFactory();
//			factory.setHost("172.16.32.202");
//			factory.setUsername("mquser");
//			factory.setPassword("rabbitmq");
//			factory.setUri("");
//
//			this.rabbitConnection = factory.newConnection();
//			this.rabbitChannel = rabbitConnection.createChannel();
//
//			this.rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "direct", true);
//
//			String sessionId = headers.get("Audiohook-Session-Id").get(0);
//			session.getAttributes().put("sessionId", sessionId);
//
//			System.out.println("RabbitMQ connection and channel established.");
//		} catch (IOException | TimeoutException e) {
//			System.err.println("Error connecting to RabbitMQ: " + e.getMessage());
//			session.close(new CloseStatus(CloseReason.CloseCodes.UNEXPECTED_CONDITION.getCode(),
//					"RabbitMQ connection error."));
//		}
//	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
		if (message instanceof TextMessage) {
			handleTextMessage(session, (TextMessage) message);
		} else if (message instanceof BinaryMessage) {
			handleBinaryMessage(session, (BinaryMessage) message);
		}
	}

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

	private void handleResumed(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("Resumed Event received for sessionId={}", ws.getAttributes().get("sessionId"));

		resumedMessageFromClient = mapper.readValue(message.getPayload(), ResumedMessage.class);
		String jsonString = mapper.writeValueAsString(resumedMessageFromClient);

		log.info("Closed JSON: " + jsonString);
	}

	private void handleClosed(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("Closed Event received for sessionId={}", ws.getAttributes().get("sessionId"));

		closedMsgFromClient = mapper.readValue(message.getPayload(), ClosedMessage.class);
		String jsonString = mapper.writeValueAsString(closedMsgFromClient);

		log.info("Closed JSON: " + jsonString);
	}

	private void handlePaused(WebSocketSession ws, TextMessage message) throws Exception {
		log.info("⏸️ Paused Event received for sessionId={}", ws.getAttributes().get("sessionId"));

		pausedMessageFromClient = mapper.readValue(message.getPayload(), PausedMessage.class);
		String jsonString = mapper.writeValueAsString(pausedMessageFromClient);

		log.info("Paused JSON: " + jsonString);

	}

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

			System.out.println("Sending Pong JSON: " + jsonString);

			ws.sendMessage(new TextMessage(jsonString));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

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
			System.out.println("Sending Paused JSON: " + jsonString);

			ws.sendMessage(new TextMessage(jsonString));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

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
			System.out.println("Sending Resumed JSON: " + jsonString);

			ws.sendMessage(new TextMessage(jsonString));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleOpen(WebSocketSession ws, TextMessage message) throws Exception {

		try {
			openMsgFromClient = mapper.readValue(message.getPayload(), OpenMessage.class);
			System.out.println(openMsgFromClient.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		String sessionId = openMsgFromClient.getId();
		String conversationId = openMsgFromClient.getParameters().getConversationId();
		String participantId = openMsgFromClient.getParameters().getParticipant().getId();
		log.info("📡 Received 'open' event for sessionId={}, participantId={}", sessionId, participantId);
		log.info("Active Agent List:" + activeAgents.toString());
		
		String agentId = fetchAgentDetails(participantId);
		if (!activeAgents.contains(agentId)) {
			ws.close(new CloseStatus(CloseReason.CloseCodes.VIOLATED_POLICY.getCode(), "Agent not active"));
			return;
		}
		log.info("Participant Id Verified" + participantId);
		
		// ⭐ Core change: Store the conversation and participant IDs for this session
		sessionManager.storeSessionMetadata(ws.getId(), conversationId, participantId);

		String jsonString = prepareOpenedMessage(sessionId);

		ws.sendMessage(new TextMessage(jsonString));

	}

	private String fetchAgentDetails(String participantId) {
//		restTemplate.exchange(null, null, null, null);
		return null;
	}

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
		System.out.println("Sending Opened JSON: " + jsonString);
		return jsonString;
	}

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

		System.out.println("Sending Closed JSON: " + jsonString);

		ws.sendMessage(new TextMessage(jsonString));
		stopSession(ws);
	}

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

//		rabbitChannel.basicPublish(RABBITMQ_EXCHANGE_NAME, routingKey, null,
//				audioMessage.toString().getBytes(StandardCharsets.UTF_8));
//		System.out.println("Published " + audio.length + " bytes to RabbitMQ with routing key: " + routingKey);

	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
		log.error("❌ Transport error on session {}: {}", session.getId(), exception.getMessage(), exception);
		stopSession(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws IOException {
		log.info("🔌 WebSocket closed: {}, reason={}", session.getId(), closeStatus.getReason());
		stopSession(session);
	}

	@Override
	public boolean supportsPartialMessages() {
		return false;
	}

	private void stopSession(WebSocketSession session) throws IOException {
		log.info("🛑 Transcription session stopped for WebSocket {}", session.getId());
		sessionManager.removeSessionMetadata(session.getId());
		session.close(new CloseStatus(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), "Closing session"));
	}

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