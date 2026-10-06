package com.cbm.customfishingarea.pool.type;

import com.cbm.customfishingarea.pool.PoolEntry;
import com.cbm.customfishingarea.pool.RewardType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/** Runs a console command with {player} placeholder. */
public final class CommandRewardType implements RewardType {
	@Override
	public String id() {
		return "command";
	}

	@Override
	public Component displayName() {
		return Component.literal("Command");
	}

	@Override
	public Component describe(PoolEntry entry) {
		String cmd = entry.data("command", entry.id);
		return Component.literal("/" + cmd + " (w=" + entry.weight + ", " + entry.resolvedChannel().id() + ")");
	}

	@Override
	public ItemStack icon(PoolEntry entry) {
		return new ItemStack(Items.COMMAND_BLOCK);
	}

	@Override
	public boolean grant(ServerPlayer player, PoolEntry entry) {
		String cmd = entry.data("command", entry.id);
		if (cmd == null || cmd.isBlank()) {
			return false;
		}
		String resolved = cmd.replace("{player}", player.getGameProfile().getName())
				.replace("{uuid}", player.getUUID().toString());
		player.getServer().getCommands().performPrefixedCommand(
				player.getServer().createCommandSourceStack().withSuppressedOutput(),
				resolved
		);
		return true;
	}

	@Override
	public Optional<Component> validate(PoolEntry entry) {
		String cmd = entry.data("command", entry.id);
		if (cmd == null || cmd.isBlank()) {
			return Optional.of(Component.literal("command is required"));
		}
		return Optional.empty();
	}
}
