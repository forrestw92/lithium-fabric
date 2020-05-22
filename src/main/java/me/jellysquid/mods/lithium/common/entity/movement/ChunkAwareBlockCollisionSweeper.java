package me.jellysquid.mods.lithium.common.entity.movement;

import me.jellysquid.mods.lithium.common.shapes.VoxelShapeExtended;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.CuboidBlockIterator;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.CollisionView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import static me.jellysquid.mods.lithium.common.entity.LithiumEntityCollisions.EPSILON;

/**
 * ChunkAwareBlockCollisionSweeper iterates over blocks in one chunk section at a time. Together with the chunk
 * section keeping track of the amount of oversized blocks inside the number of iterations can often be reduced.
 */
public class ChunkAwareBlockCollisionSweeper {
    private static final CuboidBlockIterator EMPTY_ITERATOR = new CuboidBlockIterator(0,0,0,-1,-1,-1);

    private final BlockPos.Mutable mpos = new BlockPos.Mutable();

    /**
     * The collision box being swept through the world.
     */
    private final Box box;

    /**
     * The VoxelShape of the collision box being swept through the world.
     */
    private final VoxelShape shape;

    private final CollisionView view;
    private final ShapeContext context;

    private final int minX, minY, minZ, maxX, maxY, maxZ;

    private int chunkX,chunkY,chunkZ;
    private CuboidBlockIterator chunkIt;
    private boolean sizeDecreased;
    private ChunkSection cachedChunkSection;


    private VoxelShape collidedShape;

    public ChunkAwareBlockCollisionSweeper(CollisionView view, Entity entity, Box box) {
        this.box = box;
        this.shape = VoxelShapes.cuboid(box);
        this.context = entity == null ? ShapeContext.absent() : ShapeContext.of(entity);
        this.view = view;

        this.minX = MathHelper.floor(box.x1 - EPSILON) - 1;
        this.maxX = MathHelper.floor(box.x2 + EPSILON) + 1;
        this.minY = MathHelper.floor(box.y1 - EPSILON) - 1;
        this.maxY = MathHelper.floor(box.y2 + EPSILON) + 1;
        this.minZ = MathHelper.floor(box.z1 - EPSILON) - 1;
        this.maxZ = MathHelper.floor(box.z2 + EPSILON) + 1;

        //initialize chunkX reduced by 1, because it will be increased in the first call of createSectionIterator
        this.chunkX = (this.minX >> 4) - 1;
        this.chunkY = MathHelper.clamp(this.minY >> 4, 0, 15);
        this.chunkZ = this.minZ >> 4;

        this.createSectionIterator();
    }

    private boolean createSectionIterator() {
        Chunk chunk;
        do {
            if (this.chunkX < (this.maxX >> 4)) {
                this.chunkX++;
            } else {
                this.chunkX = this.minX >> 4;
                if (this.chunkY < (this.maxY >> 4) && this.chunkY < 15) {
                    this.chunkY++;
                } else {
                    this.chunkY = MathHelper.clamp(this.minY >> 4, 0, 15);
                    if (this.chunkZ < (this.maxZ >> 4)) {
                        this.chunkZ++;
                    } else {
                        this.chunkIt = EMPTY_ITERATOR;
                        return false;
                    }
                }
            }
            //Casting to Chunk is not checked, together with other mods this could cause a ClassCastException
            chunk = (Chunk) this.view.getExistingChunk(this.chunkX, this.chunkZ);
        //skip empty chunk sections
        } while (chunk == null || ChunkSection.isEmpty(this.cachedChunkSection = chunk.getSectionArray()[this.chunkY]));

        this.sizeDecreased = !hasChunkSectionOversizedBlocks(chunk, this.chunkY);
        int sizeReduction = !this.sizeDecreased ? 0 : 1;

        int startX = Math.max(this.minX + sizeReduction, this.chunkX << 4);
        int startY = Math.max(0, Math.max(this.minY + sizeReduction, this.chunkY << 4));
        int startZ = Math.max(this.minZ + sizeReduction, this.chunkZ << 4);
        int endX = Math.min(this.maxX - sizeReduction, 15 + (this.chunkX << 4));
        int endY = Math.min(255, Math.min(this.maxY - sizeReduction, 15 + (this.chunkY << 4)));
        int endZ = Math.min(this.maxZ - sizeReduction, 15 + (this.chunkZ << 4));

        if (startX <= endX && startY <= endY && startZ <= endZ) {
            this.chunkIt = new CuboidBlockIterator(startX, startY, startZ, endX, endY, endZ);
            return true;
        }

        return this.createSectionIterator();
    }

