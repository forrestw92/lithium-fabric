package me.jellysquid.mods.lithium.mixin.chunk.oversized_blocks;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Keep track of how many oversized blocks are in this chunk section. If none are there,
 * @author 2No2Name
 */
@Mixin(ChunkSection.class)
public class MixinChunkSection {
    private short oversizedBlockCount;

    //inject into lambda in calculateCounts
    //method name can be figured out by looking at the bytecode of ChunkSection:
    //INVOKEDYNAMIC accept(Lnet/minecraft/world/chunk/ChunkSection;)Lnet/minecraft/world/chunk/PalettedContainer$CountConsumer; [
    //      // handle kind 0x6 : INVOKESTATIC
    //      java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
    //      // arguments:
    //      (Ljava/lang/Object;I)V,               !!  HERE   !!
    //      // handle kind 0x7 : INVOKESPECIAL    VVVVVVVVVVVVV
    //      net/minecraft/world/chunk/ChunkSection.method_21731(Lnet/minecraft/block/BlockState;I)V,  <-------------HERE
    //      (Lnet/minecraft/block/BlockState;I)V
    //    ]
    @Inject(method = "method_21731", at = @At(value = "INVOKE", target = "Lnet/minecraft/fluid/FluidState;hasRandomTicks()Z", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void addToOversizedBlockCount(BlockState blockState, int i, CallbackInfo ci, FluidState fluidState) {
        if (blockState.exceedsCube() || (!fluidState.isEmpty() && fluidState.getBlockState().exceedsCube())) {
            this.oversizedBlockCount = (short)(this.oversizedBlockCount + i);
        }
    }

    @Inject(method = "calculateCounts", at = @At("HEAD"))
    private void resetOversizedBlockCount(CallbackInfo ci) {
        this.oversizedBlockCount = 0;
    }

    //@Inject(method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;hasRandomTicks()Z"))
}
