package com.achilles.events;

import com.achilles.block.StatueBlockEntity;
import com.achilles.data.StatueRegistry;
import com.achilles.entity.AchillesEntity;
import com.achilles.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModEvents {

    // ------------------------------------------------------------ utilidades

    private static StatueBlockEntity zoneAt(Level level, BlockPos pos) {
        return StatueRegistry.findCovering(level, Vec3.atCenterOf(pos));
    }

    /** Mobs "del jugador": mascotas, caballos domados, golems de hierro construidos y mini Aquiles. */
    private static boolean isPlayerMob(LivingEntity entity, StatueBlockEntity statue) {
        if (entity instanceof OwnableEntity pet) {
            UUID owner = pet.getOwnerUUID();
            return owner != null && statue.isAllowed(owner);
        }
        if (entity instanceof AbstractHorse horse) return horse.isTamed();
        if (entity instanceof IronGolem golem) return golem.isPlayerCreated();
        return entity instanceof AchillesEntity;
    }

    // ------------------------------------------------------------ bloques

    /** La estatua es indestructible; y dentro de una zona solo rompen los jugadores con permiso. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(ModBlocks.STATUE.get())) {
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            StatueBlockEntity zone = zoneAt(level, event.getPos());
            if (zone != null && !zone.isAllowed(event.getPlayer())) {
                event.setCanceled(true);
            }
        }
    }

    /** Sin permiso no se puede ni empezar a picar un bloque de dentro de la zona. */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        StatueBlockEntity zone = zoneAt(level, event.getPos());
        if (zone != null && !zone.isAllowed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Sin permiso no se puede usar nada sobre la zona: ni colocar fuego o lava desde fuera,
     * ni abrir cofres o puertas de dentro. La propia estatua sí se puede tocar (para ver de quién es).
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        if (level.getBlockState(event.getPos()).is(ModBlocks.STATUE.get())) return;
        Direction face = event.getFace();
        StatueBlockEntity zone = zoneAt(level, event.getPos());
        if (zone == null && face != null) {
            zone = zoneAt(level, event.getPos().relative(face));
        }
        if (zone != null && !zone.isAllowed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        StatueBlockEntity zone = StatueRegistry.findCovering(player.level(), event.getTarget().position());
        if (zone != null && !zone.isAllowed(player)
                && !(event.getTarget() instanceof Enemy)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        StatueBlockEntity zone = StatueRegistry.findCovering(level, event.getTarget().position());
        if (zone != null && !zone.isAllowed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Los mobs hostiles no destruyen ni mueven bloques dentro de una zona (enderman, zombis con puertas, wither...). */
    @SubscribeEvent
    public void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Enemy && StatueRegistry.findCovering(entity.level(), entity.position()) != null) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Nadie pisotea los cultivos de una zona protegida. */
    @SubscribeEvent
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && zoneAt(level, event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    /** El fuego que aparezca dentro de una zona (rayos, lava, fuego esparcido) se apaga al instante. */
    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getState().getBlock() instanceof BaseFireBlock)) return;
        BlockPos pos = event.getPos();
        if (zoneAt(level, pos) == null) return;
        level.getServer().execute(() -> {
            if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
            }
        });
    }

    // ------------------------------------------------------------ daño

    /** Evita bucles: reflejar daño no debe provocar nuevos reflejos en cadena. */
    private static boolean reflecting = false;

    /** Máximo de daño que devuelve la estatua de una sola vez. */
    private static final float MAX_REFLECTED_DAMAGE = 40.0F;

    /**
     * Dentro de la zona:
     *  - los jugadores con permiso no reciben daño (salvo vacío y /kill);
     *  - sus mobs (mascotas, caballos domados, golems, mini Aquiles) tampoco, venga de quien venga;
     *  - los demás seres no hostiles solo son dañables por jugadores con permiso;
     *  - y todo ese daño se devuelve (reflejado) a quien lo causó.
     */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        StatueBlockEntity statue = StatueRegistry.findCovering(victim.level(), victim.position());
        if (statue == null) return;

        Entity attacker = source.getEntity();
        boolean attackerAllowed = attacker instanceof Player p && statue.isAllowed(p);

        if (victim instanceof ServerPlayer player) {
            if (!statue.isAllowed(player)) return;
            event.setCanceled(true);
        } else if (isPlayerMob(victim, statue)) {
            event.setCanceled(true);
        } else if (victim instanceof Player || victim instanceof Enemy
                || victim.getType().is(Tags.EntityTypes.BOSSES) || attackerAllowed) {
            return;
        } else {
            event.setCanceled(true);
        }

        if (reflecting || attacker == null || attacker == victim || attackerAllowed) return;
        if (!(attacker instanceof LivingEntity target) || !target.isAlive()) return;

        reflecting = true;
        try {
            float amount = Math.min(event.getAmount(), MAX_REFLECTED_DAMAGE);
            target.hurt(victim.damageSources().thorns(victim), amount);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6D), target.getZ(),
                        14, 0.3D, 0.4D, 0.3D, 0.15D);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.9F);
            }
            if (target instanceof ServerPlayer hit) {
                hit.displayClientMessage(Component.translatable("message.achilles.reflected"), true);
            }
        } finally {
            reflecting = false;
        }
    }

    /**
     * El santuario protege sus bloques y a quienes están dentro de las explosiones:
     * ni bloques rotos, ni entidades dañadas o empujadas.
     */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var explosion = event.getExplosion();
        var center = explosion.getPosition();
        StatueBlockEntity centerStatue = StatueRegistry.findCovering(level, center);
        if (centerStatue != null) {
            event.getAffectedBlocks().removeIf(blockPos -> centerStatue.contains(blockPos.getCenter()));
        } else {
            // La explosión puede nacer fuera de la zona y aun así alcanzar bloques protegidos.
            event.getAffectedBlocks().removeIf(blockPos ->
                    StatueRegistry.findCovering(level, blockPos.getCenter()) != null);
        }
        event.getAffectedEntities().removeIf(entity ->
                !(entity instanceof Enemy) && StatueRegistry.findCovering(level, entity.position()) != null);
    }

    /**
     * Las estatuas no se mueven con pistones, y ningún pistón de fuera puede empujar,
     * tirar o destruir bloques de dentro de una zona (ni meter bloques en ella).
     */
    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        if (level.getBlockState(event.getPos()).is(ModBlocks.STATUE.get())) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) return;   // si no puede moverse, no hay nada que proteger

        StatueBlockEntity pistonZone = zoneAt(level, event.getPos());
        Direction push = helper.getPushDirection();
        List<BlockPos> touched = new ArrayList<>(helper.getToPush());
        touched.addAll(helper.getToDestroy());
        for (BlockPos moved : touched) {
            if (level.getBlockState(moved).is(ModBlocks.STATUE.get())) {
                event.setCanceled(true);
                return;
            }
            StatueBlockEntity from = zoneAt(level, moved);
            StatueBlockEntity to = zoneAt(level, moved.relative(push));
            if ((from != null && from != pistonZone) || (to != null && to != pistonZone)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Los mobs hostiles no pueden aparecer dentro de una zona (los jefes sí, pero son repelidos). */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        Entity entity = event.getEntity();
        if (entity instanceof Enemy && !entity.getType().is(Tags.EntityTypes.BOSSES)
                && StatueRegistry.findCovering(event.getLevel(), entity.position()) != null) {
            event.setCanceled(true);
        }
    }
}
