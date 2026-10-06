package com.cbm.customfishingarea.pool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Pluggable reward type for the fishing pool catalog.
 * Add new types by registering into {@link RewardTypes}.
 */
public interface RewardType {
	String id();

	Component displayName();

	/** Human label for an entry shown in chest GUIs. */
	Component describe(PoolEntry entry);

	/** Optional icon stack for chest GUI slots. */
	default ItemStack icon(PoolEntry entry) {
		return ItemStack.EMPTY;
	}

	/** Grant the reward. Return false if it could not be applied. */
	boolean grant(ServerPlayer player, PoolEntry entry);

	/** Validate entry fields before save. */
	default Optional<Component> validate(PoolEntry entry) {
		return Optional.empty();
	}
}
