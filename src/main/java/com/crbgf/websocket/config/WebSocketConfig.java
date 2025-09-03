package com.crbgf.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.crbgf.websocket.service.AudioWebSocketHandler;
import com.crbgf.websocket.service.SessionHandshakeInterceptor;

/**
 * The Class WebSocketConfig.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	/** The audio web socket handler. */
	private final AudioWebSocketHandler audioWebSocketHandler;

	/**
	 * Instantiates a new web socket config.
	 *
	 * @param handler the handler
	 */
	public WebSocketConfig(AudioWebSocketHandler handler) {
		this.audioWebSocketHandler = handler;
	}

	/**
	 * Register web socket handlers.
	 *
	 * @param registry the registry
	 */
	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(audioWebSocketHandler, "/audiohook/ws").addInterceptors(new SessionHandshakeInterceptor())
				.setAllowedOrigins("*");
	}
}
