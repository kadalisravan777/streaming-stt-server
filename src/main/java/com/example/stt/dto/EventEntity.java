package com.example.stt.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EventEntity {
	private String type;
	private Object data;
}
