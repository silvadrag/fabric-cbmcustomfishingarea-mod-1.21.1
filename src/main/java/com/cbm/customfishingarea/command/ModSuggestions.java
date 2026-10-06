package com.cbm.customfishingarea.command;

import com.cbm.customfishingarea.area.AreaStatus;
import com.cbm.customfishingarea.area.AreaStorage;
import com.cbm.customfishingarea.area.FishingArea;
import com.cbm.customfishingarea.area.SelectionStore;
import com.cbm.customfishingarea.cobblemon.CobblemonBridge;
import com.cbm.customfishingarea.cobblemon.SpeciesCapability;
import com.cbm.customfishingarea.pool.PoolChannel;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardTypes;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Central tab-complete providers for the whole fishingarea command tree.
 */
public final class ModSuggestions {
	private ModSuggestions() {
	}

	/**
	 * Suggest area <strong>names only</strong> (never raw UUID).
	 * UUID stays in {@code areas.json} for uniqueness; players/editors use the friendly name.
	 */
	public static SuggestionProvider<CommandSourceStack> areas(boolean managerView) {
		return (ctx, builder) -> {
			var areas = managerView ? AreaStorage.all() : AreaStorage.listedForFishers();
			for (FishingArea area : areas) {
				suggestQuoted(builder, area.name);
			}
			return builder.buildFuture();
		};
	}

	/** Visualize targets: temp + area names (no UUID). */
	public static final SuggestionProvider<CommandSourceStack> VISUALIZE_TARGETS = (ctx, builder) -> {
		builder.suggest("temp");
		for (FishingArea area : AreaStorage.all()) {
			suggestQuoted(builder, area.name);
		}
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> AREA_STATUS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					Arrays.stream(AreaStatus.values()).map(s -> s.name().toLowerCase(Locale.ROOT)),
					builder
			);

