package me.jellysquid.mods.lithium.common.entity;

import me.jellysquid.mods.lithium.common.world.WorldHelper;
import me.jellysquid.mods.lithium.common.shapes.VoxelShapeExtended;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import me.jellysquid.mods.lithium.common.entity.movement.BlockCollisionSweeper;
import me.jellysquid.mods.lithium.common.util.Producer;
import net.minecraft.entity.Entity;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.CollisionView;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.minecraft.predicate.entity.EntityPredicates.EXCEPT_SPECTATOR;

public class LithiumEntityCollisions {
    public static final double EPSILON = 1.0E-7D;

    /**
     * [VanillaCopy] CollisionView#getBlockCollisions(Entity, Box)
     * This is a much, much faster implementation which uses simple collision testing against full-cube block shapes.
     * Checks against the world border are replaced with our own optimized functions which do not go through the
     * VoxelShape system.
     */
    public static Stream<VoxelShape> getBlockCollisions(CollisionView world, Entity entity, Box box) {
        if (isBoxEmpty(box)) {
            return Stream.empty();
        }

        final BlockCollisionSweeper sweeper = new BlockCollisionSweeper(world, entity, box);

        return StreamSupport.stream(new Spliterators.AbstractSpliterator<VoxelShape>(Long.MAX_VALUE, Spliterator.NONNULL | Spliterator.IMMUTABLE) {
            private boolean skipWorldBorderCheck = entity == null;

            @Override
            public boolean tryAdvance(Consumer<? super VoxelShape> consumer) {
                if (!this.skipWorldBorderCheck) {
                    this.skipWorldBorderCheck = true;

                    if (canEntityCollideWithWorldBorder(world, entity)) {
                        consumer.accept(world.getWorldBorder().asVoxelShape());

                        return true;
                    }
                }

                while (sweeper.step()) {
                    VoxelShape shape = sweeper.getCollidedShape();

                    if (shape != null) {
                        consumer.accept(shape);

                        return true;
                    }
                }

                return false;
            }
        }, false);
    }

