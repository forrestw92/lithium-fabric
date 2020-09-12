package me.jellysquid.mods.lithium.mixin.ai.nearby_entity_tracking.exact_position_listening;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onTeleportConfirm",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isInTeleportationState()Z", shift = At.Shift.BEFORE))
    private void notifyEntityTrackerEngine(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
        ((EntityTrackerEngineProvider)this.player.getServerWorld()).getEntityTracker().onEntityMovedAnyDistance(this.player);
    }
}
