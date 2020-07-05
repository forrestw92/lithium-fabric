package me.jellysquid.mods.lithium.mixin.ai.nearby_entity_tracking;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Installs event listeners to the world class which will be used to notify the {@link EntityTrackerEngine} of changes.
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {

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




    /**
     * Notify the entity tracker when an entity moves and enters a new chunk.
     */
    @Inject(method = "checkChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/WorldChunk;addEntity(Lnet/minecraft/entity/Entity;)V", shift = At.Shift.BEFORE))
    private void onEntityMoveAdd(Entity entity, CallbackInfo ci) {
        int x = MathHelper.floor(entity.getX()) >> 4;
        int y = MathHelper.floor(entity.getY()) >> 4;
        int z = MathHelper.floor(entity.getZ()) >> 4;

        EntityTrackerEngine tracker = EntityTrackerEngineProvider.getEntityTracker(this);
        tracker.onEntityAdded(x, y, z, entity);

    }

    /**
     * Notify the entity tracker when an entity moves and is removed from the previous chunk.
     */
    @Inject(method = "checkChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/WorldChunk;remove(Lnet/minecraft/entity/Entity;I)V", shift = At.Shift.BEFORE))
    private void onEntityMoveRemove(Entity entity, CallbackInfo ci) {
        // The chunkX/Y/Z fields on the entity represent the entity's chunk coordinates in the *previous* tick
        EntityTrackerEngine tracker = EntityTrackerEngineProvider.getEntityTracker(this);
        tracker.onEntityRemoved(entity.chunkX, entity.chunkY, entity.chunkZ, entity);
    }

    /**
     * Notify the entity tracker when an entity is added to the world.
     */
    @Inject(method = "addEntityPrivate", at = @At(value = "FIELD", target = "Lnet/minecraft/client/world/ClientWorld;regularEntities:Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;"))
    private void onEntityAdded(int id, Entity entity, CallbackInfo ci) {
        int chunkX = MathHelper.floor(entity.getX()) >> 4;
        int chunkY = MathHelper.floor(entity.getY()) >> 4;
        int chunkZ = MathHelper.floor(entity.getZ()) >> 4;

        EntityTrackerEngine tracker = EntityTrackerEngineProvider.getEntityTracker(this);
        tracker.onEntityAdded(chunkX, chunkY, chunkZ, entity);
    }

    /**
     * Notify the entity tracker when an entity is removed from the world.
     */
    @Inject(method = "finishRemovingEntity", at = @At(value = "HEAD"))
    private void onEntityRemoved(Entity entity, CallbackInfo ci) {
        int chunkX = MathHelper.floor(entity.getX()) >> 4;
        int chunkY = MathHelper.floor(entity.getY()) >> 4;
        int chunkZ = MathHelper.floor(entity.getZ()) >> 4;

        EntityTrackerEngine tracker = EntityTrackerEngineProvider.getEntityTracker(this);
        tracker.onEntityRemoved(chunkX, chunkY, chunkZ, entity);
    }
}
