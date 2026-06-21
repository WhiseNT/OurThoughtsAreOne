package com.whisent.ourthoughtsareone.mixin;

import com.whisent.ourthoughtsareone.core.ProjectionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void ourthoughtsareone$skipProjectionPairDoPush(Entity entity, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player selfPlayer && entity instanceof Player otherPlayer) {
            if (ProjectionManager.isProjectionPair(selfPlayer, otherPlayer)) {
                ci.cancel();
            }
        }
    }
}
