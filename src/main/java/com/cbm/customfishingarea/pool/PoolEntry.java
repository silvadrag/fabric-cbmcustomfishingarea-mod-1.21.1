package com.cbm.customfishingarea.pool;

import java.util.HashMap;
import java.util.Map;

/** Serializable pool row. {@code type} maps to a {@link RewardType} in the registry. */
public final class PoolEntry {
	public String type = "item";
	public String id = "";
	public int count = 1;
	public int weight = 100;
	public String displayName = "";
	/**
	 * Rod channel filter: {@code vanilla}, {@code cobblemon}, or {@code both}.
	 * Missing / blank → {@link PoolChannel#BOTH}.
	 */
	public String channel = PoolChannel.BOTH.id();
	/** Extra payload for economy / scoreboard / command / cobblemon / etc. */
	public Map<String, String> data = new HashMap<>();

	public PoolChannel resolvedChannel() {
		return PoolChannel.fromString(channel);
	}

	public void setChannel(PoolChannel value) {
		this.channel = value == null ? PoolChannel.BOTH.id() : value.id();
	}

	public String data(String key, String fallback) {
		if (data == null) {
			return fallback;
		}
		String value = data.get(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	public void putData(String key, String value) {
		if (data == null) {
			data = new HashMap<>();
		}
		if (value == null) {
			data.remove(key);
		} else {
			data.put(key, value);
		}
	}

	public int dataInt(String key, int fallback) {
		try {
			return Integer.parseInt(data(key, String.valueOf(fallback)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public double dataDouble(String key, double fallback) {
		try {
			return Double.parseDouble(data(key, String.valueOf(fallback)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
