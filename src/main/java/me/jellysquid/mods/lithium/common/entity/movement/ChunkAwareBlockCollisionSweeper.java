package me.jellysquid.mods.lithium.common.entity.movement;

import me.jellysquid.mods.lithium.common.shapes.VoxelShapeCaster;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
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
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

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

    private int chunkX, chunkY, chunkZ;
    private int cStartX, cStartZ;
    private int cEndX, cEndZ;
    private int cX, cY, cZ;

    private int cTotalSize;
    private int cIterated;


    private boolean sectionOversizedBlocks;
    private Chunk cachedChunk;
    private ChunkSection cachedChunkSection;

    public ChunkAwareBlockCollisionSweeper(CollisionView view, Entity entity, Box box) {
        this.box = box;
        this.shape = VoxelShapes.cuboid(box);
        this.context = entity == null ? ShapeContext.absent() : ShapeContext.of(entity);
        this.view = view;

        this.minX = MathHelper.floor(box.minX - EPSILON);
        this.maxX = MathHelper.floor(box.maxX + EPSILON);
        this.minY = MathHelper.clamp((int)(box.minY - EPSILON),0,255);
        this.maxY = MathHelper.clamp((int)(box.maxY + EPSILON),0,255);
        this.minZ = MathHelper.floor(box.minZ - EPSILON);
        this.maxZ = MathHelper.floor(box.maxZ + EPSILON);

        this.chunkX = (this.minX - 1) >> 4;
        this.chunkZ = (this.minZ - 1) >> 4;

        this.cIterated = 0;
        this.cTotalSize = 0;

        //decrement as first nextSection call will increment it again
        this.chunkX--;
    }

    private boolean nextSection() {
        do {
            do {
                if (this.cachedChunk != null && this.chunkY < 15 && this.chunkY < ((this.maxY + 1) >> 4)) {
                    this.chunkY++;
                    this.cachedChunkSection = this.cachedChunk.getSectionArray()[this.chunkY];
                } else {
                    this.chunkY = MathHelper.clamp((this.minY - 1) >> 4, 0, 15);

                    if ((this.chunkX < ((this.maxX + 1) >> 4))) {
                        //first initialization takes this branch
                        this.chunkX++;
                    } else {
                        this.chunkX = (this.minX - 1) >> 4;

                        if (this.chunkZ < ((this.maxZ + 1) >> 4)) {
                            this.chunkZ++;
                        } else {
                            return false; //no more sections to iterate
                        }
                    }
                    //Casting to Chunk is not checked, together with other mods this could cause a ClassCastException
                    this.cachedChunk = (Chunk) this.view.getExistingChunk(this.chunkX, this.chunkZ);
                    if (this.cachedChunk != null) {
                        this.cachedChunkSection = this.cachedChunk.getSectionArray()[this.chunkY];
                    }
                }
            //skip empty chunks and empty chunk sections
            } while (this.cachedChunk == null || ChunkSection.isEmpty(this.cachedChunkSection));

            this.sectionOversizedBlocks = hasChunkSectionOversizedBlocks(this.cachedChunk, this.chunkY);

            int sizeExtension = this.sectionOversizedBlocks ? 1 : 0;

            this.cEndX = Math.min(this.maxX + sizeExtension, 15 + (this.chunkX << 4));
            int cEndY = Math.min(this.maxY + sizeExtension, 15 + (this.chunkY << 4));
            this.cEndZ = Math.min(this.maxZ + sizeExtension, 15 + (this.chunkZ << 4));

            this.cStartX = Math.max(this.minX - sizeExtension, this.chunkX << 4);
            int cStartY = Math.max(this.minY - sizeExtension, this.chunkY << 4);
            this.cStartZ = Math.max(this.minZ - sizeExtension, this.chunkZ << 4);
            this.cX = this.cStartX;
            this.cY = cStartY;
            this.cZ = this.cStartZ;

            this.cTotalSize = (this.cEndX - this.cStartX + 1) * (cEndY - cStartY + 1) * (this.cEndZ - this.cStartZ + 1);
            //skip completely empty section iterations
        } while(this.cTotalSize == 0);
        this.cIterated = 0;

        return true;
    }


    /**
     * Advances the sweep forward until finding a block with a box-colliding VoxelShape
     *
     * @return null if no VoxelShape is left in the area, otherwise the next VoxelShape
     */
    public VoxelShape step() {
        while(true) {
            if (this.cIterated >= this.cTotalSize) {
                if (!this.nextSection()) {
                    return null;
                }
            }
            this.cIterated++;


            final int x = this.cX;
            final int y = this.cY;
            final int z = this.cZ;

            //iteration order matching array order in net.minecraft.world.chunk.PalettedContainer.toIndex
            if (this.cX < this.cEndX) {
                this.cX++;
            } else if (this.cZ < this.cEndZ) {
                this.cX = this.cStartX;
                this.cZ++;
            } else {
                //stop condition not here at the very top using this.cIterated
                this.cX = this.cStartX;
                this.cZ = this.cStartZ;
                this.cY++;
            }

            final int edgesHit = this.sectionOversizedBlocks ? 0 :
                            (x < this.minX || x > this.maxX ? 1 : 0) +
                            (y < this.minY || y > this.maxY ? 1 : 0) +
                            (z < this.minZ || z > this.maxZ ? 1 : 0);

            if (edgesHit == 3) {
                continue;
            }

            final BlockState state = this.cachedChunkSection.getBlockState(x & 15, y & 15, z & 15);

            if (canInteractWithBlock(state, edgesHit)) {
                this.pos.set(x, y, z);
                VoxelShape collisionShape = CollisionShapeGetter.getCollisionShape(state, this.view, this.pos, this.context);

                if (collisionShape != VoxelShapes.empty()) {
                    VoxelShape collidedShape = getCollidedShape(this.box, this.shape, collisionShape, x, y, z);
                    if (collidedShape != null) {
                        return collidedShape;
                    }
                }
            }
        }
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
        if (shape instanceof VoxelShapeCaster) {
            if (((VoxelShapeCaster) shape).intersects(entityBox, x, y, z)) {
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
