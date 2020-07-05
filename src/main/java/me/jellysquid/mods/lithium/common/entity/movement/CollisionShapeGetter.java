package me.jellysquid.mods.lithium.common.entity.movement;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public class CollisionShapeGetter {
    public static VoxelShape getCollisionShape(AbstractBlock.AbstractBlockState block, BlockView world, BlockPos pos, ShapeContext context) {
        return block.shapeCache != null && block.getBlock() != Blocks.LAVA ? block.shapeCache.collisionShape : block.getCollisionShape(world, pos, context);
    }
}
