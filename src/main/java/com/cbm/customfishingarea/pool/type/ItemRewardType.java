package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

public final class ItemRewardType implements RewardType {
	@Override
	public String id() {
		return "item";
	}

	@Override
	public Component displayName() {
		return Component.literal("Item");
	}

	@Override
	public Component describe(PoolEntry entry) {
		return Component.literal(entry.count + "x " + entry.id + " (w=" + entry.weight
				+ ", " + entry.resolvedChannel().id() + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry.id));
		return item.map(value -> new ItemStack(value, Math.max(1, Math.min(64, entry.count))))
				.orElse(new ItemStack(Items.BARRIER));
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry.id));
		if (item.isEmpty()) {
			return false;
		}
		ItemStack stack = new ItemStack(item.get(), Math.max(1, entry.count));
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		return true;
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (entry.id == null || entry.id.isBlank()) {
			return Optional.of(Component.literal("item id is required"));
		}
		if (BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry.id)).isEmpty()) {
			return Optional.of(Component.literal("unknown item: " + entry.id));
		}
		return Optional.empty();
	}
}
