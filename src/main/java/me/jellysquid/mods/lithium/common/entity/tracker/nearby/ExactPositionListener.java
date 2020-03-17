package me.jellysquid.mods.lithium.common.entity.tracker.nearby;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Secondary interface used to receive events from the
 * {@link me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine} of a world.
 *
 * ExactPositionListeners listen to all entity movements within their listened area
 * WARNING: The Entity Tracker Engine may not support listening to PLAYERS that are teleported or
 * interact with beds (maybe others) within a chunk section using ExactPositionListeners.
 * @author 2No2Name
 */
public interface ExactPositionListener extends NearbyEntityListener {

    default int getChunkRange(){ return 1; }

    /**
     * Called by the entity tracker when an entity moves and while being registered to a chunk section inside the range of this listener.
     * This is NOT a replacement for {@link NearbyEntityListener#onEntityEnteredTrackedSubchunk(Entity)}
     * nor {@link NearbyEntityListener#onEntityLeftTrackedSubchunk(Entity)}
     */
    void onEntityMovedAnyDistance(Entity entity);

    /**
     * Called by the ExactPositionListener to register itself to the EntityTrackerEngine
     * No automatic registering is done
     * @param world the world the listener listens inside
     * @param x x-Positions of the subchunks the listener listens to
     * @param y y-Positions of the subchunks the listener listens to
     * @param z z-Positions of the subchunks the listener listens to
     */
    default void registerToEntityTrackerEngine(World world, int[] x, int[] y, int[] z) {
        assert x.length == y.length && y.length == z.length;
        EntityTrackerEngine entityTracker = ((EntityTrackerEngineProvider)world).getEntityTracker();
        entityTracker.addListener(x,y,z,this);
    }

    /**
     * Called by the ExactPositionListener to deregister itself to the EntityTrackerEngine
     * No automatic deregistering is done
     * @param world the world the listener listens inside
     */
    default void deregisterFromEntityTrackerEngine(World world) {
        EntityTrackerEngine entityTracker = ((EntityTrackerEngineProvider)world).getEntityTracker();
        entityTracker.removeListener(this);
    }

}
