package com.cbm.customfishingarea.config;

import com.cbm.customfishingarea.CbmCustomFishingAreaMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime config.
 * <ul>
 *   <li>{@code customFishingEnabled=false} → vanilla + Cobblemon behave normally.</li>
 *   <li>When enabled, {@code replaceVanillaFishing}/{@code replaceCobblemonFishing}
 *   choose which channels are gated by areas/pools.</li>
 * </ul>
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
			.resolve(CbmCustomFishingAreaMod.MOD_ID)
			.resolve("config.json");

	private static Data data = new Data();

	private ModConfig() {
	}

	public static void load() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.exists(CONFIG_PATH)) {
				try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
					Data loaded = GSON.fromJson(reader, Data.class);
					if (loaded != null) {
						data = loaded;
					}
				}
			} else {
				save();
			}
		} catch (IOException e) {
			CbmCustomFishingAreaMod.LOGGER.error("Failed to load config", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CbmCustomFishingAreaMod.LOGGER.error("Failed to save config", e);
		}
	}

	public static Data get() {
		return data;
	}

	public static final class Data {
		/** Master switch for the custom fishing system. */
		public boolean customFishingEnabled = false;
		/** When custom system is on: gate/replace vanilla fishing loot. */
		public boolean replaceVanillaFishing = true;
		/** When custom system is on: gate/replace Cobblemon Pokerod rewards. Soft-dep. */
		public boolean replaceCobblemonFishing = true;
		/** Message when bobber is outside any usable area. */
		public String messageNoArea = "You can only fish inside an active fishing area.";
		/** Message when the resolved area is under maintenance. */
		public String messageMaintenance = "This fishing area is under maintenance.";
		/** Message when area is usable but pool is empty (default until rewards are added). */
		public String messageEmptyPool = "Nothing bites here yet.";
		/** Overlap rule: smallest chunk radius wins (most specific). */
		public String overlapRule = "SMALLEST_RADIUS";
	}
}
