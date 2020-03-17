package me.jellysquid.mods.lithium.mixin.ai.nearby_entity_tracking;

import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine;
import me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngineProvider;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Installs event listeners to the world class which will be used to notify the {@link EntityTrackerEngine} of changes.
 * This is only required to make ExactPositionListeners possible
 */
@Mixin(TeleportCommand.class)
public class MixinTeleportCommand {
    //redirect something to avoid having to declare a parameter with type TeleportCommand.LookTarget
    @Redirect(method = "teleport", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;refreshPositionAndAngles(DDDFF)V", ordinal = 0))
    private static void notifyEntityTrackerEngine(Entity entity, double double_1, double double_2, double double_3, float float_1, float float_2) {
        EntityTrackerEngineProvider.getEntityTracker(entity.world).beforeEntityTeleport(entity);
        entity.refreshPositionAndAngles(double_1, double_2, double_3, float_1, float_2);
        EntityTrackerEngineProvider.getEntityTracker(entity.world).afterEntityTeleport(entity);
    }
}