    /**
     * Advances the sweep forward until finding a block, updating the return value of
     * {@link ChunkAwareBlockCollisionSweeper#getCollidedShape()} with a block shape if the sweep collided with it.
     *
     * @return True if there are blocks left to be tested, otherwise false
     */
    public boolean step() {
        this.collidedShape = null;

        while(true) {
            if (!this.chunkIt.step()) {
                if (this.chunkIt == EMPTY_ITERATOR || !this.createSectionIterator()) {
                    return false;
                }
                if (!this.chunkIt.step()) {
                    //iterator always has at least 1 block due to size check in createSectionIterator!
                    assert false;
                    return false;
                }
            }

            final CuboidBlockIterator cuboidIt = this.chunkIt;
            final int x = cuboidIt.getX();
            final int y = cuboidIt.getY();
            final int z = cuboidIt.getZ();

            final int edgesHit = this.sizeDecreased ? 0 :
                    (x == this.minX || x == this.maxX ? 1 : 0) +
                            (y == this.minY || y == this.maxY ? 1 : 0) +
                            (z == this.minZ || z == this.maxZ ? 1 : 0);

            if (edgesHit == 3) {
                continue;
            }

            final BlockState state = this.cachedChunkSection.getBlockState(x & 15, y & 15, z & 15);

            if (canInteractWithBlock(state, edgesHit)) {
                final BlockPos.Mutable mpos = this.mpos;
                mpos.set(x, y, z);
                VoxelShape collisionShape = state.getCollisionShape(this.view, mpos, this.context);

                if (collisionShape != VoxelShapes.empty()) {
                    this.collidedShape = getCollidedShape(this.box, this.shape, collisionShape, x, y, z);
                    return true;
                }
            }
        }
    }

    /**
     * @return The shape collided with during the last step, otherwise null
     */
    public VoxelShape getCollidedShape() {
        return this.collidedShape;
    }

    /**
     * This is an artifact from vanilla which is used to avoid testing shapes in the extended portion of a volume
     * unless they are a shape which exceeds their voxel. Pistons must be special-cased here.
     *
     * @return True if the shape can be interacted with at the given edge boundary
     */
    private static boolean canInteractWithBlock(BlockState state, int edgesHit) {
        return (edgesHit != 1 || state.exceedsCube()) && (edgesHit != 2 || state.getBlock() == Blocks.MOVING_PISTON);
    }

    /**
     * Checks if the {@param entityShape} or {@param entityBox} intersects the given {@param shape} which is translated
     * to the given position. This is a very specialized implementation which tries to avoid going through VoxelShape
     * for full-cube shapes.
     *
     * @return A {@link VoxelShape} which contains the shape representing that which was collided with, otherwise null
     */
    private static VoxelShape getCollidedShape(Box entityBox, VoxelShape entityShape, VoxelShape shape, int x, int y, int z) {
        if (shape instanceof VoxelShapeExtended) {
            if (((VoxelShapeExtended) shape).intersects(entityBox, x, y, z)) {
                return shape.offset(x, y, z);
            } else {
                return null;
            }
        }

        shape = shape.offset(x, y, z);

        if (VoxelShapes.matchesAnywhere(shape, entityShape, BooleanBiFunction.AND)) {
            return shape;
        }

        return null;
    }

    /**
     * Checks the cached information whether the {@param chunkY} section of the {@param chunk} has oversized blocks.
     * @return Whether there are any oversized blocks in the chunk section.
     */
    private static boolean hasChunkSectionOversizedBlocks(Chunk chunk, int chunkY) {
        ChunkSection section = chunk.getSectionArray()[chunkY];
        return section != null && ((OversizedBlocksCounter)section).hasOversizedBlocks();
    }
    public interface OversizedBlocksCounter {
        boolean hasOversizedBlocks();
    }
}
