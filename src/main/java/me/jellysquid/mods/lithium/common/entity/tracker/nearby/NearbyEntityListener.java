package me.jellysquid.mods.lithium.common.entity.tracker.nearby;

import net.minecraft.entity.Entity;

/**
 * The main interface used to receive events from the
 * {@link me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine} of a world.
 */
public interface NearbyEntityListener {
    /**
     * Returns the range (in chunks) of this listener. This must never change during the lifetime of the listener.
     * TODO: Allow entity listeners to change the radius they receive updates within
     */
    int getChunkRange();

    /**
     * Called by the entity tracker when an entity enters the range of this listener.
     */
    void onEntityEnteredTrackedSubchunk(Entity entity);

    /**
     * Called by the entity tracker when an entity leaves the range of this listener or is removed from the world.
     */
    void onEntityLeftTrackedSubchunk(Entity entity);

    /**
     * Called by the Entity tracker engine when it added all the initial entities to the tracker when it is new or moved.
     */
    default void onInitialEntitiesReceived(){}
}
