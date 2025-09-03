package com.crbgf.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * The Class SessionHandshakeInterceptor.
 */
public class SessionHandshakeInterceptor implements HandshakeInterceptor {

	/** The Constant log. */
	private static final Logger log = LoggerFactory.getLogger(SessionHandshakeInterceptor.class);

	/**
	 * Before handshake.
	 *
	 * @param request the request
	 * @param response the response
	 * @param wsHandler the ws handler
	 * @param attributes the attributes
	 * @return true, if successful
	 */
	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {

		String uri = request.getURI().toString();
		log.info("🖐️ WebSocket handshake accepted: {}", uri);
		return true;
	}

	/**
	 * After handshake.
	 *
	 * @param request the request
	 * @param response the response
	 * @param wsHandler the ws handler
	 * @param exception the exception
	 */
	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
		// No operation
	}
}
