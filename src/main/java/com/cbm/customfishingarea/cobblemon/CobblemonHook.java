package com.cbm.customfishingarea.cobblemon;

import com.cbm.customfishingarea.CbmCustomFishingAreaMod;
import com.cbm.customfishingarea.fishing.FishingGate;
import com.cbm.customfishingarea.fishing.PoolRewards;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Soft-dependency hook for Cobblemon Pokerod fishing.
 * <p>
 * Important: cancelling {@code POKEROD_REEL} skips {@code retrieve()} entirely, which would
 * leave the bobber stuck. We still cancel the reel to block Cobblemon loot/pokemon, but we
 * discard the bobber ourselves first.
 */
public final class CobblemonHook {
	private CobblemonHook() {
	}

	public static void tryHook() {
		if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
			CbmCustomFishingAreaMod.LOGGER.info("Cobblemon not present — Pokerod gate skipped (soft-dep).");
			return;
		}
		try {
			hookEvents();
			CbmCustomFishingAreaMod.LOGGER.info("Cobblemon detected — Pokerod fishing gate hooked.");
		} catch (ReflectiveOperationException e) {
			CbmCustomFishingAreaMod.LOGGER.error("Failed to hook Cobblemon fishing events", e);
		}
	}

	private static void hookEvents() throws ReflectiveOperationException {
		Class<?> events = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");

		// Primary gate: cancel reel (blocks item + pokemon paths) + discard bobber so it is not stuck.
		subscribeCancelable(events, "POKEROD_REEL", CobblemonHook::handleReel);

		// Safety net if retrieve still runs somehow: cancel pokemon spawn and discard.
		subscribeCancelable(events, "BOBBER_SPAWN_POKEMON_PRE", CobblemonHook::handleSpawnPre);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void subscribeCancelable(Class<?> events, String fieldName, Consumer<Object> handler)
			throws ReflectiveOperationException {
		Field field = events.getField(fieldName);
		Object observable = field.get(null);
		Method subscribe = null;
		for (Method method : observable.getClass().getMethods()) {
			if (!method.getName().equals("subscribe")) {
				continue;
			}
			Class<?>[] params = method.getParameterTypes();
			if (params.length == 1 && Consumer.class.isAssignableFrom(params[0])) {
				subscribe = method;
				break;
			}
		}
		if (subscribe == null) {
			for (Method method : observable.getClass().getMethods()) {
				if (method.getName().equals("subscribe") && method.getParameterCount() >= 1) {
					subscribe = method;
					break;
				}
			}
		}
		if (subscribe == null) {
			throw new NoSuchMethodException("subscribe on " + fieldName);
		}
		Consumer raw = handler::accept;
		if (subscribe.getParameterCount() == 1) {
			subscribe.invoke(observable, raw);
		} else {
			subscribe.invoke(observable, 0, raw);
		}
	}

	private static void handleReel(Object event) {
		try {
			Object playerObj = invokeFlexible(event, "getPlayer", "player");
			if (!(playerObj instanceof ServerPlayer serverPlayer)) {
				return;
			}
			Entity bobber = serverPlayer.fishing;
			// Empty retract (no bite): let Cobblemon retrieve/discard normally.
			if (bobber == null || !hasActiveBite(bobber)) {
				return;
			}

			double x = bobber.getX();
			double y = bobber.getY();
			double z = bobber.getZ();
			ResourceLocation dim = bobber.level().dimension().location();

			FishingGate.Result result = FishingGate.evaluateCobblemonBobber(serverPlayer, dim, x, y, z);
			if (!result.shouldCancelOriginalRewards()) {
				return;
			}

			FishingGate.notifyIfNeeded(serverPlayer, result);
			if (result.shouldGrantCustomRewards() && result.area().isPresent()) {
				PoolRewards.grantRandom(serverPlayer, result.area().get(), FishingGate.Channel.COBBLEMON);
			}

			// Discard BEFORE cancelling reel — cancel skips retrieve(), which would otherwise leave the bobber stuck.
			if (!bobber.isRemoved()) {
				bobber.discard();
			}
			cancel(event);
		} catch (ReflectiveOperationException e) {
			CbmCustomFishingAreaMod.LOGGER.debug("Cobblemon reel gate failed", e);
		}
	}

	/** Cobblemon only awards loot/pokemon when {@code hookCountdown > 0}. */
	private static boolean hasActiveBite(Entity bobber) {
		try {
			Object value = invokeFlexible(bobber, "getHookCountdown", "hookCountdown");
			if (value instanceof Number number) {
				return number.intValue() > 0;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		// If we cannot read the field, gate on any reel with a bobber (safer than stuck bobber).
		return true;
	}

	private static void handleSpawnPre(Object event) {
		try {
			Object bobber = invokeFlexible(event, "getBobber", "bobber");
			if (!(bobber instanceof Entity entity)) {
				return;
			}
			Player owner = findOwner(entity);
			if (!(owner instanceof ServerPlayer serverPlayer)) {
				return;
			}
			ResourceLocation dim = entity.level().dimension().location();
			FishingGate.Result result = FishingGate.evaluateCobblemonBobber(
					serverPlayer, dim, entity.getX(), entity.getY(), entity.getZ());
			if (!result.shouldCancelOriginalRewards()) {
				return;
			}
			// Cobblemon's cancel path returns from retrieve() without discard — clean up ourselves.
			if (!entity.isRemoved()) {
				entity.discard();
			}
			cancel(event);
		} catch (ReflectiveOperationException e) {
			CbmCustomFishingAreaMod.LOGGER.debug("Cobblemon spawn-pre gate failed", e);
		}
	}

	private static Player findOwner(Entity bobber) {
		if (bobber instanceof FishingHook hook) {
			return hook.getPlayerOwner();
		}
		try {
			Object owner = invokeFlexible(bobber, "getPlayerOwner", "getOwner", "owner");
			if (owner instanceof Player player) {
				return player;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return null;
	}

	private static Object invokeFlexible(Object target, String... names) throws ReflectiveOperationException {
		for (String name : names) {
			try {
				Method method = target.getClass().getMethod(name);
				return method.invoke(target);
			} catch (NoSuchMethodException ignored) {
			}
		}
		for (String name : names) {
			try {
				Field field = target.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return field.get(target);
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchMethodException(String.join("/", names));
	}

	private static void cancel(Object event) throws ReflectiveOperationException {
		try {
			Method cancel = event.getClass().getMethod("cancel");
			cancel.invoke(event);
			return;
		} catch (NoSuchMethodException ignored) {
		}
		Method cancel = findMethodInHierarchy(event.getClass(), "cancel");
		if (cancel != null) {
			cancel.invoke(event);
		}
	}

	private static Method findMethodInHierarchy(Class<?> type, String name) {
		Class<?> cursor = type;
		while (cursor != null) {
			for (Method method : cursor.getDeclaredMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == 0) {
					method.setAccessible(true);
					return method;
				}
			}
			cursor = cursor.getSuperclass();
		}
		return null;
	}
}