	public static final SuggestionProvider<CommandSourceStack> AREA_SHAPE = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of("coordinates", "chunks"), builder);

	/** Common chunk radii with meaning baked into tooltips via plain values. */
	public static final SuggestionProvider<CommandSourceStack> CHUNK_RADIUS = (ctx, builder) -> {
		suggestInt(builder, 0);  // 1 chunk
		suggestInt(builder, 1);  // 3x3
		suggestInt(builder, 2);  // 5x5
		suggestInt(builder, 3);
		suggestInt(builder, 4);
		suggestInt(builder, 5);
		suggestInt(builder, 8);
		suggestInt(builder, 16);
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> ITEM_COUNT = (ctx, builder) -> {
		for (int n : new int[]{1, 4, 8, 16, 32, 64}) {
			suggestInt(builder, n);
		}
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> WEIGHT = (ctx, builder) -> {
		for (int n : new int[]{1, 5, 10, 25, 50, 100, 200, 500, 1000}) {
			suggestInt(builder, n);
		}
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> BOOLEANS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of("true", "false"), builder);

	public static final SuggestionProvider<CommandSourceStack> CHANNELS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					Arrays.stream(PoolChannel.values()).map(PoolChannel::id),
					builder
			);

	public static final SuggestionProvider<CommandSourceStack> XP_MODE = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of("points", "levels"), builder);

	public static final SuggestionProvider<CommandSourceStack> XP_AMOUNT = (ctx, builder) -> {
		for (int n : new int[]{1, 5, 10, 25, 50, 100, 250, 500}) {
			suggestInt(builder, n);
		}
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> SCORE_OP = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of("add", "set", "remove"), builder);

	public static final SuggestionProvider<CommandSourceStack> DELIVERY = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of("party_or_pc", "spawn_world"), builder);

	public static final SuggestionProvider<CommandSourceStack> SHINY = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of("random", "never", "always", "1", "5", "10"), builder);

	public static final SuggestionProvider<CommandSourceStack> LEVEL = (ctx, builder) -> {
		for (int n : new int[]{1, 5, 10, 15, 20, 30, 40, 50, 70, 100}) {
			suggestInt(builder, n);
		}
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> ITEM_TAGS = (ctx, builder) -> {
		String remaining = builder.getRemainingLowerCase();
		builder.suggest("minecraft:fishes");
		int suggested = 1;
		for (var pair : BuiltInRegistries.ITEM.getTags().toList()) {
			TagKey<Item> tag = pair.getFirst();
			String asString = tag.location().toString();
			if (SharedSuggestionProvider.matchesSubStr(remaining, asString)
					|| SharedSuggestionProvider.matchesSubStr(remaining, tag.location().getPath())) {
				builder.suggest(asString);
				if (++suggested >= 200) {
					break;
				}
			}
		}
		return builder.buildFuture();
	};

	public static final SuggestionProvider<CommandSourceStack> COBBLEMON_SPECIES = (ctx, builder) -> {
		String remaining = builder.getRemainingLowerCase();
		int suggested = 0;
		for (String species : CobblemonBridge.listSpeciesIds(500)) {
			if (SharedSuggestionProvider.matchesSubStr(remaining, species)) {
				builder.suggest(species);
				if (++suggested >= 200) {
					break;
				}
			}
		}
		return builder.buildFuture();
	};

	/** Forms for the species already typed as argument {@code species}. */
	public static SuggestionProvider<CommandSourceStack> speciesForms() {
		return (ctx, builder) -> {
			String species;
			try {
				species = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "species");
			} catch (Exception e) {
				return SharedSuggestionProvider.suggest(Stream.of("normal"), builder);
			}
			SpeciesCapability cap = SpeciesCapability.inspect(species);
			if (!cap.present()) {
				return SharedSuggestionProvider.suggest(Stream.of("normal"), builder);
			}
			return SharedSuggestionProvider.suggest(cap.forms().stream(), builder);
		};
	}

	/** Dynamic registry of pool reward types. */
	public static final SuggestionProvider<CommandSourceStack> REWARD_TYPES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					RewardTypes.all().stream().map(t -> t.id()),
					builder
			);

	/** Vanilla + mod items (path or full id substring match). */
	public static final SuggestionProvider<CommandSourceStack> ITEMS = (ctx, builder) -> {
		String remaining = builder.getRemainingLowerCase();
		int suggested = 0;
		for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
			String asString = id.toString();
			if (SharedSuggestionProvider.matchesSubStr(remaining, asString)
					|| SharedSuggestionProvider.matchesSubStr(remaining, id.getPath())) {
				builder.suggest(asString);
				if (++suggested >= 200) {
					break; // keep tab responsive on huge registries
				}
			}
		}
		return builder.buildFuture();
	};

	/** Existing area names (for create — helps avoid accidental duplicates). */
	public static final SuggestionProvider<CommandSourceStack> EXISTING_AREA_NAMES = (ctx, builder) -> {
		for (FishingArea area : AreaStorage.all()) {
			suggestQuoted(builder, area.name);
		}
		return builder.buildFuture();
	};

	/**
	 * Pool entry indices for the area already parsed as {@code id}.
	 * Format: {@code 0}, {@code 1}, … plus a human hint as separate name suggestions when possible.
	 */
	public static final SuggestionProvider<CommandSourceStack> POOL_INDEX = (ctx, builder) -> {
		Optional<FishingArea> area = resolveAreaArg(ctx, "id");
		if (area.isEmpty() || area.get().pool == null || area.get().pool.isEmpty()) {
			return builder.buildFuture();
		}
		var pool = area.get().pool;
		for (int i = 0; i < pool.size(); i++) {
			PoolEntry entry = pool.get(i);
			String label = i + ":" + entry.type + ":" + (entry.id == null ? "?" : entry.id);
			builder.suggest(String.valueOf(i));
			// also allow selecting via compact label if user types it
			if (SharedSuggestionProvider.matchesSubStr(builder.getRemainingLowerCase(), label.toLowerCase(Locale.ROOT))) {
				builder.suggest(String.valueOf(i));
			}
		}
		return builder.buildFuture();
	};

	/** Suggest "temp" chunk radius only when target is temp; otherwise still show common radii. */
	public static final SuggestionProvider<CommandSourceStack> VISUALIZE_CHUNK_RADIUS = CHUNK_RADIUS;

	/** Whether player currently has a temp marker selection — used as soft hint via "temp". */
	public static SuggestionProvider<CommandSourceStack> visualizeTargetsWithTempHint() {
		return (ctx, builder) -> {
			boolean hasTemp = false;
			try {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				hasTemp = SelectionStore.get(player).isPresent();
			} catch (CommandSyntaxException ignored) {
			}
			builder.suggest("off");
			builder.suggest("temp");
			for (FishingArea area : AreaStorage.all()) {
				suggestQuoted(builder, area.name);
			}
			return builder.buildFuture();
		};
	}

	private static Optional<FishingArea> resolveAreaArg(CommandContext<CommandSourceStack> ctx, String name) {
		try {
			return AreaStorage.byId(StringArgumentTypeGet(ctx, name));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static String StringArgumentTypeGet(CommandContext<CommandSourceStack> ctx, String name) {
		return com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, name);
	}

	private static void suggestQuoted(SuggestionsBuilder builder, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		if (value.indexOf(' ') >= 0 || value.indexOf(':') >= 0) {
			builder.suggest('"' + value + '"');
		} else {
			builder.suggest(value);
		}
	}

	private static void suggestInt(SuggestionsBuilder builder, int value) {
		String remaining = builder.getRemaining();
		String as = String.valueOf(value);
		if (remaining.isEmpty() || as.startsWith(remaining)) {
			builder.suggest(as);
		}
	}
}
