package com.cbm.customfishingarea.pool;

import com.cbm.customfishingarea.fishing.FishingGate;

import java.util.Locale;

/**
 * Which fishing rod channel may roll this pool entry.
 * Default {@link #BOTH} — safest for existing / test data.
 */
public enum PoolChannel {
	VANILLA,
	COBBLEMON,
	BOTH;

	public boolean matches(FishingGate.Channel rodChannel) {
		return switch (this) {
			case BOTH -> true;
			case VANILLA -> rodChannel == FishingGate.Channel.VANILLA;
			case COBBLEMON -> rodChannel == FishingGate.Channel.COBBLEMON;
		};
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static PoolChannel fromString(String raw) {
		if (raw == null || raw.isBlank()) {
			return BOTH;
		}
		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "vanilla", "rod", "fishing_rod" -> VANILLA;
			case "cobblemon", "pokerod", "poke_rod" -> COBBLEMON;
			default -> BOTH;
		};
	}
}
