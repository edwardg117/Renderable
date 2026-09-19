package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.ParticleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Turns Minecraft objects into core keys. Called once per object per session, so it may allocate. */
final class KeyFactory {

    private KeyFactory() {
    }

    static BlockEntityKey blockEntity(BlockEntity be) {
        ResourceLocation id = BlockEntityType.getKey(be.getType());
        String typeId = id == null ? be.getClass().getName() : id.toString();
        Level level = be.getLevel();
        String dimension = level == null ? "unknown" : level.dimension().location().toString();
        BlockPos p = be.getBlockPos();
        return new BlockEntityKey(typeId, dimension, p.getX(), p.getY(), p.getZ());
    }

    static EntityKey entity(Entity e) {
        String typeId = EntityType.getKey(e.getType()).toString();
        String name;
        if (e instanceof Player player) {
            name = player.getGameProfile().getName();
        } else if (e.hasCustomName() && e.getCustomName() != null) {
            name = e.getCustomName().getString();
        } else {
            name = "";
        }
        return new EntityKey(typeId, e.getId(), name);
    }

    static ParticleKey particle(Class<?> particleClass) {
        return new ParticleKey(particleClass.getName());
    }
}
