package me.jellysquid.mods.lithium.common.entity.tracker;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.ExactPositionListener;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.NearbyEntityListener;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.NearbyEntityListenerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Tracks the entities within a world and provides notifications to listeners when a tracked entity enters or leaves a
 * watched area. This removes the necessity to constantly poll the world for nearby entities each tick and generally
 * provides a sizable boost to performance.
 */
public class EntityTrackerEngine {
    private final Long2ObjectOpenHashMap<TrackedEntityList> sections = new Long2ObjectOpenHashMap<>();
    private final HashMap<NearbyEntityListener, List<TrackedEntityList>> sectionsByListener = new HashMap<>();

    /**
     * Called when an entity is added to the world.
     */
    public void onEntityAdded(int x, int y, int z, Entity entity) {
        if (this.addEntity(x, y, z, entity)) {
            if (entity instanceof NearbyEntityListenerProvider) {
                this.addListener(x, y, z, ((NearbyEntityListenerProvider) entity).getListener());
            }
        }
    }

    /**
     * Called after an entity was teleported.
     * Makes ExactPositionListeners possible.
     * @author 2No2Name
     */
    public void afterEntityTeleport(Entity entity) {
        //do not do subchunk registrations here, as they are done separately
        this.onEntityMovedAnyDistance(entity);
    }

    /**
     * Called when an entity is removed from the world.
     */
    public void onEntityRemoved(int x, int y, int z, Entity entity) {
        if (this.removeEntity(x, y, z, entity)) {
            if (entity instanceof NearbyEntityListenerProvider) {
                this.removeListener(((NearbyEntityListenerProvider) entity).getListener());
            }
        }
    }

    /**
     * Called when an entity moves between chunks within a world. This is less expensive to call than manually
     * removing/adding an entity from chunks each time it moves.
     */
    public void onEntityMoved(int aX, int aY, int aZ, int bX, int bY, int bZ, Entity entity) {
        if (this.removeEntity(aX, aY, aZ, entity) && this.addEntity(bX, bY, bZ, entity)) {
            if (entity instanceof NearbyEntityListenerProvider) {
                this.moveListener(aX, aY, aZ, bX, bY, bZ, ((NearbyEntityListenerProvider) entity).getListener());
            }
        }
    }

    /**
     * Called when an entity moves within a world, even when not changing chunk sections.
     * This allows for tracking areas that are smaller than chunk sections precisely.
     * This method cannot be used as a replacement for the methods that handle entering and leaving chunk sections.
     *
     * Note: only listeners that listen to the chunk section the entity is registered at are notified, even
     * when the entity was just moved to another chunk section. This is intended to allow listeners to behave
     * exactly like vanilla getting entities works. E.g. hoppers only look at chunk sections two blocks around
     * their interaction area, and then check if the entities from those sections intersect with the area
     * @param entity the entity that moved or was moved
     * @author 2No2Name
     */
    public void onEntityMovedAnyDistance(Entity entity) {
        TrackedEntityList chunkSectionList = this.getList(entity.chunkX, entity.chunkY, entity.chunkZ);
        if (chunkSectionList != null) {
            chunkSectionList.onEntityMovedAnyDistance(entity);
        }
    }

    private boolean addEntity(int x, int y, int z, Entity entity) {
        return this.getOrCreateList(x, y, z).addTrackedEntity(entity);
    }

    private boolean removeEntity(int x, int y, int z, Entity entity) {
        TrackedEntityList list = this.getList(x, y, z);

        if (list == null) {
            return false;
        }

        return list.removeTrackedEntity(entity);
    }

    /**
     * Adds a listener to a cubic area
     * @param x1 most negative subchunk coordinate
     * @param y1 most negative subchunk coordinate
     * @param z1 most negative subchunk coordinate
     * @param x2 most positive subchunk coordinate
     * @param y2 most positive subchunk coordinate
     * @param z2 most positive subchunk coordinate
     * @param listener the listener to be added
     */
    private void addListener(int x1, int y1, int z1, int x2, int y2, int z2, NearbyEntityListener listener){
        List<TrackedEntityList> all = new ArrayList<>((x2-x1)*(y2-y1)*(z2-z1));

        for (int a = x1; a <= x2; a++) {
            for (int b = y1; b <= y2; b++) {
                for (int c = z1; c <= z2; c++) {
                    TrackedEntityList list = this.getOrCreateList(a, b, c);
                    list.addListener(listener);

                    all.add(list);
                }
            }
        }
        this.sectionsByListener.put(listener, all);
        listener.onInitialEntitiesReceived();
    }

    /**
     * Adds a listener to the given positions
     * @param x x-Positions of the subchunks the listener listens to
     * @param y y-Positions of the subchunks the listener listens to
     * @param z z-Positions of the subchunks the listener listens to
     * @param listener the listener to be added
     */
    public void addListener(int[] x, int[] y, int[] z, NearbyEntityListener listener) {
        assert x.length == y.length && y.length == z.length;

        List<TrackedEntityList> all = new ArrayList<>(x.length);
        for (int i = 0; i < x.length; i++) {
            TrackedEntityList list = this.getOrCreateList(x[i], y[i], z[i]);
            list.addListener(listener);

            all.add(list);
        }
        this.sectionsByListener.put(listener, all);
        listener.onInitialEntitiesReceived();
    }

