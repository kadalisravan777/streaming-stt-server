package com.example.stt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)

public class PauseMessage {
	private String version;
	private String id;
	private String type;
	private Integer seq;
	private Integer clientseq;
	private Integer serverseq;
	private Object parameters; // Empty
	
	
	public Integer getServerseq() {
		return serverseq;
	}
	public void setServerseq(Integer serverseq) {
		this.serverseq = serverseq;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Integer getSeq() {
		return seq;
	}
	public void setSeq(Integer seq) {
		this.seq = seq;
	}
	public Integer getClientseq() {
		return clientseq;
	}
	public void setClientseq(Integer clientseq) {
		this.clientseq = clientseq;
	}
	public Object getParameters() {
		return parameters;
	}
	public void setParameters(Object parameters) {
		this.parameters = parameters;
	}
	
}
