package com.cbm.customfishingarea.area;

import com.cbm.customfishingarea.CbmCustomFishingAreaMod;
import com.cbm.customfishingarea.config.ModConfig;
import com.cbm.customfishingarea.pool.PoolChannel;
import com.cbm.customfishingarea.pool.PoolEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.SectionPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AreaStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type LIST_TYPE = new TypeToken<List<FishingArea>>() {}.getType();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
			.resolve(CbmCustomFishingAreaMod.MOD_ID)
			.resolve("areas.json");

	private static final List<FishingArea> AREAS = new ArrayList<>();

	private AreaStorage() {
	}

	public static synchronized void load() {
		AREAS.clear();
		try {
			Files.createDirectories(PATH.getParent());
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH)) {
					List<FishingArea> loaded = GSON.fromJson(reader, LIST_TYPE);
					if (loaded != null) {
						AREAS.addAll(loaded);
						for (FishingArea area : AREAS) {
							if (area.pool == null) {
								area.pool = new ArrayList<>();
							}
							if (area.status == null) {
								area.status = AreaStatus.OPEN;
							}
							migrateShape(area);
							migratePoolChannels(area);
						}
					}
				}
			} else {
				save();
			}
		} catch (IOException e) {
			CbmCustomFishingAreaMod.LOGGER.error("Failed to load areas", e);
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(AREAS, writer);
			}
		} catch (IOException e) {
			CbmCustomFishingAreaMod.LOGGER.error("Failed to save areas", e);
		}
	}

	public static synchronized List<FishingArea> all() {
		return Collections.unmodifiableList(new ArrayList<>(AREAS));
	}

	public static synchronized List<FishingArea> listedForFishers() {
		return AREAS.stream().filter(a -> a.status.isListedForFishers()).toList();
	}

	public static synchronized Optional<FishingArea> byId(String id) {
		return AREAS.stream().filter(a -> a.id.equalsIgnoreCase(id) || a.name.equalsIgnoreCase(id)).findFirst();
	}

	public static synchronized void upsert(FishingArea area) {
		AREAS.removeIf(a -> a.id.equals(area.id));
		area.updatedAt = System.currentTimeMillis();
		AREAS.add(area);
		save();
	}

	public static synchronized boolean delete(String id) {
		boolean removed = AREAS.removeIf(a -> a.id.equalsIgnoreCase(id) || a.name.equalsIgnoreCase(id));
		if (removed) {
			save();
		}
		return removed;
	}

	public static synchronized Optional<FishingArea> resolveAt(net.minecraft.resources.ResourceLocation dimension, double x, double y, double z) {
		List<FishingArea> hits = AREAS.stream()
				.filter(a -> a.contains(dimension, x, y, z))
				.sorted(overlapComparator())
				.toList();
		return hits.isEmpty() ? Optional.empty() : Optional.of(hits.getFirst());
	}

	/** Missing channel → BOTH (safe default for older saves / tests). */
	private static void migratePoolChannels(FishingArea area) {
		if (area.pool == null) {
			return;
		}
		for (PoolEntry entry : area.pool) {
			if (entry == null) {
				continue;
			}
			if (entry.data == null) {
				entry.data = new java.util.HashMap<>();
			}
			if (entry.channel == null || entry.channel.isBlank()) {
				entry.setChannel(PoolChannel.BOTH);
			} else {
				entry.setChannel(entry.resolvedChannel());
			}
		}
	}

	/** Migrate legacy center+chunkRadius / sphere into A→B bounds. */
	private static void migrateShape(FishingArea area) {
		if (area.shape == null) {
			area.shape = area.chunkShape ? AreaShape.CHUNKS : AreaShape.COORDINATES;
		}

		boolean hasNewBounds = area.maxX != 0 || area.minX != 0 || area.maxZ != 0 || area.minZ != 0
				|| area.maxChunkX != 0 || area.minChunkX != 0;
		// If already has chunk bounds or coord bounds that look set, just normalize.
		if (hasNewBounds && (area.maxX >= area.minX || area.maxChunkX >= area.minChunkX)) {
			if (area.shape == AreaShape.CHUNKS && area.maxChunkX < area.minChunkX) {
				// derive chunks from block mins if needed
				area.minChunkX = SectionPos.blockToSectionCoord(area.minX);
				area.maxChunkX = SectionPos.blockToSectionCoord(area.maxX);
				area.minChunkZ = SectionPos.blockToSectionCoord(area.minZ);
				area.maxChunkZ = SectionPos.blockToSectionCoord(area.maxZ);
			}
			normalize(area);
			return;
		}

		// Legacy center + chunkRadius
		int r = Math.max(0, area.chunkRadius);
		if (r == 0 && area.radius > 0) {
			r = Math.max(0, (int) Math.ceil(area.radius / 16.0) - 1);
		}
		int cx = SectionPos.blockToSectionCoord(area.centerX);
		int cz = SectionPos.blockToSectionCoord(area.centerZ);
		area.shape = AreaShape.CHUNKS;
		area.minChunkX = cx - r;
		area.maxChunkX = cx + r;
		area.minChunkZ = cz - r;
		area.maxChunkZ = cz + r;
		area.minX = area.minChunkX * 16;
		area.maxX = (area.maxChunkX + 1) * 16 - 1;
		area.minZ = area.minChunkZ * 16;
		area.maxZ = (area.maxChunkZ + 1) * 16 - 1;
		area.minY = area.centerY - 32;
		area.maxY = area.centerY + 32;
		normalize(area);
	}

	private static void normalize(FishingArea area) {
		if (area.minX > area.maxX) {
			int t = area.minX;
			area.minX = area.maxX;
			area.maxX = t;
		}
		if (area.minY > area.maxY) {
			int t = area.minY;
			area.minY = area.maxY;
			area.maxY = t;
		}
		if (area.minZ > area.maxZ) {
			int t = area.minZ;
			area.minZ = area.maxZ;
			area.maxZ = t;
		}
		if (area.minChunkX > area.maxChunkX) {
			int t = area.minChunkX;
			area.minChunkX = area.maxChunkX;
			area.maxChunkX = t;
		}
		if (area.minChunkZ > area.maxChunkZ) {
			int t = area.minChunkZ;
			area.minChunkZ = area.maxChunkZ;
			area.maxChunkZ = t;
		}
		area.centerX = (area.minX + area.maxX) / 2;
		area.centerY = (area.minY + area.maxY) / 2;
		area.centerZ = (area.minZ + area.maxZ) / 2;
		area.chunkShape = area.shape == AreaShape.CHUNKS;
	}

	private static Comparator<FishingArea> overlapComparator() {
		String rule = ModConfig.get().overlapRule;
		Comparator<FishingArea> primary = Comparator.comparingLong(FishingArea::volumeScore);
		if ("LARGEST_RADIUS".equalsIgnoreCase(rule) || "LARGEST_VOLUME".equalsIgnoreCase(rule)) {
			primary = Comparator.comparingLong(FishingArea::volumeScore).reversed();
		}
		return primary
				.thenComparing(Comparator.comparingLong((FishingArea a) -> a.updatedAt).reversed())
				.thenComparing(a -> a.name, String.CASE_INSENSITIVE_ORDER);
	}
}
