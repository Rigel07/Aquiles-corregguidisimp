package com.achilles.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Puntos de estatua por jugador (5 por cada estatua encontrada). Se guarda en el mundo (overworld). */
public class PointsData extends SavedData {
    private static final String NAME = "achilles_points";

    /** UUID -> { ganados, gastados } */
    private final Map<UUID, int[]> points = new HashMap<>();
    /** UUID -> estatuas ya encontradas ("dimensión|posición"). */
    private final Map<UUID, Set<String>> found = new HashMap<>();

    public static PointsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PointsData::load, PointsData::new, NAME);
    }

    public static PointsData load(CompoundTag tag) {
        PointsData data = new PointsData();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID id = UUID.fromString(entry.getString("Id"));
            data.points.put(id, new int[]{entry.getInt("Earned"), entry.getInt("Spent")});
            ListTag foundList = entry.getList("Found", Tag.TAG_STRING);
            Set<String> set = new HashSet<>();
            for (int j = 0; j < foundList.size(); j++) set.add(foundList.getString(j));
            data.found.put(id, set);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, int[]> e : points.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", e.getKey().toString());
            entry.putInt("Earned", e.getValue()[0]);
            entry.putInt("Spent", e.getValue()[1]);
            ListTag foundList = new ListTag();
            for (String key : found.getOrDefault(e.getKey(), Set.of())) foundList.add(StringTag.valueOf(key));
            entry.put("Found", foundList);
            list.add(entry);
        }
        tag.put("Players", list);
        return tag;
    }

    private int[] entry(UUID id) {
        return points.computeIfAbsent(id, k -> new int[]{0, 0});
    }

    public void addPoint(UUID id) {
        entry(id)[0]++;
        setDirty();
    }

    public void addPoints(UUID id, int amount) {
        entry(id)[0] += amount;
        setDirty();
    }

    /** Marca una estatua como encontrada y da los puntos si es la primera vez. Devuelve true si se dieron. */
    public boolean discover(UUID id, String statueKey, int reward) {
        entry(id);
        if (!found.computeIfAbsent(id, k -> new HashSet<>()).add(statueKey)) return false;
        addPoints(id, reward);
        return true;
    }

    public int available(UUID id) {
        int[] e = entry(id);
        return Math.max(0, e[0] - e[1]);
    }

    public void spend(UUID id, int amount) {
        entry(id)[1] += amount;
        setDirty();
    }
}
