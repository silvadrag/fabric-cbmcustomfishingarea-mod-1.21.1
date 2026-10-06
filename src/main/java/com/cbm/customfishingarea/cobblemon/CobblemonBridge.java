package com.cbm.customfishingarea.cobblemon;

import com.cbm.customfishingarea.CbmCustomFishingAreaMod;
import com.cbm.customfishingarea.pool.PoolEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Soft Cobblemon API via reflection. IVs/EVs always left to Cobblemon's own random rolls.
 */
public final class CobblemonBridge {
	public enum Delivery {
		PARTY_OR_PC,
		SPAWN_WORLD;

		public static Delivery from(String raw) {
			if (raw == null) {
				return PARTY_OR_PC;
			}
			return switch (raw.trim().toLowerCase(Locale.ROOT)) {
				case "spawn", "spawn_world", "world", "entity", "bobber" -> SPAWN_WORLD;
				default -> PARTY_OR_PC;
			};
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public record SpeciesInfo(String resourceName, String displayName, List<String> forms, List<String> aspects) {
	}

	private CobblemonBridge() {
	}

	public static boolean isLoaded() {
		return FabricLoader.getInstance().isModLoaded("cobblemon");
	}

	public static Optional<SpeciesInfo> speciesInfo(String speciesId) {
		if (!isLoaded() || speciesId == null || speciesId.isBlank()) {
			return Optional.empty();
		}
		try {
			Object species = resolveSpecies(normalizeSpecies(speciesId));
			if (species == null) {
				return Optional.empty();
			}
			String name = stringOr(invokeFlexible(species, "getName", "name", "showdownId"), normalizeSpecies(speciesId));
			String resource = stringOr(invokeFlexible(species, "resourceIdentifier", "getResourceIdentifier", "getName"), name);
			List<String> forms = readForms(species);
			List<String> aspects = readAspects(species);
			return Optional.of(new SpeciesInfo(resource.contains(":") ? resource : "cobblemon:" + resource, name, forms, aspects));
		} catch (ReflectiveOperationException e) {
			CbmCustomFishingAreaMod.LOGGER.debug("speciesInfo failed for {}", speciesId, e);
			return Optional.empty();
		}
	}

	public static List<String> listSpeciesIds(int limit) {
		List<String> out = new ArrayList<>();
		if (!isLoaded()) {
			return out;
		}
		try {
			Object registry = pokemonSpeciesRegistry();
			if (registry == null) {
				return out;
			}
			Collection<?> all = null;
			for (String methodName : List.of("getSpecies", "species", "all", "getAll")) {
				try {
					Object result = registry.getClass().getMethod(methodName).invoke(registry);
					if (result instanceof Collection<?> collection) {
						all = collection;
						break;
					}
					if (result instanceof Iterable<?> iterable) {
						all = new ArrayList<>();
						for (Object o : iterable) {
							((List<Object>) all).add(o);
						}
						break;
					}
				} catch (NoSuchMethodException ignored) {
				}
			}
			if (all == null) {
				return out;
			}
			for (Object species : all) {
				if (out.size() >= limit) {
					break;
				}
				String name = stringOr(invokeFlexible(species, "getName", "name", "showdownId"), null);
				if (name != null && !name.isBlank()) {
					out.add(name.toLowerCase(Locale.ROOT));
				}
			}
		} catch (ReflectiveOperationException e) {
			CbmCustomFishingAreaMod.LOGGER.debug("listSpeciesIds failed", e);
		}
		return out;
	}

	/**
	 * Create + deliver a Pokémon from pool entry.
	 * IVs/EVs/nature/ability are left to Cobblemon defaults (random).
	 */
	public static boolean grant(ServerPlayer player, PoolEntry entry, boolean detailed) {
		if (!isLoaded()) {
			player.displayClientMessage(net.minecraft.network.chat.Component.literal(
					"Cobblemon is not installed — Pokémon reward skipped."), true);
			return false;
		}
		try {
			String props = buildProperties(entry, detailed);
			Object pokemon = createPokemon(props);
			if (pokemon == null) {
				return false;
			}
			applyShinyChance(pokemon, entry, player);
			Delivery delivery = Delivery.from(entry.data("delivery", Delivery.PARTY_OR_PC.id()));
			if (delivery == Delivery.SPAWN_WORLD) {
				return spawnNearPlayer(player, pokemon);
			}
			return givePartyOrPc(player, pokemon);
		} catch (ReflectiveOperationException e) {
			CbmCustomFishingAreaMod.LOGGER.warn("Cobblemon grant failed for {}", entry.id, e);
			return false;
		}
	}

	private static String buildProperties(PoolEntry entry, boolean detailed) {
		String species = normalizeSpecies(entry.id);
		int min = Math.max(1, entry.dataInt("level_min", entry.dataInt("level", 5)));
		int max = Math.max(min, entry.dataInt("level_max", min));
		int level = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);

		StringBuilder sb = new StringBuilder(species);
		sb.append(" level=").append(level);

		if (detailed) {
			String form = entry.data("form", "");
			if (!form.isBlank() && !"normal".equalsIgnoreCase(form)) {
				sb.append(" form=").append(form);
			}
			String aspects = entry.data("aspects", "");
			if (!aspects.isBlank()) {
				for (String aspect : aspects.split("[,\\s]+")) {
					if (!aspect.isBlank()) {
						sb.append(' ').append(aspect.trim());
					}
				}
			}
		} else {
			String aspects = entry.data("aspects", "");
			if (!aspects.isBlank()) {
				for (String aspect : aspects.split("[,\\s]+")) {
					if (!aspect.isBlank()) {
						sb.append(' ').append(aspect.trim());
					}
				}
			}
		}
		return sb.toString();
	}

