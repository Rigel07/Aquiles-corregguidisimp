package com.achilles.data;

/**
 * Números de equilibrio de las estatuas. Con las estatuas generadas separadas al menos ~150 bloques,
 * el radio máximo (58) nunca hace que dos zonas se solapen.
 */
public final class StatueStats {
    public static final int MAX_LEVEL = 100;
    public static final double BASE_RADIUS = 8.0D;
    public static final double RADIUS_PER_LEVEL = 0.5D;
    public static final float BASE_HEAL_PER_SECOND = 0.5F;
    public static final float HEAL_PER_LEVEL = 0.1F;
    /** Puntos que recibe un jugador la primera vez que encuentra una estatua. */
    public static final int DISCOVERY_POINTS = 5;
    /** Mejora del mini Aquiles guardián: vida y daño extra por nivel (hasta el nivel 100). */
    public static final double GUARD_HEALTH_PER_LEVEL = 0.5D;
    public static final double GUARD_DAMAGE_PER_LEVEL = 0.1D;
    public static final double GUARD_BASE_HEALTH = 12.0D;
    public static final double GUARD_BASE_DAMAGE = 1.0D;

    private StatueStats() {
    }

    public static double radius(int radiusLevel) {
        return BASE_RADIUS + RADIUS_PER_LEVEL * radiusLevel;
    }

    public static double guardHealth(int guardLevel) {
        return GUARD_BASE_HEALTH + GUARD_HEALTH_PER_LEVEL * guardLevel;
    }

    public static double guardDamage(int guardLevel) {
        return GUARD_BASE_DAMAGE + GUARD_DAMAGE_PER_LEVEL * guardLevel;
    }

    public static float healPerSecond(int healLevel) {
        return BASE_HEAL_PER_SECOND + HEAL_PER_LEVEL * healLevel;
    }
}
