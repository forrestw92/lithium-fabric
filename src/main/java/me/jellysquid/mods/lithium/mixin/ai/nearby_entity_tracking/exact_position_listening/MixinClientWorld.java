package me.jellysquid.mods.lithium.mixin.ai.nearby_entity_tracking.exact_position_listening;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Installs event listeners to the world class which will be used to notify the {@link EntityTrackerEngine} of changes.
 */
@Mixin(ClientWorld.class)
public class MixinClientWorld {

    //begin ExactPositionListeners
    //author 2No2Name
    //code used in MixinServerWorld and MixinClientWorld
    //running the Entity tracker on client world probably doesn't make sense, because logic runs on server worlds
    //but nontheless doing it for the consistency because the mod owner Jelly seems to run it on client worlds

    //Used for detecting whether an entity moved. Variables can be used for both normal and riding entities.
    private double entityPrevX,entityPrevY,entityPrevZ;
    /**
     * Prepare notifying the entity tracker when an entity moves (used for within chunk section tracking).
     */
    @Inject(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V", shift = At.Shift.BEFORE))
    private void rememberCoords(Entity entity, CallbackInfo ci){
        this.entityPrevX = entity.getX();
        this.entityPrevY = entity.getY();
        this.entityPrevZ = entity.getZ();
        ((EntityTrackerEngineProvider)this).setEntityTrackedNow(entity);
    }
    /**
     * Prepare notifying the entity tracker when an entity moves (used for within chunk section tracking).
     * (call for entities riding a vehicle)
     */
    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tickRiding()V", shift = At.Shift.BEFORE))
    private void rememberCoords_RidingEntity(Entity vehicle, Entity entityRiding, CallbackInfo ci){
        this.entityPrevX = entityRiding.getX();
        this.entityPrevY = entityRiding.getY();
        this.entityPrevZ = entityRiding.getZ();
        ((EntityTrackerEngineProvider)this).setEntityTrackedNow(entityRiding);
    }
    /**
     * Notify the entity tracker when an entity moves (used for within chunk section tracking).
     */
    @Inject(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void notifyEntityTracker(Entity entity, CallbackInfo ci){
        if(this.entityPrevX != entity.getX() || this.entityPrevY != entity.getY() || this.entityPrevZ != entity.getZ()) {
            EntityTrackerEngine tracker = EntityTrackerEngineProvider.getEntityTracker(this);
            tracker.onEntityMovedAnyDistance(entity);
        }
        ((EntityTrackerEngineProvider)this).setEntityTrackedNow(null);
    }
    /**
     * Notify the entity tracker when an entity moves (used for within chunk section tracking).
     * (call for entities riding a vehicle)
     */
    @Inject(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tickRiding()V", shift = At.Shift.AFTER))
    private void notifyEntityTracker_RidingEntity(Entity vehicle, Entity entityRiding, CallbackInfo ci){
        if(this.entityPrevX != entityRiding.getX() || this.entityPrevY != entityRiding.getY() || this.entityPrevZ != entityRiding.getZ()) {
            EntityTrackerEngine tracker = EntityTrackerEngineProvider.getEntityTracker(this);
            tracker.onEntityMovedAnyDistance(entityRiding);
        }
        ((EntityTrackerEngineProvider)this).setEntityTrackedNow(null);
    }
    //end ExactPositionListeners
}
