package com.achilles.block;

import com.achilles.data.PointsData;
import com.achilles.data.StatueRegistry;
import com.achilles.data.StatueStats;
import com.achilles.entity.AchillesEntity;
import com.achilles.registry.ModEntities;
import com.achilles.network.NetworkHandler;
import com.achilles.network.OpenStatuePacket;
import com.achilles.registry.ModBlockEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cerebro de la estatua: propietario, niveles, jugadores con permiso, y la zona:
 * repele/elimina mobs hostiles, expulsa a jugadores sin permiso y cura a los que están dentro.
 */
public class StatueBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";
    private int radiusLevel;
    private int healLevel;
    private int guardLevel;
    private final Map<UUID, String> trusted = new LinkedHashMap<>();
    private int ticks;

    public StatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATUE.get(), pos, state);
    }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            StatueRegistry.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        StatueRegistry.remove(this);
    }

    // ------------------------------------------------------------ datos

    public boolean hasOwner() {
        return owner != null;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public double getRadius() {
        return StatueStats.radius(radiusLevel);
    }

    private Vec3 center() {
        return Vec3.atCenterOf(this.worldPosition).add(0.0D, 0.5D, 0.0D);
    }

    /** ¿Este jugador (por UUID) tiene permiso en la zona? Sirve para sus mascotas aunque estén solas. */
    public boolean isAllowed(UUID id) {
        return owner == null || owner.equals(id) || trusted.containsKey(id);
    }

    public boolean contains(Vec3 pos) {
        double r = getRadius();
        return center().distanceToSqr(pos) <= r * r;
    }

    /** Sin propietario: todos. Con propietario: él, los de la lista y administradores en creativo. */
    public boolean isAllowed(Player player) {
        if (owner == null) return true;
        if (owner.equals(player.getUUID()) || trusted.containsKey(player.getUUID())) return true;
        return player.isCreative() && player.hasPermissions(2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("RadiusLevel", radiusLevel);
        tag.putInt("HealLevel", healLevel);
        tag.putInt("GuardLevel", guardLevel);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : trusted.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            entry.putString("Name", e.getValue());
            list.add(entry);
        }
        tag.put("Trusted", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        radiusLevel = Mth.clamp(tag.getInt("RadiusLevel"), 0, StatueStats.MAX_LEVEL);
        healLevel = Mth.clamp(tag.getInt("HealLevel"), 0, StatueStats.MAX_LEVEL);
        guardLevel = Mth.clamp(tag.getInt("GuardLevel"), 0, StatueStats.MAX_LEVEL);
        trusted.clear();
        ListTag list = tag.getList("Trusted", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            trusted.put(entry.getUUID("Id"), entry.getString("Name"));
        }
    }

    // ------------------------------------------------------------ interacción

    public void interact(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (owner == null) {
            owner = player.getUUID();
            ownerName = player.getGameProfile().getName();
            discover(player);
            setChanged();
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.5D,
                    worldPosition.getZ() + 0.5D, 40, 0.6D, 1.0D, 0.6D, 0.3D);
            level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.achilles.claimed"), false);
            sendOpen(player);
        } else if (isOwner(player)) {
            sendOpen(player);
        } else if (trusted.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.achilles.trusted", ownerName), true);
        } else {
            player.displayClientMessage(Component.translatable("message.achilles.owned_by", ownerName), true);
        }
    }

    public void sendOpen(ServerPlayer player) {
        if (owner == null) return;
        int points = PointsData.get(player.server).available(owner);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenStatuePacket(worldPosition, ownerName, radiusLevel, healLevel, guardLevel, points,
                        new ArrayList<>(trusted.values())));
    }

    /** type 0 = radio, 1 = curación, 2 = mini Aquiles (vida y daño de los guardianes). */
    public void upgrade(ServerPlayer player, int type, int amount) {
        if (!isOwner(player)) return;
        PointsData data = PointsData.get(player.server);
        int current = type == 0 ? radiusLevel : type == 1 ? healLevel : guardLevel;
        int spend = Math.min(Math.min(amount, StatueStats.MAX_LEVEL - current), data.available(owner));
        if (spend <= 0) return;
        data.spend(owner, spend);
        if (type == 0) radiusLevel += spend;
        else if (type == 1) healLevel += spend;
        else guardLevel += spend;
        setChanged();
        if (type == 2 && player.level() instanceof ServerLevel serverLevel) {
            maintainGuardians(serverLevel);   // aplica ya la mejora a los guardianes existentes
        }
        player.level().playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    public void setTrusted(ServerPlayer player, String name, boolean add) {
        if (!isOwner(player)) return;
        if (add) {
            if (trusted.size() >= 20) return;
            Optional<GameProfile> profile = lookup(player.server, name);
            if (profile.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.achilles.player_not_found", name), true);
                return;
            }
            if (!profile.get().getId().equals(owner)) {
                trusted.put(profile.get().getId(), profile.get().getName());
            }
        } else {
            trusted.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(name));
        }
        setChanged();
    }

    private static Optional<GameProfile> lookup(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getGameProfile());
        return server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(name);
    }

    // ------------------------------------------------------------ la zona

    public static void serverTick(Level level, BlockPos pos, BlockState state, StatueBlockEntity statue) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        statue.ticks++;
        if (statue.ticks % 20 == 0) statue.discoverNearby(serverLevel);
        statue.reflectProjectiles(serverLevel);
        if (statue.ticks % 4 == 0) statue.guard(serverLevel);
        if (statue.ticks % 20 == 0) statue.healPlayers(serverLevel);
        if (statue.ticks % 100 == 0) statue.maintainGuardians(serverLevel);
        if (statue.ticks % 40 == 0) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 2.2D, pos.getZ() + 0.5D,
                    2, 0.3D, 0.2D, 0.3D, 0.01D);
        }
    }

    /** Mantiene una guardia pequeña y persistente. Las mejoras de radio desbloquean más guardianes
     * y las mejoras de curación aumentan su resistencia/daño. */
    private void maintainGuardians(ServerLevel level) {
        int desired = Math.min(4, 1 + radiusLevel / 25);
        int tier = Math.min(3, healLevel / 25);
        AABB area = new AABB(worldPosition).inflate(14.0D);
        int current = level.getEntitiesOfClass(AchillesEntity.class, area, guard ->
                worldPosition.equals(guard.getGuardPost())).size();

        // También actualiza la fuerza de los guardianes existentes cuando sube Curación.
        for (AchillesEntity guard : level.getEntitiesOfClass(AchillesEntity.class, area, g ->
                worldPosition.equals(g.getGuardPost()))) {
            guard.setGuardStats(tier, guardLevel);
        }
        if (current >= desired) {
            return;
        }

        for (int i = current; i < desired; i++) {
            int x = worldPosition.getX() + level.random.nextInt(9) - 4;
            int z = worldPosition.getZ() + level.random.nextInt(9) - 4;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() || y > level.getMaxBuildHeight() - 2) continue;
            if (!level.getBlockState(new BlockPos(x, y - 1, z)).isSolid()) continue;

            AchillesEntity guard = ModEntities.ACHILLES.get().create(level);
            if (guard == null) continue;
            guard.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.STRUCTURE, null, null);
            guard.setGuardPost(worldPosition);
            guard.setGuardStats(tier, guardLevel);
            guard.setPersistenceRequired();
            level.addFreshEntity(guard);
        }

        if (desired > 1) {
            level.sendParticles(ParticleTypes.ENCHANT, worldPosition.getX() + 0.5D, worldPosition.getY() + 2.0D,
                    worldPosition.getZ() + 0.5D, 8, 0.7D, 0.6D, 0.7D, 0.05D);
        }
    }

    // ------------------------------------------------------------ descubrimiento

    private static final double DISCOVER_RANGE = 24.0D;
    private static final String SAFE_ITEM_TAG = "achilles_safe_item";

    /** Quien se acerca a una estatua por primera vez la "encuentra" y gana puntos. */
    private void discoverNearby(ServerLevel level) {
        double range = Math.max(getRadius(), DISCOVER_RANGE);
        Vec3 c = center();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(range))) {
            if (player.isSpectator() || c.distanceToSqr(player.position()) > range * range) continue;
            discover(player);
        }
    }

    /** Da los puntos de descubrimiento si este jugador no había encontrado antes esta estatua. */
    public boolean discover(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String key = level.dimension().location() + "|" + worldPosition.asLong();
        if (!PointsData.get(level.getServer()).discover(player.getUUID(), key, StatueStats.DISCOVERY_POINTS)) {
            return false;
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0D), player.getZ(),
                25, 0.5D, 0.8D, 0.5D, 0.2D);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.achilles.discovered", StatueStats.DISCOVERY_POINTS), false);
        return true;
    }

    // ------------------------------------------------------------ escudo reflectante

    /** ¿Debe este proyectil atravesar la zona sin ser reflejado? (los disparados por jugadores con permiso) */
    private boolean passesThrough(Projectile projectile) {
        Entity owner = projectile.getOwner();
        return owner instanceof Player player && isAllowed(player);
    }

    /**
     * Cada tick: todo proyectil que fuese a cruzar la superficie de la zona hacia dentro rebota
     * y sale disparado de vuelta hacia quien lo lanzó (o espejado si no se sabe quién fue).
     */
    private void reflectProjectiles(ServerLevel level) {
        double r = getRadius();
        Vec3 c = center();
        AABB box = new AABB(c, c).inflate(r + 12.0D);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box)) {
            if (projectile.isRemoved() || projectile instanceof FireworkRocketEntity) continue;
            Vec3 vel = projectile.getDeltaMovement();
            double speedSqr = vel.lengthSqr();
            if (speedSqr < 1.0E-4D) continue;                       // clavado o parado
            Vec3 rel = projectile.position().subtract(c);
            double cc = rel.lengthSqr() - r * r;
            if (cc < 0.0D) continue;                                // ya está dentro: no es una entrada
            double b = 2.0D * rel.dot(vel);
            double disc = b * b - 4.0D * speedSqr * cc;
            if (disc <= 0.0D) continue;                             // no toca la esfera
            double t = (-b - Math.sqrt(disc)) / (2.0D * speedSqr);
            if (t < 0.0D || t > 1.0D) continue;                     // no la cruza en este tick
            if (passesThrough(projectile)) continue;
            reflect(level, projectile, c, vel, t);
        }
    }

    private void reflect(ServerLevel level, Projectile projectile, Vec3 c, Vec3 vel, double t) {
        Vec3 entry = projectile.position().add(vel.scale(t));
        Vec3 normal = entry.subtract(c);
        normal = normal.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : normal.normalize();
        Vec3 outside = entry.add(normal.scale(0.4D));
        double speed = Math.max(vel.length(), 0.6D);

        Vec3 newDir;
        Entity owner = projectile.getOwner();
        if (owner != null && owner.isAlive() && owner.level() == level) {
            Vec3 toOwner = owner.getEyePosition().subtract(outside);
            newDir = toOwner.lengthSqr() > 1.0E-4D ? toOwner.normalize() : normal;
        } else {
            // espejo sobre la superficie de la zona: v' = v - 2 (v·n) n
            Vec3 mirrored = vel.subtract(normal.scale(2.0D * vel.dot(normal)));
            newDir = mirrored.lengthSqr() > 1.0E-6D ? mirrored.normalize() : normal;
        }
        Vec3 newVel = newDir.scale(speed);

        projectile.setPos(outside.x, outside.y, outside.z);
        projectile.setDeltaMovement(newVel);
        projectile.setYRot((float) (Mth.atan2(newVel.x, newVel.z) * (180.0D / Math.PI)));
        projectile.setXRot((float) (Mth.atan2(newVel.y, newVel.horizontalDistance()) * (180.0D / Math.PI)));
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;

        // Bolas de fuego y calaveras aceleran por su cuenta: se les da la nueva dirección también.
        if (projectile instanceof AbstractHurtingProjectile) {
            try {
                double px = (Double) ObfuscationReflectionHelper.getPrivateValue(AbstractHurtingProjectile.class, (AbstractHurtingProjectile) projectile, "xPower");
                double py = (Double) ObfuscationReflectionHelper.getPrivateValue(AbstractHurtingProjectile.class, (AbstractHurtingProjectile) projectile, "yPower");
                double pz = (Double) ObfuscationReflectionHelper.getPrivateValue(AbstractHurtingProjectile.class, (AbstractHurtingProjectile) projectile, "zPower");
                double mag = Math.sqrt(px * px + py * py + pz * pz);
                if (mag > 0.0D) {
                    ObfuscationReflectionHelper.setPrivateValue(AbstractHurtingProjectile.class, (AbstractHurtingProjectile) projectile, newDir.x * mag, "xPower");
                    ObfuscationReflectionHelper.setPrivateValue(AbstractHurtingProjectile.class, (AbstractHurtingProjectile) projectile, newDir.y * mag, "yPower");
                    ObfuscationReflectionHelper.setPrivateValue(AbstractHurtingProjectile.class, (AbstractHurtingProjectile) projectile, newDir.z * mag, "zPower");
                }
            } catch (RuntimeException ignored) {
                // si el campo no se pudo leer, el proyectil igualmente sale rebotado con su nueva velocidad
            }
        }

        level.sendParticles(ParticleTypes.ENCHANTED_HIT, outside.x, outside.y, outside.z, 12, 0.25D, 0.25D, 0.25D, 0.1D);
        level.sendParticles(ParticleTypes.END_ROD, outside.x, outside.y, outside.z, 4, 0.1D, 0.1D, 0.1D, 0.05D);
        level.playSound(null, outside.x, outside.y, outside.z, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    private void guard(ServerLevel level) {
        double r = getRadius();
        Vec3 c = center();
        AABB box = new AABB(c, c).inflate(r + 6.0D);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy)) {
            LivingEntity target = mob.getTarget();
            if (target != null && c.distanceToSqr(target.position()) < r * r) {
                mob.setTarget(null);
            }
            double d = Math.sqrt(mob.distanceToSqr(c));
            if (d >= r) continue;

            boolean boss = mob.getType().is(Tags.EntityTypes.BOSSES);
            if (!boss && !mob.hasCustomName() && d < r - 3.0D) {
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY(0.5D), mob.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
                mob.discard();
                continue;
            }
            Vec3 away = new Vec3(mob.getX() - c.x, 0.0D, mob.getZ() - c.z);
            if (away.lengthSqr() < 1.0E-4D) away = new Vec3(1.0D, 0.0D, 0.0D);
            away = away.normalize();
            mob.setDeltaMovement(away.x * 0.8D, 0.35D, away.z * 0.8D);
            mob.hurtMarked = true;
            mob.getNavigation().stop();
        }

        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (c.distanceToSqr(player.position()) < r * r && !isAllowed(player)) {
                eject(level, player, c, r);
            }
        }

        // Los objetos tirados dentro de la zona no pueden quemarse, explotar ni caer en lava.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            boolean inside = c.distanceToSqr(item.position()) <= r * r;
            if (inside && !item.isInvulnerable()) {
                item.setInvulnerable(true);
                item.addTag(SAFE_ITEM_TAG);
            } else if (!inside && item.getTags().contains(SAFE_ITEM_TAG)) {
                item.setInvulnerable(false);
                item.removeTag(SAFE_ITEM_TAG);
            }
        }
    }

    private void eject(ServerLevel level, ServerPlayer player, Vec3 c, double r) {
        Vec3 dir = new Vec3(player.getX() - c.x, 0.0D, player.getZ() - c.z);
        if (dir.lengthSqr() < 1.0E-4D) dir = new Vec3(1.0D, 0.0D, 0.0D);
        dir = dir.normalize();
        double tx = c.x + dir.x * (r + 2.0D);
        double tz = c.z + dir.z * (r + 2.0D);
        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
        player.connection.teleport(tx, ty, tz, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("message.achilles.restricted", ownerName), true);
    }

    private void healPlayers(ServerLevel level) {
        double r = getRadius();
        Vec3 c = center();
        float amount = StatueStats.healPerSecond(healLevel);

        // Las mascotas de los jugadores con permiso también se curan dentro de la zona.
        for (TamableAnimal pet : level.getEntitiesOfClass(TamableAnimal.class, new AABB(c, c).inflate(r))) {
            if (pet.getOwnerUUID() != null && isAllowed(pet.getOwnerUUID())
                    && c.distanceToSqr(pet.position()) <= r * r && pet.getHealth() < pet.getMaxHealth()) {
                pet.heal(amount);
            }
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(r))) {
            if (player.isSpectator() || c.distanceToSqr(player.position()) > r * r || !isAllowed(player)) continue;

            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(amount);
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY(1.0D), player.getZ(), 1, 0.3D, 0.3D, 0.3D, 0.0D);
            }
            // Bendición de Aquiles: las mejoras altas convierten la zona en un santuario real.
            // Se refrescan cada segundo para no crear una cola enorme de efectos.
            if (healLevel >= 25) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 30, 0, true, false, true));
            }
            if (healLevel >= 75) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 30, 0, true, false, true));
            }

            // Mejoras de curación altas: limpia efectos negativos (50+) y sacia el hambre (100)
            if (healLevel >= 50 && ticks % 100 == 0) {
                List<net.minecraft.world.effect.MobEffect> harmful = new ArrayList<>();
                for (MobEffectInstance effect : player.getActiveEffects()) {
                    if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) harmful.add(effect.getEffect());
                }
                harmful.forEach(player::removeEffect);
            }
            if (healLevel >= StatueStats.MAX_LEVEL && player.getFoodData().needsFood()) {
                player.getFoodData().eat(1, 0.5F);
            }
        }
    }
}
