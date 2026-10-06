package com.cbm.customfishingarea.gui;

import com.cbm.customfishingarea.area.AreaStorage;
import com.cbm.customfishingarea.area.AreaStatus;
import com.cbm.customfishingarea.area.AreaVisualizer;
import com.cbm.customfishingarea.area.FishingArea;
import com.cbm.customfishingarea.area.SelectionStore;
import com.cbm.customfishingarea.cobblemon.CobblemonBridge;
import com.cbm.customfishingarea.cobblemon.SpeciesCapability;
import com.cbm.customfishingarea.permission.ModPermissions;
import com.cbm.customfishingarea.pool.PoolChannel;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import com.cbm.customfishingarea.pool.RewardTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Server chest GUIs only (vanilla {@link ChestMenu}).
 * Manager flows mirror commands; Cobblemon options auto enable/disable via {@link SpeciesCapability}.
 */
public final class FishingMenus {
	private FishingMenus() {
	}

	public static void openFisher(ServerPlayer player) {
		if (!ModPermissions.canFish(player)) {
			player.displayClientMessage(Component.literal("Missing permission to view fishing areas."), true);
			return;
		}

		List<FishingArea> listed = AreaStorage.listedForFishers();
		SimpleContainer container = new SimpleContainer(27);
		int slot = 0;
		for (FishingArea area : listed) {
			if (slot >= 27) {
				break;
			}
			container.setItem(slot++, fisherIcon(area, player));
		}
		if (listed.isEmpty()) {
			ItemStack empty = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
			empty.set(DataComponents.CUSTOM_NAME, Component.literal("No listed fishing areas"));
			container.setItem(13, empty);
		}

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> clickableChest(MenuType.GENERIC_9x3, id, inv, container, 3, Map.of()),
				Component.literal("Fishing Areas")
		));
	}

	public static void openManager(ServerPlayer player) {
		if (!ModPermissions.canManage(player)) {
			player.displayClientMessage(Component.literal("Missing permission to manage fishing areas."), true);
			return;
		}

		List<FishingArea> all = AreaStorage.all();
		SimpleContainer container = new SimpleContainer(54);
		Map<Integer, Consumer<ServerPlayer>> clicks = new HashMap<>();
		int slot = 0;
		for (FishingArea area : all) {
			if (slot >= 45) {
				break;
			}
			final String areaId = area.name;
			container.setItem(slot, managerIcon(area));
			clicks.put(slot, p -> openArea(p, areaId));
			slot++;
		}

		container.setItem(45, named(Items.BLAZE_ROD, "Marker A→B",
				SelectionStore.describe(player).split(" \\| ")));
		container.setItem(49, named(Items.BOOK, "Help",
				"Click an area to manage status / pool / visualize",
				"Cobblemon forms auto-enable only if that species has them",
				"/fishingarea manage pool-add … for full command control"));
		container.setItem(53, named(Items.COMPASS, "Fisher preview", "Opens the fisher chest list"));
		clicks.put(53, FishingMenus::openFisher);

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> clickableChest(MenuType.GENERIC_9x6, id, inv, container, 6, clicks),
				Component.literal("Manage Fishing Areas")
		));
	}

	public static void openArea(ServerPlayer player, String areaId) {
		Optional<FishingArea> areaOpt = AreaStorage.byId(areaId);
		if (areaOpt.isEmpty()) {
			player.displayClientMessage(Component.literal("Area not found."), true);
			openManager(player);
			return;
		}
		FishingArea area = areaOpt.get();
		SimpleContainer container = new SimpleContainer(27);
		Map<Integer, Consumer<ServerPlayer>> clicks = new HashMap<>();

		container.setItem(4, named(Items.NAME_TAG, area.name,
				area.shape.label() + " — " + area.describeBounds(),
				"status=" + area.status.name().toLowerCase(),
				"rewards=" + (area.pool == null ? 0 : area.pool.size())));

		container.setItem(10, named(Items.LIME_DYE, "Status → OPEN", "Click to set open"));
		clicks.put(10, p -> setStatus(p, area.name, AreaStatus.OPEN));
		container.setItem(12, named(Items.ORANGE_DYE, "Status → MAINTENANCE", "Click to set maintenance"));
		clicks.put(12, p -> setStatus(p, area.name, AreaStatus.MAINTENANCE));
		container.setItem(14, named(Items.GRAY_DYE, "Status → HIDDEN", "Click to set hidden"));
		clicks.put(14, p -> setStatus(p, area.name, AreaStatus.HIDDEN));

		container.setItem(16, named(Items.ENDER_EYE, "Visualize", "Toggle permanent outline"));
		clicks.put(16, p -> {
			AreaVisualizer.show(p, area);
			p.displayClientMessage(Component.literal("Visualizing " + area.name), true);
		});

		container.setItem(20, named(Items.CHEST, "Edit pool", "Add / remove reward entries"));
		clicks.put(20, p -> openPool(p, area.name));

		container.setItem(22, named(Items.FISHING_ROD, "Add Cobblemon Pokémon",
				"Detailed entry with smart form filters"));
		clicks.put(22, p -> beginCobblemonDraft(p, area.name, "cobblemon_pokemon"));

		container.setItem(24, named(Items.COD, "Add Cobblemon Species",
				"Simple species + level + delivery"));
		clicks.put(24, p -> beginCobblemonDraft(p, area.name, "cobblemon_species"));

		container.setItem(18, named(Items.ARROW, "Back", "Return to area list"));
		clicks.put(18, FishingMenus::openManager);

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> clickableChest(MenuType.GENERIC_9x3, id, inv, container, 3, clicks),
				Component.literal("Area: " + area.name)
		));
	}

	public static void openPool(ServerPlayer player, String areaId) {
		Optional<FishingArea> areaOpt = AreaStorage.byId(areaId);
		if (areaOpt.isEmpty()) {
			openManager(player);
			return;
		}
		FishingArea area = areaOpt.get();
		SimpleContainer container = new SimpleContainer(54);
		Map<Integer, Consumer<ServerPlayer>> clicks = new HashMap<>();

		List<PoolEntry> pool = area.pool == null ? List.of() : area.pool;
		for (int i = 0; i < pool.size() && i < 45; i++) {
			PoolEntry entry = pool.get(i);
			final int index = i;
			ItemStack icon = RewardTypes.get(entry.type).map(t -> t.icon(entry)).orElse(new ItemStack(Items.PAPER));
			if (icon.isEmpty()) {
				icon = new ItemStack(Items.PAPER);
			}
			List<Component> lore = new ArrayList<>();
			lore.add(RewardTypes.get(entry.type).map(t -> t.describe(entry)).orElse(Component.literal(entry.type)));
			lore.add(Component.literal("channel=" + entry.resolvedChannel().id()).withStyle(ChatFormatting.YELLOW));
			lore.add(Component.literal("Left-click: cycle channel").withStyle(ChatFormatting.GRAY));
			lore.add(Component.literal("Right-click: remove").withStyle(ChatFormatting.RED));
			icon.set(DataComponents.CUSTOM_NAME, Component.literal("[" + i + "] " + entry.type + ":" + entry.id));
			icon.set(DataComponents.LORE, new ItemLore(lore));
			container.setItem(i, icon);
			clicks.put(i, p -> {
				// handled in specialized click with button — use left as cycle
				cycleChannel(p, area.name, index);
			});
		}

		container.setItem(45, named(Items.ARROW, "Back", "Area menu"));
		clicks.put(45, p -> openArea(p, area.name));
		container.setItem(49, named(Items.EMERALD, "Add reward type…", "Pick a reward type"));
		clicks.put(49, p -> openAddType(p, area.name));
		container.setItem(53, named(Items.TNT, "Clear pool", "Removes all rewards"));
		clicks.put(53, p -> {
			AreaStorage.byId(area.name).ifPresent(a -> {
				a.pool.clear();
				AreaStorage.upsert(a);
			});
			openPool(p, area.name);
		});

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, container, 6) {
					@Override
					public void clicked(int slotIndex, int button, ClickType clickType, net.minecraft.world.entity.player.Player clickPlayer) {
						if (!(clickPlayer instanceof ServerPlayer serverPlayer)) {
							return;
						}
						if (slotIndex >= 0 && slotIndex < 45 && slotIndex < pool.size()) {
							if (button == 1) {
								removePoolEntry(serverPlayer, area.name, slotIndex);
							} else {
								cycleChannel(serverPlayer, area.name, slotIndex);
							}
							return;
						}
						Consumer<ServerPlayer> action = clicks.get(slotIndex);
						if (action != null) {
							action.accept(serverPlayer);
							return;
						}
						if (slotIndex >= 0 && slotIndex < 54) {
							return;
						}
						super.clicked(slotIndex, button, clickType, clickPlayer);
					}
				},
				Component.literal("Pool: " + area.name)
		));
	}

	public static void openAddType(ServerPlayer player, String areaId) {
		SimpleContainer container = new SimpleContainer(27);
		Map<Integer, Consumer<ServerPlayer>> clicks = new HashMap<>();
		int slot = 0;
		for (RewardType type : RewardTypes.all()) {
			if (slot >= 18) {
				break;
			}
			ItemStack icon = switch (type.id()) {
				case "item" -> new ItemStack(Items.COD);
				case "item_tag" -> new ItemStack(Items.NAME_TAG);
				case "xp" -> new ItemStack(Items.EXPERIENCE_BOTTLE);
				case "nothing" -> new ItemStack(Items.BARRIER);
				case "command" -> new ItemStack(Items.COMMAND_BLOCK);
				case "scoreboard" -> new ItemStack(Items.OAK_SIGN);
				case "economy" -> new ItemStack(Items.GOLD_INGOT);
				case "cobblemon_species" -> new ItemStack(Items.FISHING_ROD);
				case "cobblemon_pokemon" -> new ItemStack(Items.TROPICAL_FISH);
				default -> new ItemStack(Items.PAPER);
			};
			icon.set(DataComponents.CUSTOM_NAME, type.displayName());
			icon.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal(type.id()).withStyle(ChatFormatting.DARK_GRAY),
					Component.literal("Click to configure / add").withStyle(ChatFormatting.GREEN)
			)));
			container.setItem(slot, icon);
			final String typeId = type.id();
			clicks.put(slot, p -> beginTypeDraft(p, areaId, typeId));
			slot++;
		}
		container.setItem(22, named(Items.ARROW, "Back", "Pool"));
		clicks.put(22, p -> openPool(p, areaId));

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> clickableChest(MenuType.GENERIC_9x3, id, inv, container, 3, clicks),
				Component.literal("Add reward")
		));
	}

	private static void beginTypeDraft(ServerPlayer player, String areaId, String typeId) {
		if ("cobblemon_species".equals(typeId) || "cobblemon_pokemon".equals(typeId)) {
			beginCobblemonDraft(player, areaId, typeId);
			return;
		}
		GuiSessions.Draft draft = GuiSessions.get(player.getUUID());
		draft.areaId = areaId;
		draft.rewardType = typeId;
		draft.entry = new PoolEntry();
		draft.entry.type = typeId;
		draft.entry.setChannel(PoolChannel.BOTH);
		draft.entry.weight = 100;
		draft.entry.count = 1;

		switch (typeId) {
			case "item" -> {
				draft.entry.id = "minecraft:cod";
				commitDraft(player, draft);
				player.displayClientMessage(Component.literal(
						"Added default cod. Edit via /fishingarea manage pool-add or remove+re-add."), true);
			}
			case "item_tag" -> {
				draft.entry.id = "minecraft:fishes";
				commitDraft(player, draft);
				player.displayClientMessage(Component.literal("Added item_tag minecraft:fishes."), true);
			}
			case "xp" -> {
				draft.entry.id = "xp";
				draft.entry.count = 10;
				draft.entry.putData("mode", "points");
				commitDraft(player, draft);
			}
			case "nothing" -> {
				draft.entry.id = "nothing";
				commitDraft(player, draft);
			}
			case "economy" -> {
				draft.entry.id = "dollars";
				draft.entry.count = 100;
				draft.entry.putData("currency", "dollars");
				draft.entry.putData("amount", "100");
				commitDraft(player, draft);
				player.displayClientMessage(Component.literal(
						"Added economy dollars x100 (adapter soft-deps Impactor/…)."), true);
			}
			case "scoreboard" -> {
				draft.entry.id = "fishing";
				draft.entry.count = 1;
				draft.entry.putData("op", "add");
				commitDraft(player, draft);
			}
			case "command" -> {
				draft.entry.id = "say {player} caught something";
				draft.entry.putData("command", draft.entry.id);
				commitDraft(player, draft);
			}
			default -> player.displayClientMessage(Component.literal(
					"Use command: /fishingarea manage pool-add \"" + areaId + "\" " + typeId + " …"), true);
		}
		openPool(player, areaId);
	}

	private static void beginCobblemonDraft(ServerPlayer player, String areaId, String typeId) {
		if (!CobblemonBridge.isLoaded()) {
			player.displayClientMessage(Component.literal("Cobblemon is not installed on this server."), true);
			return;
		}
		GuiSessions.Draft draft = GuiSessions.get(player.getUUID());
		draft.areaId = areaId;
		draft.rewardType = typeId;
		draft.entry = new PoolEntry();
		draft.entry.type = typeId;
		draft.entry.setChannel(PoolChannel.COBBLEMON);
		draft.entry.weight = 100;
		draft.entry.putData("delivery", "party_or_pc");
		draft.entry.putData("level_min", "5");
		draft.entry.putData("level_max", "15");
		draft.entry.putData("form", "normal");
		draft.entry.putData("shiny", "random");
		draft.page = 0;
		openSpeciesPicker(player);
	}

	public static void openSpeciesPicker(ServerPlayer player) {
		GuiSessions.Draft draft = GuiSessions.get(player.getUUID());
		List<String> species = CobblemonBridge.listSpeciesIds(400);
		SimpleContainer container = new SimpleContainer(54);
		Map<Integer, Consumer<ServerPlayer>> clicks = new HashMap<>();

		int pageSize = 45;
		int start = draft.page * pageSize;
		for (int i = 0; i < pageSize && start + i < species.size(); i++) {
			String id = species.get(start + i);
			ItemStack stack = new ItemStack(Items.TROPICAL_FISH);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(id));
			stack.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal("Click to configure").withStyle(ChatFormatting.GREEN)
			)));
			container.setItem(i, stack);
			final String speciesId = id;
			clicks.put(i, p -> {
				GuiSessions.Draft d = GuiSessions.get(p.getUUID());
				d.entry.id = speciesId;
				openCobblemonOptions(p);
			});
		}

		container.setItem(45, named(Items.ARROW, "Prev page", "page " + draft.page));
		clicks.put(45, p -> {
			GuiSessions.get(p.getUUID()).page = Math.max(0, draft.page - 1);
			openSpeciesPicker(p);
		});
		container.setItem(49, named(Items.BARRIER, "Cancel", "Back to area"));
		clicks.put(49, p -> openArea(p, draft.areaId));
		container.setItem(53, named(Items.ARROW, "Next page", "page " + (draft.page + 1)));
		clicks.put(53, p -> {
			GuiSessions.get(p.getUUID()).page = draft.page + 1;
			openSpeciesPicker(p);
		});

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> clickableChest(MenuType.GENERIC_9x6, id, inv, container, 6, clicks),
				Component.literal("Pick species (" + draft.rewardType + ")")
		));
	}

	/**
	 * Smart options screen: Mega/Gmax/regional/forms only clickable when the species has them.
	 * Disabled slots show a clear chat reason on click.
	 */
	public static void openCobblemonOptions(ServerPlayer player) {
		GuiSessions.Draft draft = GuiSessions.get(player.getUUID());
		SpeciesCapability cap = SpeciesCapability.inspect(draft.entry.id);
		SimpleContainer container = new SimpleContainer(54);
		Map<Integer, Consumer<ServerPlayer>> clicks = new HashMap<>();

		container.setItem(4, named(Items.NAME_TAG, draft.entry.id,
				"type=" + draft.rewardType,
				"forms=" + String.join(", ", cap.forms()),
				"IVs/EVs = Cobblemon random (not configurable)"));

		int slot = 9;
		for (SpeciesCapability.Option option : cap.options()) {
			if (slot >= 36) {
				break;
			}
			if (option.kind() == SpeciesCapability.OptionKind.SHINY
					|| option.kind() == SpeciesCapability.OptionKind.LEVEL
					|| option.kind() == SpeciesCapability.OptionKind.DELIVERY) {
				continue; // placed in dedicated slots below
			}
			boolean selected = isSelected(draft, option);
			Item item = option.enabled()
					? (selected ? Items.LIME_CONCRETE : Items.LIGHT_BLUE_CONCRETE)
					: Items.GRAY_CONCRETE;
			ItemStack stack = new ItemStack(item);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(
					(option.enabled() ? "" : "§8") + option.label() + (selected ? " §a✔" : "")));
			List<Component> lore = new ArrayList<>();
			lore.add(Component.literal(option.kind() + ":" + option.id()).withStyle(ChatFormatting.DARK_GRAY));
			if (option.enabled()) {
				lore.add(Component.literal(selected ? "Selected — click to clear/toggle" : "Click to select")
						.withStyle(ChatFormatting.GREEN));
			} else {
				lore.add(Component.literal(option.disabledReason()).withStyle(ChatFormatting.RED));
				lore.add(Component.literal("Not available for this species").withStyle(ChatFormatting.DARK_RED));
			}
			stack.set(DataComponents.LORE, new ItemLore(lore));
			container.setItem(slot, stack);
			final SpeciesCapability.Option opt = option;
			clicks.put(slot, p -> handleOptionClick(p, opt));
			slot++;
		}

		// Shiny
		container.setItem(37, named(Items.GLOWSTONE_DUST, "Shiny: " + draft.entry.data("shiny", "random"),
				"Click to cycle: random → 1% → 5% → 10% → always → never",
				"IVs/EVs stay Cobblemon-random"));
		clicks.put(37, p -> {
			GuiSessions.Draft d = GuiSessions.get(p.getUUID());
			d.entry.putData("shiny", nextShiny(d.entry.data("shiny", "random")));
			openCobblemonOptions(p);
		});

		// Level cycle
		container.setItem(39, named(Items.EXPERIENCE_BOTTLE,
				"Level " + draft.entry.data("level_min", "5") + "-" + draft.entry.data("level_max", "15"),
				"Click to cycle common ranges"));
		clicks.put(39, p -> {
			GuiSessions.Draft d = GuiSessions.get(p.getUUID());
			cycleLevel(d.entry);
			openCobblemonOptions(p);
		});

		// Delivery
		String delivery = draft.entry.data("delivery", "party_or_pc");
		container.setItem(41, named(Items.ENDER_CHEST, "Delivery: " + delivery,
				"party_or_pc = give into party (overflow PC)",
				"spawn_world = spawn entity near bobber/player"));
		clicks.put(41, p -> {
			GuiSessions.Draft d = GuiSessions.get(p.getUUID());
			String cur = d.entry.data("delivery", "party_or_pc");
			d.entry.putData("delivery", "party_or_pc".equals(cur) ? "spawn_world" : "party_or_pc");
			openCobblemonOptions(p);
		});

		// Channel
		container.setItem(43, named(Items.COMPASS, "Channel: " + draft.entry.resolvedChannel().id(),
				"Click to cycle both → cobblemon → vanilla"));
		clicks.put(43, p -> {
			GuiSessions.Draft d = GuiSessions.get(p.getUUID());
			d.entry.setChannel(nextChannel(d.entry.resolvedChannel()));
			openCobblemonOptions(p);
		});

		container.setItem(45, named(Items.ARROW, "Back", "Species list"));
		clicks.put(45, FishingMenus::openSpeciesPicker);
		container.setItem(49, named(Items.EMERALD, "Confirm add", "Validate + save into pool"));
		clicks.put(49, FishingMenus::confirmCobblemonDraft);

		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> clickableChest(MenuType.GENERIC_9x6, id, inv, container, 6, clicks),
				Component.literal("Configure " + draft.entry.id)
		));
	}

	private static void handleOptionClick(ServerPlayer player, SpeciesCapability.Option option) {
		if (!option.enabled()) {
			player.displayClientMessage(option.clickMessage().copy().withStyle(ChatFormatting.RED), true);
			return;
		}
		GuiSessions.Draft draft = GuiSessions.get(player.getUUID());
		switch (option.kind()) {
			case FORM, MEGA, GMAX, REGIONAL -> {
				if ("mega".equals(option.id()) || "gmax".equals(option.id()) || "regional".equals(option.id())) {
					player.displayClientMessage(option.clickMessage().copy().withStyle(ChatFormatting.RED), true);
					return;
				}
				String current = draft.entry.data("form", "normal");
				draft.entry.putData("form", current.equalsIgnoreCase(option.id()) ? "normal" : option.id());
				player.displayClientMessage(Component.literal("Form: " + draft.entry.data("form", "normal"))
						.withStyle(ChatFormatting.GREEN), true);
			}
			case ASPECT -> {
				String aspects = draft.entry.data("aspects", "");
				List<String> list = new ArrayList<>();
				if (!aspects.isBlank()) {
					for (String part : aspects.split("[,\\s]+")) {
						if (!part.isBlank()) {
							list.add(part);
						}
					}
				}
				if (list.stream().anyMatch(a -> a.equalsIgnoreCase(option.id()))) {
					list.removeIf(a -> a.equalsIgnoreCase(option.id()));
				} else {
					list.add(option.id());
				}
				draft.entry.putData("aspects", String.join(",", list));
				player.displayClientMessage(Component.literal("Aspects: " + draft.entry.data("aspects", "(none)"))
						.withStyle(ChatFormatting.GREEN), true);
			}
			default -> player.displayClientMessage(option.clickMessage(), true);
		}
		openCobblemonOptions(player);
	}

	private static boolean isSelected(GuiSessions.Draft draft, SpeciesCapability.Option option) {
		return switch (option.kind()) {
			case FORM, MEGA, GMAX, REGIONAL ->
					draft.entry.data("form", "normal").equalsIgnoreCase(option.id());
			case ASPECT -> {
				String aspects = draft.entry.data("aspects", "");
				for (String part : aspects.split("[,\\s]+")) {
					if (part.equalsIgnoreCase(option.id())) {
						yield true;
					}
				}
				yield false;
			}
			default -> false;
		};
	}

	private static void confirmCobblemonDraft(ServerPlayer player) {
		GuiSessions.Draft draft = GuiSessions.get(player.getUUID());
		draft.entry.type = draft.rewardType;
		Optional<Component> error = RewardTypes.get(draft.rewardType).flatMap(t -> t.validate(draft.entry));
		if (error.isPresent()) {
			player.displayClientMessage(error.get().copy().withStyle(ChatFormatting.RED), false);
			return;
		}
		commitDraft(player, draft);
		player.displayClientMessage(Component.literal("Added " + draft.rewardType + ":" + draft.entry.id)
				.withStyle(ChatFormatting.GREEN), true);
		openPool(player, draft.areaId);
	}

	private static void commitDraft(ServerPlayer player, GuiSessions.Draft draft) {
		AreaStorage.byId(draft.areaId).ifPresent(area -> {
			area.pool.add(draft.entry);
			AreaStorage.upsert(area);
		});
		GuiSessions.clear(player.getUUID());
	}

	private static void setStatus(ServerPlayer player, String areaId, AreaStatus status) {
		AreaStorage.byId(areaId).ifPresent(area -> {
			area.status = status;
			AreaStorage.upsert(area);
			player.displayClientMessage(Component.literal(area.name + " → " + status.name().toLowerCase()), true);
		});
		openArea(player, areaId);
	}

	private static void cycleChannel(ServerPlayer player, String areaId, int index) {
		AreaStorage.byId(areaId).ifPresent(area -> {
			if (index < 0 || index >= area.pool.size()) {
				return;
			}
			PoolEntry entry = area.pool.get(index);
			entry.setChannel(nextChannel(entry.resolvedChannel()));
			AreaStorage.upsert(area);
			player.displayClientMessage(Component.literal("[" + index + "] channel=" + entry.resolvedChannel().id()), true);
		});
		openPool(player, areaId);
	}

	private static void removePoolEntry(ServerPlayer player, String areaId, int index) {
		AreaStorage.byId(areaId).ifPresent(area -> {
			if (index >= 0 && index < area.pool.size()) {
				PoolEntry removed = area.pool.remove(index);
				AreaStorage.upsert(area);
				player.displayClientMessage(Component.literal("Removed " + removed.type + ":" + removed.id), true);
			}
		});
		openPool(player, areaId);
	}

	private static PoolChannel nextChannel(PoolChannel current) {
		return switch (current) {
			case BOTH -> PoolChannel.COBBLEMON;
			case COBBLEMON -> PoolChannel.VANILLA;
			case VANILLA -> PoolChannel.BOTH;
		};
	}

	private static String nextShiny(String current) {
		return switch (current.toLowerCase()) {
			case "random" -> "1";
			case "1" -> "5";
			case "5" -> "10";
			case "10" -> "always";
			case "always" -> "never";
			default -> "random";
		};
	}

	private static void cycleLevel(PoolEntry entry) {
		int min = entry.dataInt("level_min", 5);
		String key = min + "-" + entry.dataInt("level_max", min);
		String next = switch (key) {
			case "5-5" -> "5-15";
			case "5-15" -> "10-20";
			case "10-20" -> "20-40";
			case "20-40" -> "50-50";
			default -> "5-5";
		};
		String[] parts = next.split("-");
		entry.putData("level_min", parts[0]);
		entry.putData("level_max", parts[1]);
	}

	private static ChestMenu clickableChest(MenuType<ChestMenu> type, int id,
			net.minecraft.world.entity.player.Inventory inv, SimpleContainer container, int rows,
			Map<Integer, Consumer<ServerPlayer>> clicks) {
		return new ChestMenu(type, id, inv, container, rows) {
			@Override
			public void clicked(int slotIndex, int button, ClickType clickType, net.minecraft.world.entity.player.Player clickPlayer) {
				if (clickPlayer instanceof ServerPlayer serverPlayer) {
					Consumer<ServerPlayer> action = clicks.get(slotIndex);
					if (action != null) {
						action.accept(serverPlayer);
						return;
					}
				}
				if (slotIndex >= 0 && slotIndex < rows * 9) {
					return;
				}
				super.clicked(slotIndex, button, clickType, clickPlayer);
			}
		};
	}

	private static ItemStack fisherIcon(FishingArea area, ServerPlayer player) {
		ItemStack stack = new ItemStack(area.status == AreaStatus.MAINTENANCE ? Items.ORANGE_CONCRETE : Items.LIME_CONCRETE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(area.name).withStyle(ChatFormatting.AQUA));

		double dx = player.getX() - (area.centerX + 0.5);
		double dy = player.getY() - (area.centerY + 0.5);
		double dz = player.getZ() - (area.centerZ + 0.5);
		int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);

		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal("Status: " + area.status.name().toLowerCase()).withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Type: " + area.shape.label()).withStyle(ChatFormatting.GRAY));
		lore.add(Component.literal(area.describeBounds()).withStyle(ChatFormatting.GRAY));
		lore.add(Component.literal("Distance ~" + dist + " blocks").withStyle(ChatFormatting.GRAY));
		lore.add(Component.literal("Rewards:").withStyle(ChatFormatting.GOLD));
		if (area.pool == null || area.pool.isEmpty()) {
			lore.add(Component.literal("  (empty)").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			int shown = 0;
			for (PoolEntry entry : area.pool) {
				if (shown++ >= 6) {
					lore.add(Component.literal("  ...").withStyle(ChatFormatting.DARK_GRAY));
					break;
				}
				lore.add(RewardTypes.get(entry.type)
						.map(t -> t.describe(entry).copy().withStyle(ChatFormatting.WHITE))
						.orElse(Component.literal("  " + entry.type + ":" + entry.id)));
			}
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static ItemStack managerIcon(FishingArea area) {
		ItemStack stack = new ItemStack(switch (area.status) {
			case OPEN -> Items.LIME_CONCRETE;
			case MAINTENANCE -> Items.ORANGE_CONCRETE;
			case HIDDEN -> Items.GRAY_CONCRETE;
		});
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(area.name + " [" + area.status + "]")
				.withStyle(ChatFormatting.GOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal(area.shape.label() + " — " + area.describeBounds()));
		lore.add(Component.literal("rewards=" + (area.pool == null ? 0 : area.pool.size())));
		lore.add(Component.literal("Click to manage").withStyle(ChatFormatting.GREEN));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static ItemStack named(Item item, String name, String... loreLines) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		List<Component> lore = new ArrayList<>();
		for (String line : loreLines) {
			lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}
}
