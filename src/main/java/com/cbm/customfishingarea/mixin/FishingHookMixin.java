package com.cbm.customfishingarea.mixin;

import com.cbm.customfishingarea.fishing.FishingGate;
import com.cbm.customfishingarea.fishing.PoolRewards;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gates vanilla fishing loot using bobber position + dimension.
 * Only intercepts when a bite is active ({@code nibble > 0}) so empty retract still cleans up the hook.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow
	private int nibble;

	@Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
	private void cbmcustomfishingarea$gateRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		FishingHook self = (FishingHook) (Object) this;
		if (self.level().isClientSide() || this.nibble <= 0) {
			return;
		}

		FishingGate.Result result = FishingGate.evaluateVanillaHook(self);
		if (!result.shouldCancelOriginalRewards()) {
			return;
		}

		Player owner = self.getPlayerOwner();
		FishingGate.notifyIfNeeded(owner, result);

		if (result.shouldGrantCustomRewards()
				&& owner instanceof ServerPlayer serverPlayer
				&& result.area().isPresent()) {
			PoolRewards.grantRandom(serverPlayer, result.area().get(), FishingGate.Channel.VANILLA);
		}

		self.discard();
		cir.setReturnValue(1);
	}
}
