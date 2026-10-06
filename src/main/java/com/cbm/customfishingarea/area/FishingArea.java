package com.cbm.customfishingarea.area;

import com.cbm.customfishingarea.pool.PoolEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fishing zone from marker A → B.
 * <ul>
 *   <li>{@link AreaShape#COORDINATES} — inclusive block box (uses Y)</li>
 *   <li>{@link AreaShape#CHUNKS} — inclusive chunk rectangle on XZ (ignores Y)</li>
 * </ul>
 */
public final class FishingArea {
	public String id = UUID.randomUUID().toString();
	public String name = "Unnamed";
	public String dimension = "minecraft:overworld";
	public AreaShape shape = AreaShape.CHUNKS;

	/** Inclusive bounds (block coords). For CHUNKS these still store the A/B click blocks. */
	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;

	/** Inclusive chunk bounds (used when shape == CHUNKS). */
	public int minChunkX;
	public int maxChunkX;
	public int minChunkZ;
	public int maxChunkZ;

	// --- legacy fields (migrated on load) ---
	@Deprecated public int centerX;
	@Deprecated public int centerY;
	@Deprecated public int centerZ;
	@Deprecated public int chunkRadius;
	@Deprecated public double radius;
	@Deprecated public boolean chunkShape = true;

	public AreaStatus status = AreaStatus.OPEN;
	public List<PoolEntry> pool = new ArrayList<>();
	public String createdBy = "";
	public long updatedAt = System.currentTimeMillis();

	public ResourceLocation dimensionId() {
		return ResourceLocation.parse(dimension);
	}

	public static FishingArea fromSelection(String name, AreaShape shape, SelectionStore.Selection sel, String createdBy) {
		FishingArea area = new FishingArea();
		area.name = name;
		area.shape = shape;
		area.dimension = sel.dimension().toString();
		area.createdBy = createdBy;
		area.applyCorners(sel.a(), sel.b(), shape);
		return area;
	}

	public void applyCorners(BlockPos a, BlockPos b, AreaShape shape) {
		this.shape = shape;
		this.minX = Math.min(a.getX(), b.getX());
		this.minY = Math.min(a.getY(), b.getY());
		this.minZ = Math.min(a.getZ(), b.getZ());
		this.maxX = Math.max(a.getX(), b.getX());
		this.maxY = Math.max(a.getY(), b.getY());
		this.maxZ = Math.max(a.getZ(), b.getZ());

		int cxa = SectionPos.blockToSectionCoord(a.getX());
		int cxb = SectionPos.blockToSectionCoord(b.getX());
		int cza = SectionPos.blockToSectionCoord(a.getZ());
		int czb = SectionPos.blockToSectionCoord(b.getZ());
		this.minChunkX = Math.min(cxa, cxb);
		this.maxChunkX = Math.max(cxa, cxb);
		this.minChunkZ = Math.min(cza, czb);
		this.maxChunkZ = Math.max(cza, czb);

		// keep legacy center for old tools / display fallback
		this.centerX = (minX + maxX) / 2;
		this.centerY = (minY + maxY) / 2;
		this.centerZ = (minZ + maxZ) / 2;
		this.chunkShape = shape == AreaShape.CHUNKS;
		this.chunkRadius = Math.max(maxChunkX - minChunkX, maxChunkZ - minChunkZ) / 2;
	}

	public boolean contains(ResourceLocation dimensionKey, double x, double y, double z) {
		if (!dimensionId().equals(dimensionKey)) {
			return false;
		}
		if (shape == AreaShape.COORDINATES) {
			int bx = (int) Math.floor(x);
			int by = (int) Math.floor(y);
			int bz = (int) Math.floor(z);
			return bx >= minX && bx <= maxX
					&& by >= minY && by <= maxY
					&& bz >= minZ && bz <= maxZ;
		}
		// CHUNKS — Y ignored
		int cx = SectionPos.blockToSectionCoord((int) Math.floor(x));
		int cz = SectionPos.blockToSectionCoord((int) Math.floor(z));
		return cx >= minChunkX && cx <= maxChunkX
				&& cz >= minChunkZ && cz <= maxChunkZ;
	}

	/** Approximate volume for overlap “smallest wins”. */
	public long volumeScore() {
		if (shape == AreaShape.COORDINATES) {
			long dx = (long) maxX - minX + 1;
			long dy = (long) maxY - minY + 1;
			long dz = (long) maxZ - minZ + 1;
			return Math.max(1L, dx * dy * dz);
		}
		long cx = (long) maxChunkX - minChunkX + 1;
		long cz = (long) maxChunkZ - minChunkZ + 1;
		return Math.max(1L, cx * cz);
	}

	public String describeBounds() {
		if (shape == AreaShape.COORDINATES) {
			return "coords [" + minX + "," + minY + "," + minZ + "] → ["
					+ maxX + "," + maxY + "," + maxZ + "]";
		}
		return "chunks [" + minChunkX + "," + minChunkZ + "] → ["
				+ maxChunkX + "," + maxChunkZ + "] ("
				+ ((maxChunkX - minChunkX + 1) * 16) + "×"
				+ ((maxChunkZ - minChunkZ + 1) * 16) + " blocks, Y ignored)";
	}

	public BlockPos cornerA() {
		return new BlockPos(minX, minY, minZ);
	}

	public BlockPos cornerB() {
		return new BlockPos(maxX, maxY, maxZ);
	}

	/** Display center. */
	public BlockPos centerPos() {
		return new BlockPos(centerX, centerY, centerZ);
	}
}
