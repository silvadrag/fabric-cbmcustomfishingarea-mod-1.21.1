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

/**
 * Detailed Cobblemon pool entry: form / aspects / shiny chance / delivery.
 * IVs/EVs intentionally omitted — Cobblemon rolls them.
 * Use {@link SpeciesCapability} in GUI to enable only real forms for that species.
 */
public final class CobblemonPokemonRewardType implements RewardType {
	@Override
	public String id() {
		return "cobblemon_pokemon";
	}

	@Override
	public Component displayName() {
		return Component.literal("Cobblemon Pokémon");
	}

	@Override
	public Component describe(PoolEntry entry) {
		String form = entry.data("form", "normal");
		String shiny = entry.data("shiny", entry.data("shiny_chance", "random"));
		String delivery = entry.data("delivery", CobblemonBridge.Delivery.PARTY_OR_PC.id());
		int min = entry.dataInt("level_min", 5);
		int max = entry.dataInt("level_max", min);
		return Component.literal(entry.id + "/" + form + " lv" + min + (max != min ? "-" + max : "")
				+ " shiny=" + shiny + " [" + delivery + "] (w=" + entry.weight + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.TROPICAL_FISH);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		return CobblemonBridge.grant(player, entry, true);
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		if (entry.id == null || entry.id.isBlank()) {
			return Optional.of(Component.literal("species id is required"));
		}
		if (!CobblemonBridge.isLoaded()) {
			return Optional.empty();
		}
		SpeciesCapability cap = SpeciesCapability.inspect(entry.id);
		if (!cap.present()) {
			return Optional.of(Component.literal("unknown Cobblemon species: " + entry.id));
		}
		Optional<Component> formError = cap.validateForm(entry.data("form", "normal"));
		if (formError.isPresent()) {
			return formError;
		}
		return Optional.empty();
	}
}
