package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.WallOfFireEntity;
import com.bending.sound.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 2 (Defense): sweep your arm and a wall of flame roars up 3 blocks in front of you,
 * 14 wide and 4.5 tall, for 6 seconds, with jets of fire bursting out of the ground along it.
 * It burns up every projectile that tries to cross it (arrows, fireballs, other benders' blasts),
 * scorches and shoves back anything that walks into it, and leaves the ground along it burning.
 */
public class WallOfFire extends Ability {
    private static final double DISTANCE = 3.0;

    public WallOfFire() {
        super("wall_of_fire", Element.FIRE, AbilitySlot.DEFENSE, CastType.TAP);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 25f;
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() > 1e-4 ? flat.normalize() : Vec3.directionFromRotation(0, player.getYRot());
        Vec3 at = player.position().add(flat.scale(DISTANCE));
        // Stand it on the ground: drop down to 3 blocks onto solid footing, or climb a step.
        BlockPos pos = BlockPos.containing(at);
        double y = at.y;
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos below = pos.offset(0, dy - 1, 0);
            BlockPos here = pos.offset(0, dy, 0);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    && level.getBlockState(here).getCollisionShape(level, here).isEmpty()) {
                y = here.getY();
                break;
            }
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
        level.addFreshEntity(new WallOfFireEntity(level, player, new Vec3(at.x, y, at.z), yaw, power,
                Math.round(WallOfFireEntity.LIFE * com.bending.skill.SkillStats.ability(player, id(), com.bending.skill.SkillStat.DURATION))));
        com.bending.ability.BendingAnimations.play(player, com.bending.ability.BendingAnimations.WALL_RISE);
        level.playSound(null, at.x, y, at.z, ModSounds.WALL_OF_FIRE_RISE.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
