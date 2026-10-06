package com.cbm.customfishingarea.area;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Marker A → B selection (two corners). */
public final class SelectionStore {
	public record Selection(ResourceLocation dimension, BlockPos a, BlockPos b) {
		public boolean complete() {
			return a != null && b != null;
		}
	}

	private static final class MutableSelection {
		ResourceLocation dimension;
		BlockPos a;
		BlockPos b;
	}

	private static final Map<UUID, MutableSelection> TEMP = new ConcurrentHashMap<>();

	private SelectionStore() {
	}

	public static void bootstrap() {
		TEMP.clear();
	}

	/**
	 * Click progression: empty → set A; has A → set B; has A+B → restart at A.
	 * Returns which corner was just set ("A" or "B").
	 */
	public static String setNext(ServerPlayer player, ResourceLocation dimension, BlockPos pos) {
		MutableSelection sel = TEMP.computeIfAbsent(player.getUUID(), u -> new MutableSelection());
		BlockPos immutable = pos.immutable();

		if (sel.a == null || (sel.a != null && sel.b != null)
				|| (sel.dimension != null && !sel.dimension.equals(dimension))) {
			sel.dimension = dimension;
			sel.a = immutable;
			sel.b = null;
			return "A";
		}
		sel.b = immutable;
		return "B";
	}

	public static Optional<Selection> get(ServerPlayer player) {
		MutableSelection sel = TEMP.get(player.getUUID());
		if (sel == null || sel.a == null) {
			return Optional.empty();
		}
		return Optional.of(new Selection(sel.dimension, sel.a, sel.b));
	}

	public static Optional<Selection> getComplete(ServerPlayer player) {
		return get(player).filter(Selection::complete);
	}

	public static void clear(ServerPlayer player) {
		TEMP.remove(player.getUUID());
	}

	public static String describe(ServerPlayer player) {
		return get(player).map(s -> {
			String a = s.a().getX() + " " + s.a().getY() + " " + s.a().getZ();
			String b = s.b() == null ? "(not set)" : (s.b().getX() + " " + s.b().getY() + " " + s.b().getZ());
			return s.dimension() + " | A=" + a + " | B=" + b;
		}).orElse("No selection — click two corners with the marker");
	}
}
