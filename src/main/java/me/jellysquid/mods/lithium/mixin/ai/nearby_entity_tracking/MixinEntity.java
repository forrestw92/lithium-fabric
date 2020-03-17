package me.jellysquid.mods.lithium.mixin.ai.nearby_entity_tracking;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the base world class to provide a {@link EntityTrackerEngine}.
 */
@Mixin(Entity.class)
public class MixinEntity {
    @Shadow
    public World world;

    //Only using move inject for movements not caused during ticking of the entity, usually pistons, shulkers etc.
    //If the previous position becomes important (this call is relevant for movement within a chunk section anyways)
    //We can add an extra call at the beginning of the move method to get that info
    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At(value = "RETURN"))
    private void notifyEntityTrackerEngineAboutBeingMoved(CallbackInfo ci) {
        if (!((EntityTrackerEngineProvider) this.world).isEntityTrackedNow((Entity)(Object)this)) {
            ((EntityTrackerEngineProvider) this.world).getEntityTracker().onEntityMovedAnyDistance((Entity)(Object)this);
        }
    }
}
