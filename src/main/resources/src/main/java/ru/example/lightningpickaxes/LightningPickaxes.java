package ru.example.lightningpickaxes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LightningPickaxes implements ModInitializer {
    public static final String MOD_ID = "lightning_pickaxes";

    private static final double RANGE = 64.0;
    private static final int COOLDOWN_TICKS = 40;
    private static final boolean VISUAL_ONLY = false;

    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(ItemTags.PICKAXES)) {
                return InteractionResult.PASS;
            }
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                strike(serverLevel, serverPlayer, stack);
            }
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.tickCount % 3 != 0) continue;
                ItemStack stack = player.getMainHandItem();
                if (!stack.is(ItemTags.PICKAXES) || player.getCooldowns().isOnCooldown(stack)) continue;
                ServerLevel level = (ServerLevel) player.level();
                Vec3 hand = handPosition(player);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        hand.x, hand.y, hand.z, 2, 0.25, 0.25, 0.25, 0.4);
            }
        });
    }

    private static void strike(ServerLevel level, ServerPlayer player, ItemStack stack) {
        HitResult hit = player.pick(RANGE, 1.0F, false);
        Vec3 target = hit.getLocation();

        BlockPos targetPos = BlockPos.containing(target);
        if (hit.getType() == HitResult.Type.MISS) {
            targetPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, targetPos);
            target = Vec3.atBottomCenterOf(targetPos);
        }

        drawArc(level, handPosition(player), target);

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(Vec3.atBottomCenterOf(targetPos));
            bolt.setCause(player);
            bolt.setVisualOnly(VISUAL_ONLY);
            level.addFreshEntity(bolt);
        }

        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
    }

    private static void drawArc(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        int steps = Math.max(2, (int) (length / 0.75));
        var random = level.getRandom();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double jitter = 0.35 * Math.sin(Math.PI * t);
            Vec3 p = from.add(delta.scale(t)).add(
                    (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0.1);
        }
        level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
    }

    private static Vec3 handPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return player.getEyePosition()
                .add(look.scale(0.7))
                .add(right.scale(0.35))
                .add(0, -0.3, 0);
    }
}