    /**
     * Adds a listener to a cubic area
     * @param x center pos
     * @param y center pos
     * @param z center
     * @param listener listener to be added. get the listening range from it
     */
    private void addListener(int x, int y, int z, final NearbyEntityListener listener) {
        int r = listener.getChunkRange();

        if (r == 0) {
            return;
        }

        int size = (2*r + 1);
        size = size * size * size;

        List<TrackedEntityList> all = new ArrayList<>(size);

        for (int x2 = x - r; x2 <= x + r; x2++) {
            for (int y2 = y - r; y2 <= y + r; y2++) {
                for (int z2 = z - r; z2 <= z + r; z2++) {
                    TrackedEntityList list = this.getOrCreateList(x2, y2, z2);
                    list.addListener(listener);

                    all.add(list);
                }
            }
        }
        this.sectionsByListener.put(listener, all);
        listener.onInitialEntitiesReceived();
    }

    public void removeListener(NearbyEntityListener listener) {
        int r = listener.getChunkRange();

        if (r == 0) {
            return;
        }

        List<TrackedEntityList> all = this.sectionsByListener.remove(listener);

        if (all != null) {
            for (TrackedEntityList list : all) {
                list.removeListener(listener);
            }
        } else {
            throw new IllegalArgumentException("Entity listener not tracked");
        }
    }

    // Faster implementation which avoids removing from/adding to every list twice on an entity move event
    private void moveListener(int aX, int aY, int aZ, int bX, int bY, int bZ, NearbyEntityListener listener) {
        int radius = listener.getChunkRange();

        if (radius == 0) {
            return;
        }

        BlockBox before = new BlockBox(aX - radius, aY - radius, aZ - radius, aX + radius, aY + radius, aZ + radius);
        BlockBox after = new BlockBox(aX - radius, aY - radius, aZ - radius, bX + radius, bY + radius, bZ + radius);

        BlockBox merged = new BlockBox(before);
        merged.encompass(after);

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = merged.minX; x <= merged.maxX; x++) {
            for (int y = merged.minY; y <= merged.maxY; y++) {
                for (int z = merged.minZ; z <= merged.maxZ; z++) {
                    pos.set(x, y, z);

                    boolean leaving = before.contains(pos);
                    boolean entering = after.contains(pos);

                    // Nothing to change
                    if (leaving == entering) {
                        continue;
                    }

                    if (leaving) {
                        // The listener has left the chunk
                        TrackedEntityList list = this.getList(x, y, z);

                        if (list == null) {
                            throw new IllegalStateException("Expected there to be a listener list while moving entity but there was none");
                        }

                        list.removeListener(listener);
                    } else {
                        // The listener has entered the chunk
                        TrackedEntityList list = this.getOrCreateList(x, y, z);
                        list.addListener(listener);
                    }
                }
            }
        }
        listener.onInitialEntitiesReceived();
    }

    private TrackedEntityList getOrCreateList(int x, int y, int z) {
        return this.sections.computeIfAbsent(encode(x, y, z), TrackedEntityList::new);
    }

    private TrackedEntityList getList(int x, int y, int z) {
        return this.sections.get(encode(x, y, z));
    }

    private static long encode(int x, int y, int z) {
        return ChunkSectionPos.asLong(x, y, z);
    }

    private class TrackedEntityList {
        private final Set<Entity> entities = new ReferenceOpenHashSet<>();
        private final Set<NearbyEntityListener> listeners = new ReferenceOpenHashSet<>();
        private final Set<ExactPositionListener> exactPositionListeners = new ReferenceOpenHashSet<>();

        private final long key;

        private TrackedEntityList(long key) {
            this.key = key;
        }

        public void addListener(NearbyEntityListener listener) {
            for (Entity entity : this.entities) {
                listener.onEntityEnteredTrackedSubchunk(entity);
            }

            this.listeners.add(listener);
            if (listener instanceof ExactPositionListener) {
                this.exactPositionListeners.add((ExactPositionListener) listener);
            }
        }

        public void removeListener(NearbyEntityListener listener) {
            if (this.listeners.remove(listener)) {
                for (Entity entity : this.entities) {
                    listener.onEntityLeftTrackedSubchunk(entity);
                }
                if (listener instanceof ExactPositionListener) {
                    this.exactPositionListeners.remove(listener);
                }

                this.checkEmpty();
            }
        }

        public void onEntityMovedAnyDistance(Entity entity) {
            if (this.exactPositionListeners.isEmpty()) return;
            for (ExactPositionListener listener : this.exactPositionListeners) {
                listener.onEntityMovedAnyDistance(entity);
            }
        }

        public boolean addTrackedEntity(Entity entity) {
            for (NearbyEntityListener listener : this.listeners) {
                listener.onEntityEnteredTrackedSubchunk(entity);
            }

            return this.entities.add(entity);
        }

        public boolean removeTrackedEntity(Entity entity) {
            boolean ret = this.entities.remove(entity);

            if (ret) {
                for (NearbyEntityListener listener : this.listeners) {
                    listener.onEntityLeftTrackedSubchunk(entity);
                }

                this.checkEmpty();
            }

            return ret;
        }

        private void checkEmpty() {
            if (this.entities.isEmpty() && this.listeners.isEmpty()) {
                EntityTrackerEngine.this.sections.remove(this.key);
            }
        }
    }
}
