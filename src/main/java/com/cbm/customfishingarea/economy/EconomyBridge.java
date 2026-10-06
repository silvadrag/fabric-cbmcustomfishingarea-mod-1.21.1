package com.cbm.customfishingarea.economy;

import com.cbm.customfishingarea.CbmCustomFishingAreaMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Soft economy adapter. Tries Impactor-style APIs first; fails soft when no economy mod.
 * Entry fields: {@code currency} (or {@link com.cbm.customfishingarea.pool.PoolEntry#id}), {@code amount}.
 */
public final class EconomyBridge {
	private static Boolean available;

	private EconomyBridge() {
	}

	public static boolean isAvailable() {
		if (available != null) {
			return available;
		}
		available = FabricLoader.getInstance().isModLoaded("impactor")
				|| FabricLoader.getInstance().isModLoaded("impactor-api")
				|| FabricLoader.getInstance().isModLoaded("sdmeconomy")
				|| FabricLoader.getInstance().isModLoaded("economylite");
		return available;
	}

	public static boolean deposit(ServerPlayer player, String currencyId, double amount) {
		if (amount <= 0 || currencyId == null || currencyId.isBlank()) {
			return false;
		}
		try {
			if (tryImpactor(player.getUUID(), currencyId, amount)) {
				return true;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			CbmCustomFishingAreaMod.LOGGER.debug("Economy deposit failed for {}", currencyId, e);
		}
		CbmCustomFishingAreaMod.LOGGER.warn(
				"Economy reward skipped (no working adapter): currency={} amount={} player={}",
				currencyId, amount, player.getGameProfile().getName());
		return false;
	}

	private static boolean tryImpactor(UUID playerId, String currencyId, double amount)
			throws ReflectiveOperationException {
		Class<?> services = Class.forName("net.impactdev.impactor.api.services.ServiceManager");
		Object manager;
		try {
			Method provide = services.getMethod("provide");
			manager = provide.invoke(null);
		} catch (NoSuchMethodException e) {
			FieldLike field = FieldLike.find(services, "INSTANCE", "instance");
			manager = field == null ? null : field.get(null);
		}
		if (manager == null) {
			return false;
		}

		Optional<?> economyOpt = invokeOptional(manager, "provide",
				Class.forName("net.impactdev.impactor.api.economy.EconomyService"));
		if (economyOpt.isEmpty()) {
			return false;
		}
		Object economy = economyOpt.get();

		Object currency = resolveCurrency(economy, currencyId);
		if (currency == null) {
			return false;
		}

		Object account = invoke(economy, "account", playerId);
		if (account instanceof Optional<?> opt) {
			if (opt.isEmpty()) {
				return false;
			}
			account = opt.get();
		}
		if (account == null) {
			return false;
		}

		BigDecimal value = BigDecimal.valueOf(amount);
		try {
			invoke(account, "deposit", currency, value);
			return true;
		} catch (NoSuchMethodException ignored) {
		}
		try {
			invoke(account, "depositAsync", currency, value);
			return true;
		} catch (NoSuchMethodException ignored) {
		}
		return false;
	}

	private static Object resolveCurrency(Object economy, String currencyId) throws ReflectiveOperationException {
		try {
			Object currencies = invoke(economy, "currencies");
			if (currencies != null) {
				try {
					Object found = invoke(currencies, "currency", currencyId);
					if (found instanceof Optional<?> opt) {
						return opt.orElse(null);
					}
					if (found != null) {
						return found;
					}
				} catch (NoSuchMethodException ignored) {
				}
			}
		} catch (NoSuchMethodException ignored) {
		}
		try {
			Object found = invoke(economy, "currency", currencyId);
			if (found instanceof Optional<?> opt) {
				return opt.orElse(null);
			}
			return found;
		} catch (NoSuchMethodException ignored) {
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Optional<?> invokeOptional(Object target, String method, Class<?> typeArg)
			throws ReflectiveOperationException {
		for (Method m : target.getClass().getMethods()) {
			if (!m.getName().equals(method) || m.getParameterCount() != 1) {
				continue;
			}
			Object result = m.invoke(target, typeArg);
			if (result instanceof Optional<?> opt) {
				return opt;
			}
		}
		return Optional.empty();
	}

	private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
		Class<?>[] types = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			Object arg = args[i];
			if (arg instanceof UUID) {
				types[i] = UUID.class;
			} else if (arg instanceof BigDecimal) {
				types[i] = BigDecimal.class;
			} else if (arg instanceof String) {
				types[i] = String.class;
			} else if (arg instanceof Class<?>) {
				types[i] = Class.class;
			} else {
				types[i] = arg.getClass();
			}
		}
		Method method = findMethod(target.getClass(), name, types);
		if (method == null) {
			throw new NoSuchMethodException(name);
		}
		return method.invoke(target, args);
	}

	private static Method findMethod(Class<?> type, String name, Class<?>[] types) {
		Class<?> cursor = type;
		while (cursor != null) {
			for (Method method : cursor.getDeclaredMethods()) {
				if (!method.getName().equals(name) || method.getParameterCount() != types.length) {
					continue;
				}
				Class<?>[] params = method.getParameterTypes();
				boolean ok = true;
				for (int i = 0; i < params.length; i++) {
					if (!params[i].isAssignableFrom(types[i]) && !boxMatch(params[i], types[i])) {
						ok = false;
						break;
					}
				}
				if (ok) {
					method.setAccessible(true);
					return method;
				}
			}
			cursor = cursor.getSuperclass();
		}
		return null;
	}

	private static boolean boxMatch(Class<?> param, Class<?> arg) {
		return (param == Object.class);
	}

	private record FieldLike(java.lang.reflect.Field field) {
		Object get(Object owner) throws IllegalAccessException {
			field.setAccessible(true);
			return field.get(owner);
		}

		static FieldLike find(Class<?> type, String... names) {
			for (String name : names) {
				try {
					return new FieldLike(type.getField(name));
				} catch (NoSuchFieldException ignored) {
				}
				try {
					java.lang.reflect.Field f = type.getDeclaredField(name);
					return new FieldLike(f);
				} catch (NoSuchFieldException ignored) {
				}
			}
			return null;
		}
	}
}
