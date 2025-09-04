package com.crbgf.websocket.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TokenService {

	@Value("${genesys.client.id}")
	private String clientId;

	@Value("${genesys.client.secret}")
	private String clientSecret;

	@Value("${genesys.auth.url}")
	private String authUrl;

	private final RestTemplate restTemplate = new RestTemplate();

	private String accessToken;

	public synchronized String getAccessToken() {
		if (accessToken == null) {
			fetchNewToken();
		}
		return accessToken;
	}

	public synchronized void refreshToken() {
		fetchNewToken();
	}

	private void fetchNewToken() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);

		HttpEntity<String> request = new HttpEntity<>("grant_type=client_credentials", headers);

		ResponseEntity<Map> response = restTemplate.exchange(authUrl, HttpMethod.POST, request, Map.class);

		if (response.getStatusCode().is2xxSuccessful()) {
			Map<String, Object> body = response.getBody();
			this.accessToken = (String) body.get("access_token");
		} else {
			throw new RuntimeException("Failed to fetch access token: " + response.getStatusCode());
		}
	}
}
