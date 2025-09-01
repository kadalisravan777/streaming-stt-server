package com.example.stt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscardedMessage {
	 private String version;
	    private String id;
	    private String type;
	    private Integer seq;
	    private Integer serverseq;
	    private String position;
	    private DiscardedParameters parameters;
}
