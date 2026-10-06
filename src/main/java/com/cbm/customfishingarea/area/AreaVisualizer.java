package com.cbm.customfishingarea.area;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permanent per-player visualization until {@link #stop}.
 * Thin sparse particles for coords box or chunk fill.
 */
public final class AreaVisualizer {
	private record Job(ServerPlayer player, FishingArea area) {
	}

	private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();
	private static final DustParticleOptions FILL = new DustParticleOptions(new Vector3f(0.20f, 0.90f, 1.0f), 0.45f);
	private static final DustParticleOptions EDGE = new DustParticleOptions(new Vector3f(0.05f, 0.70f, 1.0f), 0.55f);
	private static final int FILL_STEP = 4;
	private static final int EDGE_STEP = 2;
	private static final int DRAW_INTERVAL = 10;
	private static int tickCounter;

	private AreaVisualizer() {
	}

	public static void bootstrap() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (JOBS.isEmpty()) {
				return;
			}
			tickCounter++;
			if (tickCounter % DRAW_INTERVAL != 0) {
				return;
			}
			JOBS.values().removeIf(job -> {
				if (!job.player.isAlive() || job.player.level().getServer() == null) {
					return true;
				}
				if (!job.player.level().dimension().location().equals(job.area.dimensionId())) {
					return false;
				}
				draw(job.player, job.area);
				return false;
			});
		});
	}

	public static void show(ServerPlayer player, FishingArea area) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!level.dimension().location().equals(area.dimensionId())) {
			player.displayClientMessage(Component.literal(
					"Wrong dimension. Go to " + area.dimension + " then visualize again."
			), true);
			return;
		}
		JOBS.put(player.getUUID(), new Job(player, area));
		player.displayClientMessage(Component.literal(
				"Showing '" + area.name + "' (" + area.shape.label() + ") permanently. "
						+ "/fishingarea manage visualize off to hide."
		), false);
		draw(player, area);
	}

	public static void showSelection(ServerPlayer player, SelectionStore.Selection sel, AreaShape shape) {
		FishingArea fake = FishingArea.fromSelection("temp", shape, sel, player.getGameProfile().getName());
		show(player, fake);
	}

	public static boolean stop(ServerPlayer player) {
		if (JOBS.remove(player.getUUID()) != null) {
			player.displayClientMessage(Component.literal("Area visualization off."), true);
			return true;
		}
		player.displayClientMessage(Component.literal("No active visualization."), true);
		return false;
	}

	private static void draw(ServerPlayer player, FishingArea area) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (area.shape == AreaShape.COORDINATES) {
			drawCoordBox(level, player, area);
		} else {
			drawChunkFill(level, player, area);
		}
	}

	private static void drawCoordBox(ServerLevel level, ServerPlayer player, FishingArea area) {
		int minX = area.minX;
		int maxX = area.maxX + 1;
		int minY = area.minY;
		int maxY = area.maxY + 1;
		int minZ = area.minZ;
		int maxZ = area.maxZ + 1;

		// Horizontal rings at minY / mid / maxY
		int midY = (minY + maxY) / 2;
		for (int y : new int[]{minY, midY, Math.max(minY, maxY - 1)}) {
			drawRectXZ(level, player, EDGE, minX, maxX, minZ, maxZ, y + 0.1);
		}
		// Vertical edges
		for (int x : new int[]{minX, maxX}) {
			for (int z : new int[]{minZ, maxZ}) {
				for (int y = minY; y <= maxY; y += EDGE_STEP) {
					level.sendParticles(player, EDGE, true, x + 0.05, y + 0.1, z + 0.05, 1, 0, 0, 0, 0);
				}
			}
		}
		// Sparse fill at mid height
		for (int x = minX; x < maxX; x += FILL_STEP) {
			for (int z = minZ; z < maxZ; z += FILL_STEP) {
				level.sendParticles(player, FILL, true, x + 0.5, midY + 0.2, z + 0.5, 1, 0, 0, 0, 0);
			}
		}
	}

	private static void drawChunkFill(ServerLevel level, ServerPlayer player, FishingArea area) {
		int minX = area.minChunkX * 16;
		int maxX = (area.maxChunkX + 1) * 16;
		int minZ = area.minChunkZ * 16;
		int maxZ = (area.maxChunkZ + 1) * 16;
		double y = player.getY() + 0.2;

		for (int x = minX; x < maxX; x += FILL_STEP) {
			for (int z = minZ; z < maxZ; z += FILL_STEP) {
				level.sendParticles(player, FILL, true, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
			}
		}
		drawRectXZ(level, player, EDGE, minX, maxX, minZ, maxZ, y);
		for (int cx = area.minChunkX; cx <= area.maxChunkX + 1; cx++) {
			drawEdge(level, player, FILL, minZ, maxZ, cx * 16, y, false);
		}
		for (int cz = area.minChunkZ; cz <= area.maxChunkZ + 1; cz++) {
			drawEdge(level, player, FILL, minX, maxX, cz * 16, y, true);
		}
	}

	private static void drawRectXZ(ServerLevel level, ServerPlayer player, DustParticleOptions dust,
			int minX, int maxX, int minZ, int maxZ, double y) {
		drawEdge(level, player, dust, minX, maxX, minZ, y, true);
		drawEdge(level, player, dust, minX, maxX, maxZ, y, true);
		drawEdge(level, player, dust, minZ, maxZ, minX, y, false);
		drawEdge(level, player, dust, minZ, maxZ, maxX, y, false);
	}

	private static void drawEdge(ServerLevel level, ServerPlayer player, DustParticleOptions dust,
			int from, int to, int fixed, double y, boolean alongX) {
		for (int v = from; v <= to; v += EDGE_STEP) {
			double x = alongX ? v : fixed;
			double z = alongX ? fixed : v;
			level.sendParticles(player, dust, true, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
		}
	}
}
