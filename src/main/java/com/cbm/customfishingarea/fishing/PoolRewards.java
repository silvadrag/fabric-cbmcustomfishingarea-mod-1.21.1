package com.cbm.customfishingarea.fishing;

import com.cbm.customfishingarea.area.FishingArea;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import com.cbm.customfishingarea.pool.RewardTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/** Weighted roll against an area pool, filtered by rod channel. */
public final class PoolRewards {
	private PoolRewards() {
	}

	public static boolean grantRandom(ServerPlayer player, FishingArea area, FishingGate.Channel rodChannel) {
		List<PoolEntry> pool = area.pool;
		if (pool == null || pool.isEmpty()) {
			return false;
		}
		List<PoolEntry> eligible = new ArrayList<>();
		int total = 0;
		for (PoolEntry entry : pool) {
			if (entry == null || entry.weight <= 0) {
				continue;
			}
			if (!entry.resolvedChannel().matches(rodChannel)) {
				continue;
			}
			eligible.add(entry);
			total += entry.weight;
		}
		if (total <= 0 || eligible.isEmpty()) {
			return false;
		}
		RandomSource random = player.getRandom();
		int roll = random.nextInt(total);
		int cursor = 0;
		for (PoolEntry entry : eligible) {
			cursor += Math.max(0, entry.weight);
			if (roll < cursor) {
				RewardType type = RewardTypes.get(entry.type).orElse(null);
				if (type == null) {
					return false;
				}
				return type.grant(player, entry);
			}
		}
		return false;
	}
}
