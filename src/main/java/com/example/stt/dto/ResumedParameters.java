package com.example.stt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)

public class ResumedParameters {
	private String start;
	private String discarded;
	public String getStart() {
		return start;
	}
	public void setStart(String start) {
		this.start = start;
	}
	public String getDiscarded() {
		return discarded;
	}
	public void setDiscarded(String discarded) {
		this.discarded = discarded;
	}
	
}
