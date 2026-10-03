package com.achilles.world;

import com.achilles.Achilles;
import com.achilles.entity.AchillesStatueEntity;
import com.achilles.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** Generación determinista: una celda de 20x20 chunks puede contener una estatua, evitando que aparezcan juntas. */
@Mod.EventBusSubscriber(modid = Achilles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AchillesWorldGen {
    public static final int CELL_CHUNKS = 20; // 320 bloques entre centros como máximo aproximado
    private static final int MIN_Y = -60;

    private AchillesWorldGen() {}
    public static void register() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        if (cx == 0 && cz == 0) return;

        int cellX = Math.floorDiv(cx, CELL_CHUNKS);
        int cellZ = Math.floorDiv(cz, CELL_CHUNKS);
        if (Math.floorMod(cx, CELL_CHUNKS) != CELL_CHUNKS / 2 || Math.floorMod(cz, CELL_CHUNKS) != CELL_CHUNKS / 2) return;

        long seed = level.getSeed() ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L) ^ 0xA0C11E5L;
        Random random = new Random(seed);
        // No todas las celdas tienen una estatua: hace que el mundo se sienta descubierto y evita saturación.
        if (random.nextInt(100) >= 72) return;

        int x = chunk.getPos().getMinBlockX() + 3 + random.nextInt(10);
        int z = chunk.getPos().getMinBlockZ() + 3 + random.nextInt(10);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (y <= MIN_Y || y >= level.getMaxBuildHeight() - 4) return;

        BlockPos ground = new BlockPos(x, y - 1, z);
        var biome = level.getBiome(ground).unwrapKey().map(k -> k.location().toString()).orElse("");
        if (biome.contains("ocean") || biome.contains("river") || biome.contains("swamp") || biome.contains("nether") || biome.contains("end")) return;
        if (!level.getBlockState(ground).isSolid()) return;

        /*
         * IMPORTANTE:
         * No buscamos entidades en un AABB alrededor de la estatua durante ChunkEvent.Load.
         * Este evento puede ejecutarse mientras Minecraft está generando un chunk
         * (WorldGenRegion). Consultar entidades de chunks vecinos desde aquí puede pedir
         * una región fuera de los límites de generación y provocar:
         * "We are asking a region for a chunk out of bound".
         *
         * La posición ya es determinista: solo la celda que contiene exactamente este
         * chunk puede generar la estatua, por lo que no necesitamos esa consulta.
         */
        AchillesStatueEntity statue = ModEntities.STATUE.get().create(level);
        if (statue == null) return;
        statue.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360F, 0);
        level.addFreshEntity(statue);
    }
}
