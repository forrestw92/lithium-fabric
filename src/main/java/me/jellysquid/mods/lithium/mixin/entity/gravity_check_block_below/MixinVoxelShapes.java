package me.jellysquid.mods.lithium.mixin.entity.gravity_check_block_below;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.stream.Stream;

@Mixin(VoxelShapes.class)
public class MixinVoxelShapes {
    /**
     * Check the block below the entity first, as it is the block that is most likely going to cancel the movement from
     * gravity.
     */
    @Inject(method = "calculatePushVelocity(Lnet/minecraft/util/math/Box;Lnet/minecraft/world/WorldView;DLnet/minecraft/block/ShapeContext;Lnet/minecraft/util/math/AxisCycleDirection;Ljava/util/stream/Stream;)D",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/AxisCycleDirection;opposite()Lnet/minecraft/util/math/AxisCycleDirection;", ordinal = 0),
            cancellable = true, locals = LocalCapture.NO_CAPTURE)
    private static void checkBelowFeet(Box box, WorldView world, double initial, ShapeContext context, AxisCycleDirection direction, Stream<VoxelShape> shapes, CallbackInfoReturnable<Double> cir) {
        //                  [VanillaCopy]
        if (initial >= 0 || direction.opposite().cycle(Direction.Axis.Z) != Direction.Axis.Y) {
            return;
        }
        int x,y,z;
        x = MathHelper.floor((box.x1 + box.x2)/2);
        y = MathHelper.ceil(box.y1) - 1;
        z = MathHelper.floor((box.z1 + box.z2)/2);
        BlockPos pos = new BlockPos(x,y,z);
        //[VanillaCopy]
        BlockState blockState = world.getBlockState(pos);
        initial = blockState.getCollisionShape(world, pos, context).calculateMaxDistance(Direction.Axis.Y, box.offset(-x, -y, -z), initial);
        if (Math.abs(initial) < 1.0E-7D) {
            cir.setReturnValue(0.0D);
        }
    }
}
