package me.jellysquid.mods.lithium.common.entity.tracker;

import net.minecraft.entity.Entity;

public interface EntityTrackerEngineProvider {
    EntityTrackerEngine getEntityTracker();


    boolean isEntityTrackedNow(Entity entity);
    void setEntityTrackedNow(Entity entity);

    static EntityTrackerEngine getEntityTracker(Object world) {
        return world instanceof EntityTrackerEngineProvider ? ((EntityTrackerEngineProvider) world).getEntityTracker() : null;
    }
}
