package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

public final class XpRewardType implements RewardType {
	@Override
	public String id() {
		return "xp";
	}

	@Override
	public Component displayName() {
		return Component.literal("Experience");
	}

	@Override
	public Component describe(PoolEntry entry) {
		boolean levels = "levels".equalsIgnoreCase(entry.data("mode", "points"));
		return Component.literal((levels ? entry.count + " levels" : entry.count + " xp")
				+ " (w=" + entry.weight + ", " + entry.resolvedChannel().id() + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.EXPERIENCE_BOTTLE);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		int amount = Math.max(1, entry.count);
		if ("levels".equalsIgnoreCase(entry.data("mode", "points"))) {
			player.giveExperienceLevels(amount);
		} else {
			player.giveExperiencePoints(amount);
		}
		return true;
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (entry.count <= 0) {
			return Optional.of(Component.literal("xp amount must be > 0"));
		}
		return Optional.empty();
	}
}
