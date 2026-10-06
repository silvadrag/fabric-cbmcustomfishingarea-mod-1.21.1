package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Picks one random item from an item tag (e.g. {@code minecraft:fishes}). */
public final class ItemTagRewardType implements RewardType {
	@Override
	public String id() {
		return "item_tag";
	}

	@Override
	public Component displayName() {
		return Component.literal("Item Tag");
	}

	@Override
	public Component describe(PoolEntry entry) {
		return Component.literal("tag#" + entry.id + " x" + entry.count + " (w=" + entry.weight
				+ ", " + entry.resolvedChannel().id() + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		List<Item> items = resolveTag(entry.id);
		if (items.isEmpty()) {
			return new ItemStack(Items.NAME_TAG);
		}
		return new ItemStack(items.getFirst(), Math.max(1, Math.min(64, entry.count)));
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		List<Item> items = resolveTag(entry.id);
		if (items.isEmpty()) {
			return false;
		}
		Item item = items.get(player.getRandom().nextInt(items.size()));
		ItemStack stack = new ItemStack(item, Math.max(1, entry.count));
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		return true;
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (entry.id == null || entry.id.isBlank()) {
			return Optional.of(Component.literal("item tag id is required (e.g. minecraft:fishes)"));
		}
		if (resolveTag(entry.id).isEmpty()) {
			return Optional.of(Component.literal("empty or unknown item tag: " + entry.id));
		}
		return Optional.empty();
	}

	private static List<Item> resolveTag(String raw) {
		String id = raw == null ? "" : raw.trim();
		if (id.startsWith("#")) {
			id = id.substring(1);
		}
		if (!id.contains(":")) {
			id = "minecraft:" + id;
		}
		ResourceLocation rl;
		try {
			rl = ResourceLocation.parse(id);
		} catch (Exception e) {
			return List.of();
		}
		TagKey<Item> tag = TagKey.create(Registries.ITEM, rl);
		List<Item> items = new ArrayList<>();
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
			items.add(holder.value());
		}
		return items;
	}
}
