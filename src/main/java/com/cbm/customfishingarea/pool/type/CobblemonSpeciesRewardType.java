package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.cobblemon.CobblemonBridge;
import com.cbm.customfishingarea.cobblemon.SpeciesCapability;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/** Simple Cobblemon grant: species + optional level/aspects + delivery. IVs/EVs = Cobblemon random. */
public final class CobblemonSpeciesRewardType implements RewardType {
	@Override
	public String id() {
		return "cobblemon_species";
	}

	@Override
	public Component displayName() {
		return Component.literal("Cobblemon Species");
	}

	@Override
	public Component describe(PoolEntry entry) {
		int min = entry.dataInt("level_min", entry.dataInt("level", 5));
		int max = entry.dataInt("level_max", min);
		String delivery = entry.data("delivery", CobblemonBridge.Delivery.PARTY_OR_PC.id());
		return Component.literal(entry.id + " lv" + min + (max != min ? "-" + max : "")
				+ " [" + delivery + "] (w=" + entry.weight + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.FISHING_ROD);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		return CobblemonBridge.grant(player, entry, false);
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (entry.id == null || entry.id.isBlank()) {
			return Optional.of(Component.literal("species id is required (e.g. magikarp)"));
		}
		if (!CobblemonBridge.isLoaded()) {
			return Optional.empty(); // allow saving offline; grant will skip
		}
		SpeciesCapability cap = SpeciesCapability.inspect(entry.id);
		if (!cap.present()) {
			return Optional.of(Component.literal("unknown Cobblemon species: " + entry.id));
		}
		return Optional.empty();
	}
}
