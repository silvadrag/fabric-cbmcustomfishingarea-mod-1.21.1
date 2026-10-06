package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.Optional;

/** Adds (or sets) a scoreboard objective value for the player. */
public final class ScoreboardRewardType implements RewardType {
	@Override
	public String id() {
		return "scoreboard";
	}

	@Override
	public Component displayName() {
		return Component.literal("Scoreboard");
	}

	@Override
	public Component describe(PoolEntry entry) {
		String op = entry.data("op", "add");
		return Component.literal("score " + entry.id + " " + op + " " + entry.count
				+ " (w=" + entry.weight + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.COMMAND_BLOCK);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		Scoreboard board = player.getServer().getScoreboard();
		Objective objective = board.getObjective(entry.id);
		if (objective == null) {
			return false;
		}
		ScoreHolder holder = ScoreHolder.forNameOnly(player.getScoreboardName());
		ScoreAccess access = board.getOrCreatePlayerScore(holder, objective);
		String op = entry.data("op", "add").toLowerCase();
		int value = entry.count;
		switch (op) {
			case "set" -> access.set(value);
			case "remove", "sub" -> access.set(access.get() - value);
			default -> access.set(access.get() + value);
		}
		return true;
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (entry.id == null || entry.id.isBlank()) {
			return Optional.of(Component.literal("scoreboard objective id is required"));
		}
		return Optional.empty();
	}
}
