package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Weighted "nothing bites" — useful for sparse pools. */
public final class NothingRewardType implements RewardType {
	@Override
	public String id() {
		return "nothing";
	}

	@Override
	public Component displayName() {
		return Component.literal("Nothing");
	}

	@Override
	public Component describe(PoolEntry entry) {
		return Component.literal("nothing (w=" + entry.weight + ", " + entry.resolvedChannel().id() + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.BARRIER);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		String msg = entry.data("message", "Nothing bites…");
		if (!msg.isBlank()) {
			player.displayClientMessage(Component.literal(msg), true);
		}
		return true;
	}
}
