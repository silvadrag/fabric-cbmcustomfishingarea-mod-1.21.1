package com.cbm.customfishingarea.area;

/**
 * Area visibility / usability status.
 * <ul>
 *   <li>{@link #OPEN} — listed for fishers, fishing allowed.</li>
 *   <li>{@link #MAINTENANCE} — listed for fishers as maintenance, fishing denied.</li>
 *   <li>{@link #HIDDEN} — hidden from fisher GUI; fishing still allowed (secret spots).</li>
 * </ul>
 */
public enum AreaStatus {
	OPEN,
	MAINTENANCE,
	HIDDEN;

	public static AreaStatus fromString(String raw) {
		if (raw == null || raw.isBlank()) {
			return OPEN;
		}
		return AreaStatus.valueOf(raw.trim().toUpperCase());
	}

	public boolean isListedForFishers() {
		return this == OPEN || this == MAINTENANCE;
	}

	public boolean allowsFishing() {
		return this == OPEN || this == HIDDEN;
	}
}
