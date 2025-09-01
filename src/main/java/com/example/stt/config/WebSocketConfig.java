package com.example.stt.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.stt.service.AudioWebSocketHandler;
import com.example.stt.service.SessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final AudioWebSocketHandler audioWebSocketHandler;

	public WebSocketConfig(AudioWebSocketHandler handler) {
		this.audioWebSocketHandler = handler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(audioWebSocketHandler, "/audiohook/ws").addInterceptors(new SessionHandshakeInterceptor())
				.setAllowedOrigins("*");
	}
}
