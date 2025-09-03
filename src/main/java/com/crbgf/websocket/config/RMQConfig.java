package com.crbgf.websocket.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * The Class RMQConfig.
 */
@Configuration
@Component
public class RMQConfig {

	/** The Constant log. */
	private static final Logger log = LoggerFactory.getLogger(RMQConfig.class);

	/** The Constant RABBITMQ_EXCHANGE_NAME. */
	private static final String RABBITMQ_EXCHANGE_NAME = null;

	/** The host name. */
	@Value("${genesys.rmq.hostname}")
	private String hostName;

	/** The username. */
	@Value("${genesys.rmq.username}")
	private String username;

	/** The password. */
	@Value("${genesys.rmq.password}")
	private String password;

	/** The rabbit connection. */
	private Connection rabbitConnection;

	/** The rabbit channel. */
	private Channel rabbitChannel;

	/**
	 * Connect to MQ.
	 *
	 * @param rabbitConnection the rabbit connection
	 * @return the channel
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws KeyManagementException the key management exception
	 * @throws NoSuchAlgorithmException the no such algorithm exception
	 * @throws URISyntaxException the URI syntax exception
	 */
	@Bean
	public Channel connectToMQ(Connection rabbitConnection)
			throws IOException, KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
		try {
			this.rabbitChannel = rabbitConnection.createChannel();
			this.rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "direct", true);
			log.info("RabbitMQ connection and channel established.");

		} catch (IOException e) {
			log.error("error occured while connecting to RMQ");
		}
		return rabbitChannel;
	}

	/**
	 * Rabbit connection.
	 *
	 * @return the connection
	 */
	@Bean
	public Connection rabbitConnection() {

		try {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setUsername(username);
			factory.setPassword(password);
			factory.setHost(hostName);
			factory.setPort(5672);
			rabbitConnection = factory.newConnection();
		} catch (IOException | TimeoutException e) {
			log.error("Exception occured while creating rabbitConnection");
			e.printStackTrace();
		}

		return rabbitConnection;
	}

	/**
	 * Rest template.
	 *
	 * @return the rest template
	 */
	@Bean
	public RestTemplate restTemplate() {

		return new RestTemplate();
	}
}
