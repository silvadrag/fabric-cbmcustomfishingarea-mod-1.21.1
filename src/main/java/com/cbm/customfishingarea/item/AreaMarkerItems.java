package com.cbm.customfishingarea.item;

import com.cbm.customfishingarea.area.SelectionStore;
import com.cbm.customfishingarea.permission.ModPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Marker tool: click corner A then corner B.
 * Never breaks / places / uses the clicked block (environment safe).
 */
public final class AreaMarkerItems {
	public static final String TAG_MARKER = "cbm_area_marker";

	private AreaMarkerItems() {
	}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(Items.BLAZE_ROD);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Fishing Area Marker (A→B)").withStyle(ChatFormatting.GOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("1) Click corner A").withStyle(ChatFormatting.GRAY),
				Component.literal("2) Click corner B").withStyle(ChatFormatting.GRAY),
				Component.literal("3) /fishingarea manage create <name> coordinates|chunks")
						.withStyle(ChatFormatting.YELLOW),
				Component.literal("Does not break or change blocks.").withStyle(ChatFormatting.DARK_GREEN)
		)));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG_MARKER, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	public static boolean isMarker(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		return data.copyTag().getBoolean(TAG_MARKER);
	}

	public static boolean isHoldingMarker(Player player) {
		return isMarker(player.getMainHandItem()) || isMarker(player.getOffhandItem());
	}

	/** Right-click in air → mark feet position. */
	public static InteractionResultHolder<ItemStack> onUseItem(Player player, Level world, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (world.isClientSide() || !isMarker(stack)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!ModPermissions.canManage(serverPlayer)) {
			serverPlayer.displayClientMessage(Component.literal("Missing permission to manage fishing areas."), true);
			return InteractionResultHolder.fail(stack);
		}
		mark(serverPlayer, serverPlayer.blockPosition());
		return InteractionResultHolder.success(stack);
	}

	/** Right-click block → mark + cancel vanilla block use. */
	public static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide() || !isMarker(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!ModPermissions.canManage(serverPlayer)) {
			return InteractionResult.FAIL;
		}
		mark(serverPlayer, hit.getBlockPos());
		return InteractionResult.SUCCESS;
	}

	/** Left-click block → mark + cancel break. */
	public static InteractionResult onAttackBlock(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
		if (world.isClientSide() || !isHoldingMarker(player)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!ModPermissions.canManage(serverPlayer)) {
			return InteractionResult.FAIL;
		}
		mark(serverPlayer, pos);
		return InteractionResult.FAIL;
	}

	/** Extra break cancel while holding marker. */
	public static boolean shouldCancelBreak(Player player) {
		return isHoldingMarker(player);
	}

	private static void mark(ServerPlayer player, BlockPos pos) {
		ResourceLocation dim = player.level().dimension().location();
		String corner = SelectionStore.setNext(player, dim, pos);
		player.displayClientMessage(Component.literal(
				"Corner " + corner + " = " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
						+ (corner.equals("A")
						? " — now click corner B"
						: " — ready! /fishingarea manage create <name> coordinates|chunks")
		).withStyle(corner.equals("A") ? ChatFormatting.AQUA : ChatFormatting.GREEN), false);
		if (corner.equals("B")) {
			player.displayClientMessage(Component.literal(SelectionStore.describe(player))
					.withStyle(ChatFormatting.GRAY), false);
		}
	}
}
