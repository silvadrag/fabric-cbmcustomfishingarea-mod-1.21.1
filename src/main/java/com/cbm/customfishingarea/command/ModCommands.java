package com.cbm.customfishingarea.command;

import com.cbm.customfishingarea.area.AreaShape;
import com.cbm.customfishingarea.area.AreaStatus;
import com.cbm.customfishingarea.area.AreaStorage;
import com.cbm.customfishingarea.area.AreaVisualizer;
import com.cbm.customfishingarea.area.FishingArea;
import com.cbm.customfishingarea.area.SelectionStore;
import com.cbm.customfishingarea.config.ModConfig;
import com.cbm.customfishingarea.gui.FishingMenus;
import com.cbm.customfishingarea.item.AreaMarkerItems;
import com.cbm.customfishingarea.permission.ModPermissions;
import com.cbm.customfishingarea.cobblemon.CobblemonBridge;
import com.cbm.customfishingarea.pool.PoolChannel;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardTypes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Role-split command tree with full tab-complete coverage.
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registry, Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fishingarea")
				.requires(ModPermissions::canUseRoot)
				.executes(ModCommands::rootHelp)
				.then(Commands.literal("help").executes(ModCommands::rootHelp))
				.then(fisherBranch())
				.then(manageBranch());

		var rootNode = dispatcher.register(root);
		dispatcher.register(Commands.literal("fa").requires(ModPermissions::canUseRoot).redirect(rootNode));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> fisherBranch() {
		return Commands.literal("fisher")
				.requires(ModPermissions::canFish)
				.executes(ModCommands::fisherHelp)
				.then(Commands.literal("help").executes(ModCommands::fisherHelp))
				.then(Commands.literal("gui")
						.requires(ModPermissions::isFisherPlayer)
						.executes(ctx -> {
							FishingMenus.openFisher(ctx.getSource().getPlayerOrException());
							return 1;
						}))
				.then(Commands.literal("list")
						.requires(ModPermissions::canFish)
						.executes(ctx -> listAreas(ctx, false)))
				.then(Commands.literal("info")
						.requires(ModPermissions::canFish)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(false))
								.executes(ctx -> infoArea(ctx, false))))
				.then(Commands.literal("where")
						.requires(ModPermissions::isFisherPlayer)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(false))
								.executes(ModCommands::whereArea)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> manageBranch() {
		LiteralArgumentBuilder<CommandSourceStack> manage = Commands.literal("manage")
				.requires(ModPermissions::canManage)
				.executes(ModCommands::manageHelp)
				.then(Commands.literal("help").executes(ModCommands::manageHelp))
				.then(Commands.literal("gui")
						.requires(ModPermissions::isManagerPlayer)
						.executes(ctx -> {
							FishingMenus.openManager(ctx.getSource().getPlayerOrException());
							return 1;
						}))
				.then(Commands.literal("marker")
						.requires(ModPermissions::isManagerPlayer)
						.executes(ModCommands::giveMarker))
				.then(Commands.literal("selection")
						.requires(ModPermissions::isManagerPlayer)
						.executes(ModCommands::showSelection)
						.then(Commands.literal("clear").executes(ModCommands::clearSelection)))
				.then(Commands.literal("create")
						.requires(ModPermissions::isManagerPlayer)
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests(ModSuggestions.EXISTING_AREA_NAMES)
								.then(Commands.argument("shape", StringArgumentType.word())
										.suggests(ModSuggestions.AREA_SHAPE)
										.executes(ModCommands::createArea))))
				.then(Commands.literal("visualize")
						.requires(ModPermissions::isManagerPlayer)
						.executes(ctx -> {
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Usage: /fishingarea manage visualize <name|temp|off> [coordinates|chunks]"
							), false);
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Permanent outline. 'temp' uses marker A→B. Shape defaults to chunks for temp."
							).withStyle(ChatFormatting.DARK_GRAY), false);
							return 1;
						})
						.then(Commands.literal("off").executes(ModCommands::visualizeOff))
						.then(Commands.literal("stop").executes(ModCommands::visualizeOff))
						.then(Commands.argument("target", StringArgumentType.string())
								.suggests(ModSuggestions.visualizeTargetsWithTempHint())
								.executes(ctx -> visualize(ctx, AreaShape.CHUNKS))
								.then(Commands.argument("shape", StringArgumentType.word())
										.suggests(ModSuggestions.AREA_SHAPE)
										.executes(ctx -> visualize(ctx, AreaShape.fromString(StringArgumentType.getString(ctx, "shape")))))))
				.then(Commands.literal("status")
						.requires(ModPermissions::canManage)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(true))
								.then(Commands.argument("status", StringArgumentType.word())
										.suggests(ModSuggestions.AREA_STATUS)
										.executes(ModCommands::setStatus))))
				.then(Commands.literal("delete")
						.requires(ModPermissions::canManage)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(true))
								.executes(ModCommands::deleteArea)))
				.then(Commands.literal("list")
						.requires(ModPermissions::canManage)
						.executes(ctx -> listAreas(ctx, true)))
				.then(Commands.literal("info")
						.requires(ModPermissions::canManage)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(true))
								.executes(ctx -> infoArea(ctx, true))))
				.then(buildPoolAdd())
				.then(Commands.literal("pool-remove")
						.requires(ModPermissions::canManage)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(true))
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.suggests(ModSuggestions.POOL_INDEX)
										.executes(ModCommands::poolRemove))))
				.then(Commands.literal("pool-clear")
						.requires(ModPermissions::canManage)
						.then(Commands.argument("id", StringArgumentType.string())
								.suggests(ModSuggestions.areas(true))
								.executes(ModCommands::poolClear)))
				.then(Commands.literal("reload")
						.requires(ModPermissions::canReload)
						.executes(ModCommands::reload))
				.then(Commands.literal("config")
						.requires(ModPermissions::canManage)
						.executes(ModCommands::configStatus)
						.then(Commands.literal("help").executes(ModCommands::configStatus))
						.then(Commands.literal("enable").executes(ctx -> setEnabled(ctx, true)))
						.then(Commands.literal("disable").executes(ctx -> setEnabled(ctx, false)))
						// Literals true/false — tab shows these words, never a cryptic <value>
						.then(Commands.literal("replace-vanilla")
								.executes(ctx -> explainReplace(ctx, true))
								.then(Commands.literal("true").executes(ctx -> setReplace(ctx, true, true)))
								.then(Commands.literal("false").executes(ctx -> setReplace(ctx, true, false))))
						.then(Commands.literal("replace-cobblemon")
								.executes(ctx -> explainReplace(ctx, false))
								.then(Commands.literal("true").executes(ctx -> setReplace(ctx, false, true)))
								.then(Commands.literal("false").executes(ctx -> setReplace(ctx, false, false)))));

		return manage;
	}

	/**
	 * Builds {@code pool-add <area> <type> ...} with a literal per registered reward type
	 * so tab-complete always matches the registry. Optional trailing {@code channel}.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> buildPoolAdd() {
		RequiredArgumentBuilder<CommandSourceStack, String> areaArg = Commands.argument("id", StringArgumentType.string())
				.suggests(ModSuggestions.areas(true));

		// item <itemId> [count] [weight] [channel]
		areaArg.then(Commands.literal("item")
				.then(Commands.argument("itemId", StringArgumentType.string())
						.suggests(ModSuggestions.ITEMS)
						.executes(ctx -> poolAddItem(ctx, 1, 100, PoolChannel.BOTH))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
								.suggests(ModSuggestions.ITEM_COUNT)
								.executes(ctx -> poolAddItem(ctx, IntegerArgumentType.getInteger(ctx, "count"), 100, PoolChannel.BOTH))
								.then(Commands.argument("weight", IntegerArgumentType.integer(1))
										.suggests(ModSuggestions.WEIGHT)
										.executes(ctx -> poolAddItem(ctx,
												IntegerArgumentType.getInteger(ctx, "count"),
												IntegerArgumentType.getInteger(ctx, "weight"),
												PoolChannel.BOTH))
										.then(channelArg(ctx -> poolAddItem(ctx,
												IntegerArgumentType.getInteger(ctx, "count"),
												IntegerArgumentType.getInteger(ctx, "weight"),
												channelOf(ctx))))))));

		// item_tag <tagId> [count] [weight] [channel]
		areaArg.then(Commands.literal("item_tag")
				.then(Commands.argument("tagId", StringArgumentType.string())
						.suggests(ModSuggestions.ITEM_TAGS)
						.executes(ctx -> poolAddItemTag(ctx, 1, 100, PoolChannel.BOTH))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
								.suggests(ModSuggestions.ITEM_COUNT)
								.executes(ctx -> poolAddItemTag(ctx, IntegerArgumentType.getInteger(ctx, "count"), 100, PoolChannel.BOTH))
								.then(Commands.argument("weight", IntegerArgumentType.integer(1))
										.suggests(ModSuggestions.WEIGHT)
										.executes(ctx -> poolAddItemTag(ctx,
												IntegerArgumentType.getInteger(ctx, "count"),
												IntegerArgumentType.getInteger(ctx, "weight"),
												PoolChannel.BOTH))
										.then(channelArg(ctx -> poolAddItemTag(ctx,
												IntegerArgumentType.getInteger(ctx, "count"),
												IntegerArgumentType.getInteger(ctx, "weight"),
												channelOf(ctx))))))));

		// xp <amount> [points|levels] [weight] [channel]
		areaArg.then(Commands.literal("xp")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.suggests(ModSuggestions.XP_AMOUNT)
						.executes(ctx -> poolAddXp(ctx, "points", 100, PoolChannel.BOTH))
						.then(Commands.argument("mode", StringArgumentType.word())
								.suggests(ModSuggestions.XP_MODE)
								.executes(ctx -> poolAddXp(ctx, StringArgumentType.getString(ctx, "mode"), 100, PoolChannel.BOTH))
								.then(Commands.argument("weight", IntegerArgumentType.integer(1))
										.suggests(ModSuggestions.WEIGHT)
										.executes(ctx -> poolAddXp(ctx,
												StringArgumentType.getString(ctx, "mode"),
												IntegerArgumentType.getInteger(ctx, "weight"),
												PoolChannel.BOTH))
										.then(channelArg(ctx -> poolAddXp(ctx,
												StringArgumentType.getString(ctx, "mode"),
												IntegerArgumentType.getInteger(ctx, "weight"),
												channelOf(ctx))))))));

		// nothing [weight] [channel]
		areaArg.then(Commands.literal("nothing")
				.executes(ctx -> poolAddNothing(ctx, 100, PoolChannel.BOTH))
				.then(Commands.argument("weight", IntegerArgumentType.integer(1))
						.suggests(ModSuggestions.WEIGHT)
						.executes(ctx -> poolAddNothing(ctx, IntegerArgumentType.getInteger(ctx, "weight"), PoolChannel.BOTH))
						.then(channelArg(ctx -> poolAddNothing(ctx,
								IntegerArgumentType.getInteger(ctx, "weight"), channelOf(ctx))))));

		// command "<cmd>" [weight] [channel]
		areaArg.then(Commands.literal("command")
				.then(Commands.argument("commandText", StringArgumentType.string())
						.executes(ctx -> poolAddCommand(ctx, 100, PoolChannel.BOTH))
						.then(Commands.argument("weight", IntegerArgumentType.integer(1))
								.suggests(ModSuggestions.WEIGHT)
								.executes(ctx -> poolAddCommand(ctx, IntegerArgumentType.getInteger(ctx, "weight"), PoolChannel.BOTH))
								.then(channelArg(ctx -> poolAddCommand(ctx,
										IntegerArgumentType.getInteger(ctx, "weight"), channelOf(ctx)))))));

		// scoreboard <objective> [amount] [op] [weight] [channel]
		areaArg.then(Commands.literal("scoreboard")
				.then(Commands.argument("objective", StringArgumentType.word())
						.executes(ctx -> poolAddScoreboard(ctx, 1, "add", 100, PoolChannel.BOTH))
						.then(Commands.argument("amount", IntegerArgumentType.integer())
								.executes(ctx -> poolAddScoreboard(ctx,
										IntegerArgumentType.getInteger(ctx, "amount"), "add", 100, PoolChannel.BOTH))
								.then(Commands.argument("op", StringArgumentType.word())
										.suggests(ModSuggestions.SCORE_OP)
										.executes(ctx -> poolAddScoreboard(ctx,
												IntegerArgumentType.getInteger(ctx, "amount"),
												StringArgumentType.getString(ctx, "op"), 100, PoolChannel.BOTH))
										.then(Commands.argument("weight", IntegerArgumentType.integer(1))
												.suggests(ModSuggestions.WEIGHT)
												.executes(ctx -> poolAddScoreboard(ctx,
														IntegerArgumentType.getInteger(ctx, "amount"),
														StringArgumentType.getString(ctx, "op"),
														IntegerArgumentType.getInteger(ctx, "weight"),
														PoolChannel.BOTH))
												.then(channelArg(ctx -> poolAddScoreboard(ctx,
														IntegerArgumentType.getInteger(ctx, "amount"),
														StringArgumentType.getString(ctx, "op"),
														IntegerArgumentType.getInteger(ctx, "weight"),
														channelOf(ctx)))))))));

		// economy <currency> <amount> [weight] [channel]
		areaArg.then(Commands.literal("economy")
				.then(Commands.argument("currency", StringArgumentType.word())
						.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
								.executes(ctx -> poolAddEconomy(ctx, 100, PoolChannel.BOTH))
								.then(Commands.argument("weight", IntegerArgumentType.integer(1))
										.suggests(ModSuggestions.WEIGHT)
										.executes(ctx -> poolAddEconomy(ctx,
												IntegerArgumentType.getInteger(ctx, "weight"), PoolChannel.BOTH))
										.then(channelArg(ctx -> poolAddEconomy(ctx,
												IntegerArgumentType.getInteger(ctx, "weight"), channelOf(ctx))))))));

		// cobblemon_species <species> [levelMin] [levelMax] [delivery] [weight] [channel]
		RequiredArgumentBuilder<CommandSourceStack, String> speciesArg = Commands.argument("species", StringArgumentType.word())
				.suggests(ModSuggestions.COBBLEMON_SPECIES)
				.executes(ctx -> poolAddCobblemon(ctx, false, 5, 5, "party_or_pc", 100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, Integer> spLevelMin = Commands.argument("levelMin", IntegerArgumentType.integer(1, 100))
				.suggests(ModSuggestions.LEVEL)
				.executes(ctx -> poolAddCobblemon(ctx, false,
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						"party_or_pc", 100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, Integer> spLevelMax = Commands.argument("levelMax", IntegerArgumentType.integer(1, 100))
				.suggests(ModSuggestions.LEVEL)
				.executes(ctx -> poolAddCobblemon(ctx, false,
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						IntegerArgumentType.getInteger(ctx, "levelMax"),
						"party_or_pc", 100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, String> spDelivery = Commands.argument("delivery", StringArgumentType.word())
				.suggests(ModSuggestions.DELIVERY)
				.executes(ctx -> poolAddCobblemon(ctx, false,
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						IntegerArgumentType.getInteger(ctx, "levelMax"),
						StringArgumentType.getString(ctx, "delivery"),
						100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, Integer> spWeight = Commands.argument("weight", IntegerArgumentType.integer(1))
				.suggests(ModSuggestions.WEIGHT)
				.executes(ctx -> poolAddCobblemon(ctx, false,
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						IntegerArgumentType.getInteger(ctx, "levelMax"),
						StringArgumentType.getString(ctx, "delivery"),
						IntegerArgumentType.getInteger(ctx, "weight"),
						PoolChannel.COBBLEMON));
		spWeight.then(channelArg(ctx -> poolAddCobblemon(ctx, false,
				IntegerArgumentType.getInteger(ctx, "levelMin"),
				IntegerArgumentType.getInteger(ctx, "levelMax"),
				StringArgumentType.getString(ctx, "delivery"),
				IntegerArgumentType.getInteger(ctx, "weight"),
				channelOf(ctx))));
		spDelivery.then(spWeight);
		spLevelMax.then(spDelivery);
		spLevelMin.then(spLevelMax);
		speciesArg.then(spLevelMin);
		areaArg.then(Commands.literal("cobblemon_species").then(speciesArg));

		// cobblemon_pokemon <species> [form] [levelMin] [levelMax] [shiny] [delivery] [weight] [channel]
		RequiredArgumentBuilder<CommandSourceStack, String> pokeSpecies = Commands.argument("species", StringArgumentType.word())
				.suggests(ModSuggestions.COBBLEMON_SPECIES)
				.executes(ctx -> poolAddCobblemonPokemon(ctx, "normal", 5, 15, "random", "party_or_pc", 100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, String> pokeForm = Commands.argument("form", StringArgumentType.word())
				.suggests(ModSuggestions.speciesForms())
				.executes(ctx -> poolAddCobblemonPokemon(ctx,
						StringArgumentType.getString(ctx, "form"), 5, 15, "random", "party_or_pc", 100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, Integer> pokeLevelMin = Commands.argument("levelMin", IntegerArgumentType.integer(1, 100))
				.suggests(ModSuggestions.LEVEL);
		RequiredArgumentBuilder<CommandSourceStack, Integer> pokeLevelMax = Commands.argument("levelMax", IntegerArgumentType.integer(1, 100))
				.suggests(ModSuggestions.LEVEL);
		RequiredArgumentBuilder<CommandSourceStack, String> pokeShiny = Commands.argument("shiny", StringArgumentType.word())
				.suggests(ModSuggestions.SHINY);
		RequiredArgumentBuilder<CommandSourceStack, String> pokeDelivery = Commands.argument("delivery", StringArgumentType.word())
				.suggests(ModSuggestions.DELIVERY)
				.executes(ctx -> poolAddCobblemonPokemon(ctx,
						StringArgumentType.getString(ctx, "form"),
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						IntegerArgumentType.getInteger(ctx, "levelMax"),
						StringArgumentType.getString(ctx, "shiny"),
						StringArgumentType.getString(ctx, "delivery"),
						100, PoolChannel.COBBLEMON));
		RequiredArgumentBuilder<CommandSourceStack, Integer> pokeWeight = Commands.argument("weight", IntegerArgumentType.integer(1))
				.suggests(ModSuggestions.WEIGHT)
				.executes(ctx -> poolAddCobblemonPokemon(ctx,
						StringArgumentType.getString(ctx, "form"),
						IntegerArgumentType.getInteger(ctx, "levelMin"),
						IntegerArgumentType.getInteger(ctx, "levelMax"),
						StringArgumentType.getString(ctx, "shiny"),
						StringArgumentType.getString(ctx, "delivery"),
						IntegerArgumentType.getInteger(ctx, "weight"),
						PoolChannel.COBBLEMON));
		pokeWeight.then(channelArg(ctx -> poolAddCobblemonPokemon(ctx,
				StringArgumentType.getString(ctx, "form"),
				IntegerArgumentType.getInteger(ctx, "levelMin"),
				IntegerArgumentType.getInteger(ctx, "levelMax"),
				StringArgumentType.getString(ctx, "shiny"),
				StringArgumentType.getString(ctx, "delivery"),
				IntegerArgumentType.getInteger(ctx, "weight"),
				channelOf(ctx))));
		pokeDelivery.then(pokeWeight);
		pokeShiny.then(pokeDelivery);
		pokeLevelMax.then(pokeShiny);
		pokeLevelMin.then(pokeLevelMax);
		pokeForm.then(pokeLevelMin);
		pokeSpecies.then(pokeForm);
		areaArg.then(Commands.literal("cobblemon_pokemon").then(pokeSpecies));

		return Commands.literal("pool-add")
				.requires(ModPermissions::canManage)
				.then(areaArg);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> channelArg(
			com.mojang.brigadier.Command<CommandSourceStack> command) {
		return Commands.argument("channel", StringArgumentType.word())
				.suggests(ModSuggestions.CHANNELS)
				.executes(command);
	}

	private static PoolChannel channelOf(CommandContext<CommandSourceStack> ctx) {
		return PoolChannel.fromString(StringArgumentType.getString(ctx, "channel"));
	}

	private static int rootHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("=== Fishing Area ===").withStyle(ChatFormatting.GOLD), false);

		boolean fish = ModPermissions.canFish(source);
		boolean manage = ModPermissions.canManage(source);

		if (fish) {
			source.sendSuccess(() -> Component.literal("Role: FISHER").withStyle(ChatFormatting.AQUA), false);
			source.sendSuccess(() -> Component.literal("  /fishingarea fisher help"), false);
		}
		if (manage) {
			source.sendSuccess(() -> Component.literal("Role: MANAGE").withStyle(ChatFormatting.GREEN), false);
			source.sendSuccess(() -> Component.literal("  /fishingarea manage help"), false);
		}
		if (!fish && !manage) {
			source.sendFailure(Component.literal("No fishingarea role permission."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Rule: bobber chunk decides (player can stand outside).").withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	private static int fisherHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("=== Fisher commands ===").withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> Component.literal("/fishingarea fisher gui|list"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea fisher info <id|name>"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea fisher where <id|name>"), false);
		return 1;
	}

	private static int manageHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("=== Manage commands ===").withStyle(ChatFormatting.GREEN), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage gui|marker|selection"), false);
		source.sendSuccess(() -> Component.literal("Marker: click A then B (does not break blocks)"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage create <name> coordinates|chunks"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage visualize <name|temp|off> [coordinates|chunks]"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage status <id|name> <open|maintenance|hidden>"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage delete|list|info …"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage pool-add <area> <type> … [channel]"), false);
		source.sendSuccess(() -> Component.literal("  types: item|item_tag|xp|nothing|command|scoreboard|economy"), false);
		source.sendSuccess(() -> Component.literal("         cobblemon_species|cobblemon_pokemon"), false);
		source.sendSuccess(() -> Component.literal("  channel: both|vanilla|cobblemon (default both / cobblemon for pokemon)"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage pool-remove <id|name> <index>"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage pool-clear <id|name>"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage reload"), false);
		source.sendSuccess(() -> Component.literal("/fishingarea manage config"), false);
		source.sendSuccess(() -> Component.literal("  enable | disable"), false);
		source.sendSuccess(() -> Component.literal("  replace-vanilla true|false   — gate vanilla fishing rod"), false);
		source.sendSuccess(() -> Component.literal("  replace-cobblemon true|false — gate Cobblemon Poké Rod"), false);
		source.sendSuccess(() -> Component.literal("Tip: press Tab — options appear as words (true/false, area names, items…).")
				.withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		ModConfig.load();
		AreaStorage.load();
		ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config + areas."), true);
		return 1;
	}

	private static int giveMarker(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		player.getInventory().add(AreaMarkerItems.create());
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Gave A→B marker. Click corner A, then corner B (blocks stay safe). Then: create <name> coordinates|chunks"
		), false);
		return 1;
	}

	private static int showSelection(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ctx.getSource().sendSuccess(() -> Component.literal(SelectionStore.describe(player)), false);
		return 1;
	}

	private static int clearSelection(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		SelectionStore.clear(ctx.getSource().getPlayerOrException());
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared marker selection A/B."), false);
		return 1;
	}

	private static int createArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		var sel = SelectionStore.getComplete(player);
		if (sel.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal(
					"Need both corners. Use marker: click A, then click B. Check with /fishingarea manage selection"
			));
			return 0;
		}
		AreaShape shape;
		try {
			shape = AreaShape.fromString(StringArgumentType.getString(ctx, "shape"));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Shape must be: coordinates  or  chunks"));
			return 0;
		}
		FishingArea area = FishingArea.fromSelection(
				StringArgumentType.getString(ctx, "name"),
				shape,
				sel.get(),
				player.getGameProfile().getName()
		);
		area.status = AreaStatus.OPEN;
		AreaStorage.upsert(area);
		SelectionStore.clear(player);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Created '" + area.name + "' (" + area.shape.label() + ") " + area.describeBounds()
						+ ". Pool empty → add rewards with pool-add."
		), true);
		AreaVisualizer.show(player, area);
		return 1;
	}

	private static int visualizeOff(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		AreaVisualizer.stop(ctx.getSource().getPlayerOrException());
		return 1;
	}

	private static int visualize(CommandContext<CommandSourceStack> ctx, AreaShape tempShape) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String target = StringArgumentType.getString(ctx, "target");
		if ("off".equalsIgnoreCase(target) || "stop".equalsIgnoreCase(target)) {
			AreaVisualizer.stop(player);
			return 1;
		}
		if ("temp".equalsIgnoreCase(target)) {
			var sel = SelectionStore.getComplete(player);
			if (sel.isEmpty()) {
				ctx.getSource().sendFailure(Component.literal("Need marker corners A and B first."));
				return 0;
			}
			AreaVisualizer.showSelection(player, sel.get(), tempShape);
			return 1;
		}
		var areaOpt = AreaStorage.byId(target);
		if (areaOpt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + target));
			return 0;
		}
		AreaVisualizer.show(player, areaOpt.get());
		return 1;
	}

	private static int setStatus(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		String statusRaw = StringArgumentType.getString(ctx, "status");
		var areaOpt = AreaStorage.byId(id);
		if (areaOpt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		FishingArea area = areaOpt.get();
		try {
			area.status = AreaStatus.fromString(statusRaw);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Status must be open|maintenance|hidden"));
			return 0;
		}
		AreaStorage.upsert(area);
		ctx.getSource().sendSuccess(() -> Component.literal("Set " + area.name + " → " + area.status), true);
		return 1;
	}

	private static int deleteArea(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		if (!AreaStorage.delete(id)) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Deleted area " + id), true);
		return 1;
	}

	private static int listAreas(CommandContext<CommandSourceStack> ctx, boolean managerView) {
		var areas = managerView ? AreaStorage.all() : AreaStorage.listedForFishers();
		String title = managerView ? "All areas (manage):" : "Listed areas (fisher):";
		ctx.getSource().sendSuccess(() -> Component.literal(title).withStyle(managerView ? ChatFormatting.GREEN : ChatFormatting.AQUA), false);
		if (areas.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("  (none)"), false);
			return 1;
		}
		for (FishingArea area : areas) {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"- " + area.name + " [" + area.status.name().toLowerCase() + "] "
							+ area.shape.label() + " " + area.describeBounds()
							+ " | rewards=" + (area.pool == null ? 0 : area.pool.size())
			), false);
		}
		return areas.size();
	}

	private static int infoArea(CommandContext<CommandSourceStack> ctx, boolean managerView) {
		String id = StringArgumentType.getString(ctx, "id");
		var areaOpt = AreaStorage.byId(id);
		if (areaOpt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		FishingArea area = areaOpt.get();
		if (!managerView && !area.status.isListedForFishers()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.literal("=== " + area.name + " ===").withStyle(ChatFormatting.GOLD), false);
		ctx.getSource().sendSuccess(() -> Component.literal("Status: " + area.status.name().toLowerCase()), false);
		ctx.getSource().sendSuccess(() -> Component.literal("World: " + area.dimension), false);
		ctx.getSource().sendSuccess(() -> Component.literal("Type: " + area.shape.label()), false);
		ctx.getSource().sendSuccess(() -> Component.literal("Bounds: " + area.describeBounds()), false);
		if (managerView) {
			ctx.getSource().sendSuccess(() -> Component.literal("Created by: " + area.createdBy), false);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Rewards:"), false);
		if (area.pool == null || area.pool.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("  (empty)").withStyle(ChatFormatting.DARK_GRAY), false);
		} else {
			for (int i = 0; i < area.pool.size(); i++) {
				PoolEntry entry = area.pool.get(i);
				final int index = i;
				ctx.getSource().sendSuccess(() -> RewardTypes.get(entry.type)
						.map(t -> Component.literal("  [" + index + "] ").append(t.describe(entry)))
						.orElse(Component.literal("  [" + index + "] " + entry.type + ":" + entry.id)), false);
			}
		}
		return 1;
	}

	private static int whereArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		var areaOpt = AreaStorage.byId(id);
		if (areaOpt.isEmpty() || !areaOpt.get().status.isListedForFishers()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		FishingArea area = areaOpt.get();
		boolean sameDim = player.level().dimension().location().toString().equals(area.dimension);
		boolean inside = sameDim && area.contains(
				player.level().dimension().location(), player.getX(), player.getY(), player.getZ());

		ctx.getSource().sendSuccess(() -> Component.literal(
				area.name + " (" + area.shape.label() + ") " + area.describeBounds()
						+ (sameDim ? (inside ? " | you are INSIDE" : " | you are OUTSIDE")
						: " | (different dimension)")
						+ " — fishing checks the BOBBER position"
		).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int poolAddItem(CommandContext<CommandSourceStack> ctx, int count, int weight, PoolChannel channel) {
		return addValidated(ctx, "item", entry -> {
			String itemId = StringArgumentType.getString(ctx, "itemId").trim();
			entry.id = itemId.contains(":") ? itemId : "minecraft:" + itemId;
			entry.count = count;
			entry.weight = weight;
			entry.setChannel(channel);
		});
	}

	private static int poolAddItemTag(CommandContext<CommandSourceStack> ctx, int count, int weight, PoolChannel channel) {
		return addValidated(ctx, "item_tag", entry -> {
			String tagId = StringArgumentType.getString(ctx, "tagId").trim();
			if (tagId.startsWith("#")) {
				tagId = tagId.substring(1);
			}
			entry.id = tagId.contains(":") ? tagId : "minecraft:" + tagId;
			entry.count = count;
			entry.weight = weight;
			entry.setChannel(channel);
		});
	}

	private static int poolAddXp(CommandContext<CommandSourceStack> ctx, String mode, int weight, PoolChannel channel) {
		return addValidated(ctx, "xp", entry -> {
			entry.id = "xp";
			entry.count = IntegerArgumentType.getInteger(ctx, "amount");
			entry.weight = weight;
			entry.putData("mode", mode);
			entry.setChannel(channel);
		});
	}

	private static int poolAddNothing(CommandContext<CommandSourceStack> ctx, int weight, PoolChannel channel) {
		return addValidated(ctx, "nothing", entry -> {
			entry.id = "nothing";
			entry.weight = weight;
			entry.setChannel(channel);
		});
	}

	private static int poolAddCommand(CommandContext<CommandSourceStack> ctx, int weight, PoolChannel channel) {
		String raw = StringArgumentType.getString(ctx, "commandText").trim();
		final String commandText = raw.startsWith("/") ? raw.substring(1) : raw;
		return addValidated(ctx, "command", entry -> {
			entry.id = commandText;
			entry.putData("command", commandText);
			entry.weight = weight;
			entry.setChannel(channel);
		});
	}

	private static int poolAddScoreboard(CommandContext<CommandSourceStack> ctx, int amount, String op, int weight, PoolChannel channel) {
		return addValidated(ctx, "scoreboard", entry -> {
			entry.id = StringArgumentType.getString(ctx, "objective");
			entry.count = amount;
			entry.weight = weight;
			entry.putData("op", op);
			entry.setChannel(channel);
		});
	}

	private static int poolAddEconomy(CommandContext<CommandSourceStack> ctx, int weight, PoolChannel channel) {
		return addValidated(ctx, "economy", entry -> {
			String currency = StringArgumentType.getString(ctx, "currency");
			double amount = DoubleArgumentType.getDouble(ctx, "amount");
			entry.id = currency;
			entry.count = Math.max(1, (int) Math.round(amount));
			entry.weight = weight;
			entry.putData("currency", currency);
			entry.putData("amount", String.valueOf(amount));
			entry.setChannel(channel);
		});
	}

	private static int poolAddCobblemon(CommandContext<CommandSourceStack> ctx, boolean detailed,
			int levelMin, int levelMax, String delivery, int weight, PoolChannel channel) {
		return addValidated(ctx, "cobblemon_species", entry -> {
			entry.id = StringArgumentType.getString(ctx, "species").toLowerCase();
			entry.weight = weight;
			entry.putData("level_min", String.valueOf(levelMin));
			entry.putData("level_max", String.valueOf(Math.max(levelMin, levelMax)));
			entry.putData("delivery", CobblemonBridge.Delivery.from(delivery).id());
			entry.setChannel(channel);
		});
	}

	private static int poolAddCobblemonPokemon(CommandContext<CommandSourceStack> ctx,
			String form, int levelMin, int levelMax, String shiny, String delivery, int weight, PoolChannel channel) {
		return addValidated(ctx, "cobblemon_pokemon", entry -> {
			entry.id = StringArgumentType.getString(ctx, "species").toLowerCase();
			entry.weight = weight;
			entry.putData("form", form);
			entry.putData("level_min", String.valueOf(levelMin));
			entry.putData("level_max", String.valueOf(Math.max(levelMin, levelMax)));
			entry.putData("shiny", shiny);
			entry.putData("delivery", CobblemonBridge.Delivery.from(delivery).id());
			entry.setChannel(channel);
		});
	}

	@FunctionalInterface
	private interface EntryConfigurer {
		void configure(PoolEntry entry);
	}

	private static int addValidated(CommandContext<CommandSourceStack> ctx, String type, EntryConfigurer configurer) {
		String id = StringArgumentType.getString(ctx, "id");
		var areaOpt = AreaStorage.byId(id);
		if (areaOpt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		FishingArea area = areaOpt.get();
		PoolEntry entry = new PoolEntry();
		entry.type = type;
		entry.setChannel(PoolChannel.BOTH);
		configurer.configure(entry);
		var validation = RewardTypes.get(type).flatMap(t -> t.validate(entry));
		if (validation.isPresent()) {
			ctx.getSource().sendFailure(validation.get());
			return 0;
		}
		area.pool.add(entry);
		AreaStorage.upsert(area);
		final int index = area.pool.size() - 1;
		ctx.getSource().sendSuccess(() -> RewardTypes.get(type)
				.map(t -> Component.literal("Added [" + index + "] ").append(t.describe(entry)).append(" to " + area.name))
				.orElse(Component.literal("Added [" + index + "] " + type + ":" + entry.id)), true);
		return 1;
	}

	private static int poolRemove(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		int index = IntegerArgumentType.getInteger(ctx, "index");
		var areaOpt = AreaStorage.byId(id);
		if (areaOpt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		FishingArea area = areaOpt.get();
		if (area.pool == null || index < 0 || index >= area.pool.size()) {
			ctx.getSource().sendFailure(Component.literal("Invalid pool index. Use /fishingarea manage info <area> to see indices."));
			return 0;
		}
		PoolEntry removed = area.pool.remove(index);
		AreaStorage.upsert(area);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Removed [" + index + "] " + removed.type + ":" + removed.id + " from " + area.name
		), true);
		return 1;
	}

	private static int poolClear(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		var areaOpt = AreaStorage.byId(id);
		if (areaOpt.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Area not found: " + id));
			return 0;
		}
		FishingArea area = areaOpt.get();
		area.pool.clear();
		AreaStorage.upsert(area);
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared pool for " + area.name), true);
		return 1;
	}

	private static int configStatus(CommandContext<CommandSourceStack> ctx) {
		var cfg = ModConfig.get();
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("=== Fishing config ===").withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> Component.literal(
				"Custom fishing system: " + onOff(cfg.customFishingEnabled)
						+ (cfg.customFishingEnabled
						? " — only fishing areas control catches"
						: " — normal Minecraft/Cobblemon fishing")
		), false);
		source.sendSuccess(() -> Component.literal(
				"Gate vanilla fishing rod: " + onOff(cfg.replaceVanillaFishing)
						+ "  (/fishingarea manage config replace-vanilla true|false)"
		), false);
		source.sendSuccess(() -> Component.literal(
				"Gate Cobblemon Poké Rod: " + onOff(cfg.replaceCobblemonFishing)
						+ "  (/fishingarea manage config replace-cobblemon true|false)"
		), false);
		source.sendSuccess(() -> Component.literal("Meaning:").withStyle(ChatFormatting.YELLOW), false);
		source.sendSuccess(() -> Component.literal("  enable  = turn ON area fishing rules"), false);
		source.sendSuccess(() -> Component.literal("  disable = turn OFF (everything fishes normally again)"), false);
		source.sendSuccess(() -> Component.literal("  replace-vanilla true  = vanilla rod must follow areas"), false);
		source.sendSuccess(() -> Component.literal("  replace-vanilla false = vanilla rod ignores areas"), false);
		source.sendSuccess(() -> Component.literal("  replace-cobblemon true  = Poké Rod must follow areas"), false);
		source.sendSuccess(() -> Component.literal("  replace-cobblemon false = Poké Rod ignores areas"), false);
		source.sendSuccess(() -> Component.literal("Press Tab after replace-vanilla / replace-cobblemon to pick true or false.")
				.withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	private static int explainReplace(CommandContext<CommandSourceStack> ctx, boolean vanilla) {
		String key = vanilla ? "replace-vanilla" : "replace-cobblemon";
		String rod = vanilla ? "vanilla fishing rod" : "Cobblemon Poké Rod";
		boolean current = vanilla ? ModConfig.get().replaceVanillaFishing : ModConfig.get().replaceCobblemonFishing;
		ctx.getSource().sendSuccess(() -> Component.literal(
				key + " (currently " + onOff(current) + ")"
		).withStyle(ChatFormatting.YELLOW), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Choose: /fishingarea manage config " + key + " true|false"
		), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"  true  → " + rod + " only works inside fishing areas (when system is enabled)"
		), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"  false → " + rod + " fishes normally, ignores areas"
		), false);
		ctx.getSource().sendSuccess(() -> Component.literal("Tip: press Tab to autocomplete true / false.")
				.withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		ModConfig.get().customFishingEnabled = enabled;
		ModConfig.save();
		if (enabled) {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"Custom fishing ON. Outside areas = no catch. Inside areas = pool rewards only."
			).withStyle(ChatFormatting.GREEN), true);
		} else {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"Custom fishing OFF. Vanilla + Cobblemon fishing behave normally again."
			).withStyle(ChatFormatting.YELLOW), true);
		}
		return 1;
	}

	private static int setReplace(CommandContext<CommandSourceStack> ctx, boolean vanilla, boolean enabled) {
		if (vanilla) {
			ModConfig.get().replaceVanillaFishing = enabled;
			ModConfig.save();
			ctx.getSource().sendSuccess(() -> Component.literal(enabled
					? "Vanilla fishing rod: GATED by fishing areas (needs config enable)."
					: "Vanilla fishing rod: NOT gated — fishes normally even if system is on."
			), true);
		} else {
			ModConfig.get().replaceCobblemonFishing = enabled;
			ModConfig.save();
			ctx.getSource().sendSuccess(() -> Component.literal(enabled
					? "Cobblemon Poké Rod: GATED by fishing areas (needs config enable)."
					: "Cobblemon Poké Rod: NOT gated — fishes normally even if system is on."
			), true);
		}
		if (!ModConfig.get().customFishingEnabled) {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"Note: custom fishing is still OFF. Run /fishingarea manage config enable to apply area rules."
			).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}
}
