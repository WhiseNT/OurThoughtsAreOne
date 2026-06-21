package com.whisent.ourthoughtsareone.mixin;

import com.whisent.ourthoughtsareone.core.ProjectionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerMixin {

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void ourthoughtsareone$disableProjectionPairCollision(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (isProjectionPlayerPair(self, other)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void ourthoughtsareone$disableProjectionPairPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (isProjectionPlayerPair(self, other)) {
            ci.cancel();
        }
    }

    private boolean isProjectionPlayerPair(Entity self, Entity other) {
        if (self instanceof Player selfPlayer && other instanceof Player otherPlayer) {
            return ProjectionManager.isProjectionPair(selfPlayer, otherPlayer);
        }
        return false;
    }
}