    /**
     * See {@link LithiumEntityCollisions#getBlockCollisions(CollisionView, Entity, Box)}
     *
     * @return True if the box (possibly that of an entity's) collided with any blocks
     */
    public static boolean doesBoxCollideWithBlocks(CollisionView world, Entity entity, Box box) {
        if (isBoxEmpty(box)) {
            return false;
        }

        final BlockCollisionSweeper sweeper = new BlockCollisionSweeper(world, entity, box);

        while (sweeper.step()) {
            if (sweeper.getCollidedShape() != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * See {@link LithiumEntityCollisions#getEntityCollisions(EntityView, Entity, Box, Predicate)}
     *
     * @return True if the box (possibly that of an entity's) collided with any other entities
     */
    public static boolean doesBoxCollideWithEntities(EntityView view, Entity entity, Box box, Predicate<Entity> predicate) {
        if (isBoxEmpty(box)) {
            return false;
        }

        return getEntityCollisionProducer(view, entity, box, predicate).computeNext(null);
    }

    /**
     * Returns a stream of entity collision boxes.
     */
    public static Stream<VoxelShape> getEntityCollisions(EntityView view, Entity entity, Box box, Predicate<Entity> predicate) {
        if (isBoxEmpty(box)) {
            return Stream.empty();
        }

        return Producer.asStream(getEntityCollisionProducer(view, entity, box.expand(EPSILON), predicate));
    }

    /**
     * [VanillaCopy] EntityView#getEntityCollisions
     * Re-implements the function named above without stream code or unnecessary allocations. This can provide a small
     * boost in some situations (such as heavy entity crowding) and reduces the allocation rate significantly.
     */
    public static Producer<VoxelShape> getEntityCollisionProducer(EntityView view, Entity entity, Box box, Predicate<Entity> predicate) {
        return new Producer<VoxelShape>() {
            private Iterator<Entity> it;

            @Override
            public boolean computeNext(Consumer<? super VoxelShape> consumer) {
                if (this.it == null) {
                    this.it = view.getEntities(entity, box).iterator();
                }

                while (this.it.hasNext()) {
                    Entity otherEntity = this.it.next();

                    if (!predicate.test(otherEntity)) {
                        continue;
                    }

                    if (entity != null && entity.isConnectedThroughVehicle(otherEntity)) {
                        continue;
                    }

                    Box otherEntityBox = otherEntity.getCollisionBox();

                    boolean produced = false;

                    if (otherEntityBox != null && box.intersects(otherEntityBox)) {
                        if (consumer == null) {
                            return true;
                        } else {
                            produced = true;
                            consumer.accept(VoxelShapes.cuboid(otherEntityBox));
                        }
                    }

                    if (entity != null) {
                        Box otherEntityHardBox = entity.getHardCollisionBox(otherEntity);

                        if (otherEntityHardBox != null && box.intersects(otherEntityHardBox)) {
                            if (consumer == null) {
                                return true;
                            } else {
                                produced = true;
                                consumer.accept(VoxelShapes.cuboid(otherEntityHardBox));
                            }
                        }
                    }

                    if (produced) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    /**
     * This provides a faster check for seeing if an entity is within the world border as it avoids going through
     * the slower shape system.
     *
     * @return True if the {@param box} is fully within the {@param border}, otherwise false.
     */
    public static boolean isBoxFullyWithinWorldBorder(WorldBorder border, Box box) {
        double wboxMinX = Math.floor(border.getBoundWest());
        double wboxMinZ = Math.floor(border.getBoundNorth());

        double wboxMaxX = Math.ceil(border.getBoundEast());
        double wboxMaxZ = Math.ceil(border.getBoundSouth());

        return box.x1 >= wboxMinX && box.x1 < wboxMaxX && box.z1 >= wboxMinZ && box.z1 < wboxMaxZ &&
                box.x2 >= wboxMinX && box.x2 < wboxMaxX && box.z2 >= wboxMinZ && box.z2 < wboxMaxZ;
    }

    private static boolean canEntityCollideWithWorldBorder(CollisionView world, Entity entity) {
        WorldBorder border = world.getWorldBorder();

        boolean isInsideBorder = isBoxFullyWithinWorldBorder(border, entity.getBoundingBox().contract(EPSILON));
        boolean isCrossingBorder = isBoxFullyWithinWorldBorder(border, entity.getBoundingBox().expand(EPSILON));

        return !isInsideBorder && isCrossingBorder;
    }

    /**
     * Partial [VanillaCopy] Classes overriding Entity.getHardCollisionBox(Entity other) or Entity.getCollisionBox()
     * The returned entity list is only used to call getCollisionBox and getHardCollisionBox. As most entities return null
     * for both of these methods, getting those is not necessary. This is why we only get entities when they overwrite
     * getCollisionBox
     * @param entityView the world
     * @param selection the box the entities have to collide with
     * @param entity the entity that is searching for the colliding entities
     * @return list of entities with collision boxes
     */
    public static List<Entity> getEntitiesWithCollisionBoxForEntity(EntityView entityView, Box selection, Entity entity) {
        if (entity != null && EntityClassGroup.HARD_COLLISION_BOX_OVERRIDE.contains(entity.getClass()) || !(entityView instanceof World)) {
            //use vanilla code when getHardCollisionBox(Entity other) is overwritten, as every entity could be relevant as argument of getHardCollisionBox
            return entityView.getEntities(entity, selection);
        } else {
            //only get entities that overwrite getCollisionBox
            return WorldHelper.getEntitiesOfClassGroup((World)entityView, entity, EntityClassGroup.COLLISION_BOX_OVERRIDE, selection, EXCEPT_SPECTATOR);
        }
    }

    /**
     * Interface to group entity types that don't always return null on getCollisionBox.
     */
    public interface CollisionBoxOverridingEntity {}

    private static boolean isBoxEmpty(Box box) {
        return box.getAverageSideLength() <= EPSILON;
    }
}