	private static void applyShinyChance(Object pokemon, PoolEntry entry, ServerPlayer player)
			throws ReflectiveOperationException {
		String mode = entry.data("shiny", entry.data("shiny_chance", "random"));
		boolean shiny;
		if ("always".equalsIgnoreCase(mode) || "true".equalsIgnoreCase(mode)) {
			shiny = true;
		} else if ("never".equalsIgnoreCase(mode) || "false".equalsIgnoreCase(mode)
				|| "random".equalsIgnoreCase(mode) || mode.isBlank()) {
			if ("random".equalsIgnoreCase(mode) || mode.isBlank()) {
				return; // leave Cobblemon default roll
			}
			shiny = false;
		} else {
			double chance;
			try {
				chance = Double.parseDouble(mode);
			} catch (NumberFormatException e) {
				chance = entry.dataDouble("shiny_chance", -1);
			}
			if (chance < 0) {
				return;
			}
			shiny = player.getRandom().nextDouble() * 100.0 < chance;
		}
		try {
			Method setShiny = pokemon.getClass().getMethod("setShiny", boolean.class);
			setShiny.invoke(pokemon, shiny);
		} catch (NoSuchMethodException e) {
			Field field = findField(pokemon.getClass(), "shiny");
			if (field != null) {
				field.setAccessible(true);
				field.setBoolean(pokemon, shiny);
			}
		}
	}

	private static Object createPokemon(String properties) throws ReflectiveOperationException {
		Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
		Object props;
		try {
			props = propsClass.getMethod("parse", String.class).invoke(null, properties);
		} catch (NoSuchMethodException e) {
			props = propsClass.getConstructor(String.class).newInstance(properties);
		}
		try {
			return propsClass.getMethod("create").invoke(props);
		} catch (NoSuchMethodException e) {
			return propsClass.getMethod("create", net.minecraft.server.level.ServerLevel.class)
					.invoke(props, (Object) null);
		}
	}

	private static boolean givePartyOrPc(ServerPlayer player, Object pokemon) throws ReflectiveOperationException {
		Object storage = playerDataManager();
		if (storage == null) {
			return false;
		}
		Object party = invokeFlexible(storage, "getParty", player);
		if (party == null) {
			party = invokeFlexible(storage, "party", player);
		}
		if (party != null && tryAdd(party, pokemon)) {
			return true;
		}
		Object pc = invokeFlexible(storage, "getPC", player);
		if (pc == null) {
			pc = invokeFlexible(storage, "pc", player);
		}
		return pc != null && tryAdd(pc, pokemon);
	}

