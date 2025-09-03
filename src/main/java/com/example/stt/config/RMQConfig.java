package com.example.stt.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.google.api.client.util.Value;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import jakarta.websocket.CloseReason;

@Component
public class RMQConfig {

	private static final String RABBITMQ_EXCHANGE_NAME = null;
	
	@Value("${genesys.rmq.hostname}")
	private String hostName;
	
	@Value("${genesys.rmq.username}")
	private String username;
	
	@Value("${genesys.rmq.password}")
	private String password;
	
	@Autowired
	private Connection rabbitConnection;
	
	@Autowired
	private Channel rabbitChannel;
	
	@Bean
	public Channel connectToMQ(WebSocketSession session, Map<String, List<String>> headers)
			throws IOException, KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
		try {
			ConnectionFactory factory = new ConnectionFactory();
//			factory.setHost(hostName);
			factory.setUsername(username);
			factory.setPassword(password);
			factory.setUri(hostName);

			this.rabbitConnection = factory.newConnection();
			this.rabbitChannel = rabbitConnection.createChannel();

			this.rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "direct", true);

//			String sessionId = headers.get("Audiohook-Session-Id").get(0);
//			session.getAttributes().put("sessionId", sessionId);

			System.out.println("RabbitMQ connection and channel established.");
			
		} catch (IOException | TimeoutException e) {
			System.err.println("Error connecting to RabbitMQ: " + e.getMessage());
			session.close(new CloseStatus(CloseReason.CloseCodes.UNEXPECTED_CONDITION.getCode(),
					"RabbitMQ connection error."));
		}
		return rabbitChannel;
	}
}
