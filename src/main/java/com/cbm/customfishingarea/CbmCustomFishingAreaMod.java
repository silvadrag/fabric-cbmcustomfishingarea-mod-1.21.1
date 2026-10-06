package com.cbm.customfishingarea;

import com.cbm.customfishingarea.area.AreaStorage;
import com.cbm.customfishingarea.area.AreaVisualizer;
import com.cbm.customfishingarea.area.SelectionStore;
import com.cbm.customfishingarea.cobblemon.CobblemonHook;
import com.cbm.customfishingarea.command.ModCommands;
import com.cbm.customfishingarea.config.ModConfig;
import com.cbm.customfishingarea.item.AreaMarkerItems;
import com.cbm.customfishingarea.permission.ModPermissions;
import com.cbm.customfishingarea.pool.RewardTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CbmCustomFishingAreaMod implements ModInitializer {
	public static final String MOD_ID = "cbmcustomfishingarea";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		ModConfig.load();
		RewardTypes.bootstrap();
		ModPermissions.bootstrap();

		CommandRegistrationCallback.EVENT.register(ModCommands::register);

		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			AreaStorage.load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			AreaStorage.save();
			server = null;
		});

		UseItemCallback.EVENT.register(AreaMarkerItems::onUseItem);
		UseBlockCallback.EVENT.register(AreaMarkerItems::onUseBlock);
		AttackBlockCallback.EVENT.register(AreaMarkerItems::onAttackBlock);
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) ->
				!AreaMarkerItems.shouldCancelBreak(player));

		SelectionStore.bootstrap();
		AreaVisualizer.bootstrap();
		CobblemonHook.tryHook();

		LOGGER.info("CBM Custom Fishing Area ready (A→B marker, coords/chunks, Cobblemon soft-dep).");
	}

	public static MinecraftServer getServer() {
		return server;
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