	private static boolean tryAdd(Object store, Object pokemon) {
		for (String name : List.of("add", "addPokemon", "offer")) {
			try {
				Method m = store.getClass().getMethod(name, pokemon.getClass());
				Object result = m.invoke(store, pokemon);
				if (result instanceof Boolean b) {
					return b;
				}
				return true;
			} catch (ReflectiveOperationException ignored) {
			}
			try {
				Class<?> pokemonClass = Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
				Method m = store.getClass().getMethod(name, pokemonClass);
				Object result = m.invoke(store, pokemon);
				if (result instanceof Boolean b) {
					return b;
				}
				return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return false;
	}

	private static boolean spawnNearPlayer(ServerPlayer player, Object pokemon) throws ReflectiveOperationException {
		ServerLevel level = player.serverLevel();
		Vec3 pos = player.position().add(player.getLookAngle().scale(1.5)).add(0, 0.2, 0);
		if (player.fishing != null && !player.fishing.isRemoved()) {
			pos = player.fishing.position();
		}

		Object entity = null;
		for (String name : List.of("sendOut", "sendOutWithAnimation")) {
			try {
				Method m = pokemon.getClass().getMethod(name, ServerLevel.class, net.minecraft.core.BlockPos.class, java.util.function.Consumer.class);
				entity = m.invoke(pokemon, level, net.minecraft.core.BlockPos.containing(pos), (java.util.function.Consumer<?>) e -> {});
				break;
			} catch (NoSuchMethodException ignored) {
			}
		}
		if (entity == null) {
			try {
				Class<?> entityClass = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity");
				entity = entityClass.getConstructor(ServerLevel.class, pokemon.getClass())
						.newInstance(level, pokemon);
				Method setPos = entity.getClass().getMethod("moveTo", double.class, double.class, double.class, float.class, float.class);
				setPos.invoke(entity, pos.x, pos.y, pos.z, player.getYRot(), 0f);
				level.addFreshEntity((net.minecraft.world.entity.Entity) entity);
				return true;
			} catch (ReflectiveOperationException e) {
				CbmCustomFishingAreaMod.LOGGER.debug("spawn entity fallback failed", e);
				return false;
			}
		}
		return entity != null;
	}

	private static Object resolveSpecies(String name) throws ReflectiveOperationException {
		Object registry = pokemonSpeciesRegistry();
		if (registry == null) {
			return null;
		}
		for (String methodName : List.of("getByName", "getByIdentifier", "get")) {
			try {
				Method m = registry.getClass().getMethod(methodName, String.class);
				Object result = m.invoke(registry, name);
				if (result instanceof Optional<?> opt) {
					return opt.orElse(null);
				}
				if (result != null) {
					return result;
				}
			} catch (NoSuchMethodException ignored) {
			}
		}
		try {
			Class<?> rl = Class.forName("net.minecraft.resources.ResourceLocation");
			Object id;
			try {
				id = rl.getMethod("parse", String.class).invoke(null, name.contains(":") ? name : "cobblemon:" + name);
			} catch (NoSuchMethodException e) {
				id = rl.getMethod("tryParse", String.class).invoke(null, name.contains(":") ? name : "cobblemon:" + name);
			}
			Method m = registry.getClass().getMethod("getByIdentifier", rl);
			Object result = m.invoke(registry, id);
			if (result instanceof Optional<?> opt) {
				return opt.orElse(null);
			}
			return result;
		} catch (ReflectiveOperationException ignored) {
		}
		return null;
	}

	private static Object pokemonSpeciesRegistry() throws ReflectiveOperationException {
		Class<?> clazz = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
		for (String field : List.of("INSTANCE", "INSTANCE$")) {
			try {
				Field f = clazz.getField(field);
				return f.get(null);
			} catch (NoSuchFieldException ignored) {
			}
		}
		try {
			return clazz.getMethod("getInstance").invoke(null);
		} catch (NoSuchMethodException ignored) {
		}
		return clazz.getDeclaredConstructor().newInstance();
	}

	private static Object playerDataManager() throws ReflectiveOperationException {
		Class<?> cobblemon = Class.forName("com.cobblemon.mod.common.Cobblemon");
		Object instance = null;
		try {
			instance = cobblemon.getField("INSTANCE").get(null);
		} catch (NoSuchFieldException e) {
			try {
				instance = cobblemon.getMethod("getInstance").invoke(null);
			} catch (NoSuchMethodException ignored) {
			}
		}
		if (instance == null) {
			return null;
		}
		for (String name : List.of("getStorage", "getPlayerDataManager", "storage", "playerDataManager")) {
			try {
				return instance.getClass().getMethod(name).invoke(instance);
			} catch (NoSuchMethodException ignored) {
			}
			try {
				Field f = instance.getClass().getField(name);
				return f.get(instance);
			} catch (NoSuchFieldException ignored) {
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static List<String> readForms(Object species) throws ReflectiveOperationException {
		Set<String> forms = new LinkedHashSet<>();
		forms.add("normal");
		Object formsObj = null;
		for (String name : List.of("getForms", "forms")) {
			try {
				formsObj = species.getClass().getMethod(name).invoke(species);
				break;
			} catch (NoSuchMethodException ignored) {
			}
		}
		if (formsObj instanceof Iterable<?> iterable) {
			for (Object form : iterable) {
				String n = stringOr(invokeFlexible(form, "getName", "name", "formOnlyShowdownId"), null);
				if (n != null && !n.isBlank()) {
					forms.add(n.toLowerCase(Locale.ROOT));
				}
			}
		} else if (formsObj instanceof java.util.Map<?, ?> map) {
			for (Object key : map.keySet()) {
				if (key != null) {
					forms.add(key.toString().toLowerCase(Locale.ROOT));
				}
			}
		}
		return new ArrayList<>(forms);
	}

	private static List<String> readAspects(Object species) throws ReflectiveOperationException {
		Set<String> aspects = new LinkedHashSet<>();
		for (String name : List.of("getImplementedAspects", "getAspects", "aspects", "getFeatures", "features")) {
			try {
				Object result = species.getClass().getMethod(name).invoke(species);
				if (result instanceof Iterable<?> iterable) {
					for (Object item : iterable) {
						String value = item == null ? null : item.toString();
						if (value != null && !value.isBlank() && !value.contains("@")) {
							aspects.add(value.toLowerCase(Locale.ROOT));
						} else if (item != null) {
							String n = stringOr(invokeFlexible(item, "getName", "name", "getKey"), null);
							if (n != null) {
								aspects.add(n.toLowerCase(Locale.ROOT));
							}
						}
					}
					break;
				}
			} catch (NoSuchMethodException ignored) {
			}
		}
		return new ArrayList<>(aspects);
	}

	private static String normalizeSpecies(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.startsWith("cobblemon:")) {
			s = s.substring("cobblemon:".length());
		}
		return s;
	}

	private static String stringOr(Object value, String fallback) {
		return value == null ? fallback : value.toString();
	}

	private static Object invokeFlexible(Object target, String... names) throws ReflectiveOperationException {
		if (target == null) {
			return null;
		}
		if (target instanceof ServerPlayer player) {
			// overload path for storage lookups
			for (String name : names) {
				try {
					return target.getClass().getMethod(name, net.minecraft.world.entity.player.Player.class)
							.invoke(target, player);
				} catch (NoSuchMethodException ignored) {
				}
				try {
					return target.getClass().getMethod(name, ServerPlayer.class).invoke(target, player);
				} catch (NoSuchMethodException ignored) {
				}
				try {
					return target.getClass().getMethod(name, java.util.UUID.class).invoke(target, player.getUUID());
				} catch (NoSuchMethodException ignored) {
				}
			}
		}
		for (String name : names) {
			try {
				return target.getClass().getMethod(name).invoke(target);
			} catch (NoSuchMethodException ignored) {
			}
			try {
				Field field = target.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return field.get(target);
			} catch (NoSuchFieldException ignored) {
			}
		}
		// storage.getParty(player) style when target is storage and first name is getParty
		return null;
	}

	private static Object invokeFlexible(Object target, String name, ServerPlayer player) throws ReflectiveOperationException {
		for (Method method : target.getClass().getMethods()) {
			if (!method.getName().equals(name) || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> p = method.getParameterTypes()[0];
			if (p.isInstance(player) || p.getName().contains("Player") || p == java.util.UUID.class) {
				Object arg = p == java.util.UUID.class ? player.getUUID() : player;
				return method.invoke(target, arg);
			}
		}
		return null;
	}

	private static Field findField(Class<?> type, String name) {
		Class<?> cursor = type;
		while (cursor != null) {
			try {
				return cursor.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				cursor = cursor.getSuperclass();
			}
		}
		return null;
	}
}
