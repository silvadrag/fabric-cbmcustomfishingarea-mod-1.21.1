package com.cbm.customfishingarea.area;

import java.util.Locale;

/** How an area footprint is defined from marker points A → B. */
public enum AreaShape {
	/** Inclusive block box (X/Y/Z). */
	COORDINATES,
	/** Inclusive chunk rectangle on XZ (Y ignored). */
	CHUNKS;

	public static AreaShape fromString(String raw) {
		if (raw == null || raw.isBlank()) {
			return CHUNKS;
		}
		String key = raw.trim().toLowerCase(Locale.ROOT);
		return switch (key) {
			case "coordinates", "coords", "coord", "blocks", "block" -> COORDINATES;
			case "chunks", "chunk" -> CHUNKS;
			default -> AreaShape.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		};
	}

	public String label() {
		return name().toLowerCase(Locale.ROOT);
	}
}
