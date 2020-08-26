package me.jellysquid.mods.lithium.common.entity.tracker;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.NearbyEntityListener;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.NearbyEntityListenerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks the entities within a world and provides notifications to listeners when a tracked entity enters or leaves a
 * watched area. This removes the necessity to constantly poll the world for nearby entities each tick and generally
 * provides a sizable boost to performance.
 */
public class EntityTrackerEngine {
    private final Long2ObjectOpenHashMap<TrackedEntityList> sections;
    private final Reference2ReferenceOpenHashMap<NearbyEntityListener, List<TrackedEntityList>> sectionsByEntity;

    private final Reference2LongOpenHashMap<Entity> allEntities;


    public EntityTrackerEngine() {
        this.sections = new Long2ObjectOpenHashMap<>();
        this.sectionsByEntity = new Reference2ReferenceOpenHashMap<>();
        this.allEntities = new Reference2LongOpenHashMap<>();
    }

    /**
     * Called when an entity is added to the world.
     */
    public void onEntityAdded(int x, int y, int z, LivingEntity entity) {
        if (this.addEntity(x, y, z, entity)) {
            if (entity instanceof NearbyEntityListenerProvider) {
                this.addListener(x, y, z, ((NearbyEntityListenerProvider) entity).getListener());
            }
        }
    }

    /**
     * Called when an entity is removed from the world.
     */
    public void onEntityRemoved(int x, int y, int z, LivingEntity entity) {
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
    public void onEntityMoved(int aX, int aY, int aZ, int bX, int bY, int bZ, LivingEntity entity) {
        if (this.removeEntity(aX, aY, aZ, entity) && this.addEntity(bX, bY, bZ, entity)) {
            if (entity instanceof NearbyEntityListenerProvider) {
                this.moveListener(aX, aY, aZ, bX, bY, bZ, ((NearbyEntityListenerProvider) entity).getListener());
            }
        }
    }

    private boolean addEntity(int x, int y, int z, LivingEntity entity) {
        if (this.allEntities.containsKey(entity)) {
            errorDoubleAdd(entity, x, y, z, ChunkSectionPos.from(this.allEntities.getLong(entity)));
        } else {
            this.allEntities.put(entity, encode(x,y,z));
        }

        return this.getOrCreateList(x, y, z).addTrackedEntity(entity);
    }

    private boolean removeEntity(int x, int y, int z, LivingEntity entity) {
        if (!this.allEntities.containsKey(entity) || this.allEntities.getLong(entity) != encode(x,y,z)) {
            errorWrongRemove(entity, x, y, z, ChunkSectionPos.from(this.allEntities.getLong(entity)));
        } else {
            this.allEntities.removeLong(entity);
        }

        TrackedEntityList list = this.getList(x, y, z);

        if (list == null) {
            return false;
        }

        return list.removeTrackedEntity(entity);
    }

    private void addListener(int x, int y, int z, NearbyEntityListener listener) {
        int r = listener.getChunkRange();

        if (r == 0) {
            return;
        }

        if (this.sectionsByEntity.containsKey(listener)) {
            errorMessageAlreadyListening(this.sectionsByEntity, listener, ChunkSectionPos.from(x,y,z));
        }

        int yMin = Math.max(0, y - r);
        int yMax = Math.min(y + r, 15);

        List<TrackedEntityList> all = new ArrayList<>((2*r+1) * (yMax - yMin +1) * (2*r+1));

        for (int x2 = x - r; x2 <= x + r; x2++) {
            for (int y2 = yMin; y2 <= yMax; y2++) {
                for (int z2 = z - r; z2 <= z + r; z2++) {
                    TrackedEntityList list = this.getOrCreateList(x2, y2, z2);
                    list.addListener(listener);

                    all.add(list);
                }
            }
        }

        this.sectionsByEntity.put(listener, all);
    }

    private void removeListener(NearbyEntityListener listener) {
        int r = listener.getChunkRange();

        if (r == 0) {
            return;
        }

        List<TrackedEntityList> all = this.sectionsByEntity.remove(listener);

        if (all != null) {
            for (TrackedEntityList list : all) {
                list.removeListener(listener);
            }
        } else {
            throw new IllegalArgumentException("Entity listener not tracked:" + listener.toString());
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

    private static ChunkSectionPos decode(long xyz) {
        return ChunkSectionPos.from(xyz);
    }

    private class TrackedEntityList {
        private final Set<LivingEntity> entities = new ReferenceOpenHashSet<>();
        private final Set<NearbyEntityListener> listeners = new ReferenceOpenHashSet<>();

        private final long key;

        private TrackedEntityList(long key) {
            this.key = key;
        }

        public void addListener(NearbyEntityListener listener) {
            for (LivingEntity entity : this.entities) {
                listener.onEntityEnteredRange(entity);
            }

            this.listeners.add(listener);
        }

        public void removeListener(NearbyEntityListener listener) {
            if (this.listeners.remove(listener)) {
                for (LivingEntity entity : this.entities) {
                    listener.onEntityLeftRange(entity);
                }

                this.checkEmpty();
            }
        }

        public boolean addTrackedEntity(LivingEntity entity) {
            for (NearbyEntityListener listener : this.listeners) {
                listener.onEntityEnteredRange(entity);
            }

            final boolean addSuccess = this.entities.add(entity);
            if(!addSuccess) {

            }
            return addSuccess;
        }

        public boolean removeTrackedEntity(LivingEntity entity) {
            boolean ret = this.entities.remove(entity);

            if (ret) {
                for (NearbyEntityListener listener : this.listeners) {
                    listener.onEntityLeftRange(entity);
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


    private static void errorMessageAlreadyListening(Reference2ReferenceOpenHashMap<NearbyEntityListener, List<TrackedEntityList>> sectionsByEntity, NearbyEntityListener listener, ChunkSectionPos newLocation) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Adding Entity listener a second time: ").append(listener.toString());
        messageBuilder.append("\n");
        messageBuilder.append(" wants to listen at: ").append(newLocation.toString());
        messageBuilder.append(" with cube radius: ").append(listener.getChunkRange());
        messageBuilder.append("\n");
        messageBuilder.append(" but was already listening at chunk sections: ");
        if (sectionsByEntity.get(listener) == null) {
            messageBuilder.append("null");
        } else {
            messageBuilder.append(sectionsByEntity.get(listener).stream().map(trackedEntityList -> decode(trackedEntityList.key).toString()).collect(Collectors.joining(",")));
        }
        throw new IllegalStateException(messageBuilder.toString());
    }


    private static void errorDoubleAdd(LivingEntity entity, int x, int y, int z, ChunkSectionPos oldPos) {
        String message = "Adding Entity a second time: " + entityToErrorString(entity) +
                "\n" +
                "at chunk pos: " + ChunkSectionPos.from(x, y, z).toString() +
                "\n" +
                "but already added at: " + oldPos.toString();
        throw new IllegalStateException(message);
    }
    private static void errorWrongRemove(LivingEntity entity, int x, int y, int z, ChunkSectionPos oldPos) {
        String message = "Removing Entity: " + entityToErrorString(entity) +
                "\n" +
                "at chunk pos: " + ChunkSectionPos.from(x, y, z).toString() +
                "\n" +
                "but wasn't registered there!" +
                "\n" +
                (oldPos == null ? "was not registered in the EntityTrackerEngine." : "was registered at: " + oldPos.toString());
        throw new IllegalStateException(message);
    }

    private static String entityToErrorString(Entity entity) {
        return entity.toString() + " with NBT: " + entity.toTag(new CompoundTag());
    }

}
