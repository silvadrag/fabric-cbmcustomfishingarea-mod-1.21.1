package com.cbm.customfishingarea.gui;

import com.cbm.customfishingarea.pool.PoolChannel;
import com.cbm.customfishingarea.pool.PoolEntry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player GUI draft state for multi-step chest editors. */
public final class GuiSessions {
	public static final class Draft {
		public String areaId = "";
		public String rewardType = "item";
		public PoolEntry entry = new PoolEntry();
		public int page;
		public String screen = "";

		public Draft() {
			entry.setChannel(PoolChannel.BOTH);
			entry.weight = 100;
			entry.count = 1;
			entry.putData("delivery", "party_or_pc");
			entry.putData("level_min", "5");
			entry.putData("level_max", "5");
			entry.putData("form", "normal");
			entry.putData("shiny", "random");
		}
	}

	private static final Map<UUID, Draft> DRAFTS = new ConcurrentHashMap<>();

	private GuiSessions() {
	}

	public static Draft get(UUID playerId) {
		return DRAFTS.computeIfAbsent(playerId, id -> new Draft());
	}

	public static void clear(UUID playerId) {
		DRAFTS.remove(playerId);
	}
}
