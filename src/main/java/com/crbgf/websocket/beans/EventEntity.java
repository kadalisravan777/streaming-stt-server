package com.crbgf.websocket.beans;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EventEntity {
	private String type;
	private Object data;
}
