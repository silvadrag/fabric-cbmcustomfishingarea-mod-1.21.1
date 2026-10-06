package com.cbm.customfishingarea.fishing;

import com.cbm.customfishingarea.area.AreaStorage;
import com.cbm.customfishingarea.area.FishingArea;
import com.cbm.customfishingarea.config.ModConfig;
import com.cbm.customfishingarea.permission.ModPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.Optional;

/**
 * Central gate for both vanilla and Cobblemon fishing channels.
 * Always evaluates using bobber position + dimension (not player feet).
 */
public final class FishingGate {
	public enum Channel {
		VANILLA,
		COBBLEMON
	}

	public enum Decision {
		/** Custom system off, or this channel is not replaced — leave original loot alone. */
		PASS_THROUGH,
		/** Custom system owns this catch; caller should cancel vanilla/cobblemon rewards then grant pool later. */
		ALLOW_CUSTOM,
		DENY_NO_PERMISSION,
		DENY_NO_AREA,
		DENY_MAINTENANCE,
		DENY_EMPTY_POOL
	}

	public record Result(Decision decision, Optional<FishingArea> area) {
		public boolean shouldCancelOriginalRewards() {
			return decision != Decision.PASS_THROUGH;
		}

		public boolean shouldGrantCustomRewards() {
			return decision == Decision.ALLOW_CUSTOM;
		}

		public Component message() {
			return switch (decision) {
				case DENY_NO_PERMISSION -> Component.literal("You do not have permission to fish here.");
				case DENY_NO_AREA -> Component.literal(ModConfig.get().messageNoArea);
				case DENY_MAINTENANCE -> Component.literal(ModConfig.get().messageMaintenance);
				case DENY_EMPTY_POOL -> Component.literal(ModConfig.get().messageEmptyPool);
				default -> Component.empty();
			};
		}
	}

	private FishingGate() {
	}

	public static Result evaluate(Player player, Channel channel, ResourceLocation dimension, double x, double y, double z) {
		if (!ModConfig.get().customFishingEnabled) {
			return new Result(Decision.PASS_THROUGH, Optional.empty());
		}
		if (channel == Channel.VANILLA && !ModConfig.get().replaceVanillaFishing) {
			return new Result(Decision.PASS_THROUGH, Optional.empty());
		}
		if (channel == Channel.COBBLEMON && !ModConfig.get().replaceCobblemonFishing) {
			return new Result(Decision.PASS_THROUGH, Optional.empty());
		}
		// Bypass only skips the fisher-permission node — areas are still enforced.
		if (!ModPermissions.canBypass(player) && !ModPermissions.canFish(player)) {
			return new Result(Decision.DENY_NO_PERMISSION, Optional.empty());
		}

		Optional<FishingArea> area = AreaStorage.resolveAt(dimension, x, y, z);
		if (area.isEmpty()) {
			return new Result(Decision.DENY_NO_AREA, Optional.empty());
		}
		return evaluateArea(area.get());
	}

	private static Result evaluateArea(FishingArea area) {
		if (!area.status.allowsFishing()) {
			return new Result(Decision.DENY_MAINTENANCE, Optional.of(area));
		}
		if (area.pool == null || area.pool.isEmpty()) {
			return new Result(Decision.DENY_EMPTY_POOL, Optional.of(area));
		}
		return new Result(Decision.ALLOW_CUSTOM, Optional.of(area));
	}

	public static Result evaluateVanillaHook(FishingHook hook) {
		Player owner = hook.getPlayerOwner();
		if (owner == null) {
			return new Result(Decision.PASS_THROUGH, Optional.empty());
		}
		ResourceLocation dim = hook.level().dimension().location();
		return evaluate(owner, Channel.VANILLA, dim, hook.getX(), hook.getY(), hook.getZ());
	}

	public static Result evaluateCobblemonBobber(ServerPlayer player, ResourceLocation dimension, double x, double y, double z) {
		return evaluate(player, Channel.COBBLEMON, dimension, x, y, z);
	}

	public static void notifyIfNeeded(Player player, Result result) {
		Component message = result.message();
		if (!message.getString().isEmpty() && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.displayClientMessage(message, true);
		}
	}
}
