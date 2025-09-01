package com.example.stt.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ErrorMessage {
	private String version;
	private String id;
	private String type;
	private Integer seq;
	private Integer serverseq;
	private String position;
	private ErrorParameters parameters;
}
