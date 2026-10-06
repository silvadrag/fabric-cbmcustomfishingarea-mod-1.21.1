package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.economy.EconomyBridge;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Soft economy payout via {@link EconomyBridge}.
 * {@code id} or data {@code currency} = currency id; {@code count} or data {@code amount} = amount.
 */
public final class EconomyRewardType implements RewardType {
	@Override
	public String id() {
		return "economy";
	}

	@Override
	public Component displayName() {
		return Component.literal("Economy");
	}

	@Override
	public Component describe(PoolEntry entry) {
		return Component.literal(amount(entry) + " " + currency(entry)
				+ " (w=" + entry.weight + ", " + entry.resolvedChannel().id() + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.GOLD_INGOT);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		return EconomyBridge.deposit(player, currency(entry), amount(entry));
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (currency(entry).isBlank()) {
			return Optional.of(Component.literal("currency id is required"));
		}
		if (amount(entry) <= 0) {
			return Optional.of(Component.literal("amount must be > 0"));
		}
		return Optional.empty();
	}

	private static String currency(PoolEntry entry) {
		return entry.data("currency", entry.id == null ? "" : entry.id);
	}

	private static double amount(PoolEntry entry) {
		double fromData = entry.dataDouble("amount", Double.NaN);
		if (!Double.isNaN(fromData)) {
			return fromData;
		}
		return entry.count;
	}
}
