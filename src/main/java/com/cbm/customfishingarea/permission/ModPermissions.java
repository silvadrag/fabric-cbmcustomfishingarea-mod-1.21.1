package com.cbm.customfishingarea.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Permission nodes. OP levels are only fallback defaults when no permission plugin is present.
 *
 * Role split:
 * <ul>
 *   <li>{@link #FISH} — fisher commands / fisher GUI</li>
 *   <li>{@link #MANAGE} — area setup, pool, config, manager GUI</li>
 *   <li>{@link #BYPASS} — optional gate bypass while testing</li>
 *   <li>{@link #RELOAD} — reload config/areas (also granted via manage)</li>
 * </ul>
 */
public final class ModPermissions {
	public static final String FISH = "cbmcustomfishingarea.fish";
	public static final String MANAGE = "cbmcustomfishingarea.manage";
	public static final String BYPASS = "cbmcustomfishingarea.bypass";
	public static final String RELOAD = "cbmcustomfishingarea.reload";

	private ModPermissions() {
	}

	public static void bootstrap() {
		// Nodes are convention-based; LuckPerms discovers them via checks.
	}

	public static boolean canFish(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		return Permissions.check(serverPlayer, FISH, true);
	}

	public static boolean canManage(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return player.hasPermissions(2);
		}
		return Permissions.check(serverPlayer, MANAGE, 2);
	}

	/**
	 * Explicit opt-in only (no OP fallback). Bypass skips the fisher permission check
	 * but still enforces area gates when the custom system is enabled.
	 */
	public static boolean canBypass(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		return Permissions.check(serverPlayer, BYPASS, false);
	}

	public static boolean canFish(CommandSourceStack source) {
		return Permissions.check(source, FISH, true);
	}

	public static boolean canManage(CommandSourceStack source) {
		return Permissions.check(source, MANAGE, 2);
	}

	public static boolean canReload(CommandSourceStack source) {
		return Permissions.check(source, RELOAD, 2) || canManage(source);
	}

	/** Root command visible if the source has either role. */
	public static boolean canUseRoot(CommandSourceStack source) {
		return canFish(source) || canManage(source);
	}

	public static boolean isFisherPlayer(CommandSourceStack source) {
		return source.isPlayer() && canFish(source);
	}

	public static boolean isManagerPlayer(CommandSourceStack source) {
		return source.isPlayer() && canManage(source);
	}
}
