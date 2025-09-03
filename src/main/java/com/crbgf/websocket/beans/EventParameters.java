package com.crbgf.websocket.beans;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EventParameters {
	private List<EventEntity> entities;
}
