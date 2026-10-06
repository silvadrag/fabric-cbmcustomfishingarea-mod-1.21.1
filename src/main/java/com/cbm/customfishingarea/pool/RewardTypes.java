package com.cbm.customfishingarea.pool;

import com.cbm.customfishingarea.pool.type.CobblemonPokemonRewardType;
import com.cbm.customfishingarea.pool.type.CobblemonSpeciesRewardType;
import com.cbm.customfishingarea.pool.type.CommandRewardType;
import com.cbm.customfishingarea.pool.type.EconomyRewardType;
import com.cbm.customfishingarea.pool.type.ItemRewardType;
import com.cbm.customfishingarea.pool.type.ItemTagRewardType;
import com.cbm.customfishingarea.pool.type.NothingRewardType;
import com.cbm.customfishingarea.pool.type.ScoreboardRewardType;
import com.cbm.customfishingarea.pool.type.XpRewardType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RewardTypes {
	private static final Map<String, RewardType> REGISTRY = new LinkedHashMap<>();

	private RewardTypes() {
	}

	public static void bootstrap() {
		REGISTRY.clear();
		register(new ItemRewardType());
		register(new ItemTagRewardType());
		register(new XpRewardType());
		register(new NothingRewardType());
		register(new CommandRewardType());
		register(new ScoreboardRewardType());
		register(new EconomyRewardType());
		register(new CobblemonSpeciesRewardType());
		register(new CobblemonPokemonRewardType());
	}

	public static void register(RewardType type) {
		REGISTRY.put(type.id(), type);
	}

	public static Optional<RewardType> get(String id) {
		return Optional.ofNullable(REGISTRY.get(id));
	}

	public static Collection<RewardType> all() {
		return REGISTRY.values();
	}
}
