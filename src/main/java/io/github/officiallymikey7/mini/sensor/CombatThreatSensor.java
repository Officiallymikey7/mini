package io.github.officiallymikey7.mini.sensor;

import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;
import io.github.officiallymikey7.mini.core.WorldState;

import java.util.Comparator;
import java.util.List;

/**
 * Calculates a composite combat-threat score and weapon assessment.
 *
 * <p>The sensor approximates:
 * <ul>
 *   <li><b>Threat score</b> – 0–100 composite, weighted by hostile count, distance,
 *       and a mob-type danger factor.</li>
 *   <li><b>Line-of-sight heuristic</b> – assumed true when the closest hostile is
 *       within {@value #LOS_RANGE} blocks (platform-agnostic approximation; the
 *       Fabric layer can override with actual ray-casting).</li>
 *   <li><b>Best weapon</b> – the highest-tier weapon found in the inventory.</li>
 *   <li><b>Weapon advantage</b> – −1.0 (severe disadvantage) to +1.0 (strong
 *       advantage), based on weapon tier versus closest mob type.</li>
 * </ul>
 *
 * <p>A 100–250 ms reaction delay is applied by {@link io.github.officiallymikey7.mini.brain.HumanInputController}
 * before any combat action derived from this sensor is executed.
 */
public final class CombatThreatSensor {

    /** Distance (blocks) within which line-of-sight is assumed. */
    private static final double LOS_RANGE = 12.0;

    /** Mob types considered high-danger (boss-tier or ranged). */
    private static final List<String> HIGH_DANGER_MOBS = List.of(
            "ender_dragon", "wither", "warden", "elder_guardian",
            "skeleton", "stray", "witch", "blaze", "ghast", "phantom");

    /** Mob types considered medium-danger. */
    private static final List<String> MEDIUM_DANGER_MOBS = List.of(
            "zombie", "zombie_villager", "husk", "drowned", "spider",
            "cave_spider", "creeper", "enderman", "pillager", "vindicator",
            "ravager", "piglin", "piglin_brute", "hoglin", "zoglin");

    private CombatThreatSensor() {}

    /**
     * Analyses the hostile list and inventory and produces a {@link ThreatResult}.
     *
     * @param state current world snapshot
     * @return populated threat analysis
     */
    public static ThreatResult analyse(WorldState state) {
        List<HostileEntity> hostiles = state.nearbyHostiles;

        if (hostiles.isEmpty()) {
            return new ThreatResult(0.0, false, bestWeapon(state), 0.5);
        }

        HostileEntity closest = hostiles.stream()
                .min(Comparator.comparingDouble(h -> h.distance))
                .orElseThrow();

        double dangerFactor = dangerFactor(closest.type);
        double distanceFactor = distanceFactor(closest.distance);
        double countBonus = Math.min(20.0, (hostiles.size() - 1) * 5.0);

        double threat = Math.min(100.0, distanceFactor * dangerFactor * 80.0 + countBonus);
        boolean los   = closest.distance <= LOS_RANGE;

        String weapon   = bestWeapon(state);
        double advantage = weaponAdvantage(weapon, closest.type);

        return new ThreatResult(threat, los, weapon, advantage);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static double dangerFactor(String mobType) {
        String lower = mobType.toLowerCase();
        if (HIGH_DANGER_MOBS.stream().anyMatch(lower::contains))   return 1.3;
        if (MEDIUM_DANGER_MOBS.stream().anyMatch(lower::contains)) return 1.0;
        return 0.7; // passive-turned-hostile or unknown
    }

    private static double distanceFactor(double distance) {
        if (distance < 3)  return 1.0;
        if (distance < 8)  return 0.85;
        if (distance < 16) return 0.6;
        return 0.35;
    }

    private static String bestWeapon(WorldState state) {
        String best = "fist";
        int bestTier = -1;
        for (InventoryItem item : state.inventory) {
            String n = item.name.toLowerCase();
            int tier = weaponTier(n);
            if (tier > bestTier) { bestTier = tier; best = item.name; }
        }
        // Also consider main-hand item
        int mainTier = weaponTier(state.mainHandItem.toLowerCase());
        if (mainTier > bestTier) best = state.mainHandItem;
        return best;
    }

    private static int weaponTier(String name) {
        if (name.contains("netherite_sword")) return 5;
        if (name.contains("diamond_sword"))   return 4;
        if (name.contains("iron_sword"))       return 3;
        if (name.contains("stone_sword"))      return 2;
        if (name.contains("wooden_sword") || name.contains("golden_sword")) return 1;
        return -1;
    }

    /**
     * Returns a weapon-advantage value in [−1, +1].
     * A netherite sword against a basic zombie yields +1.
     * A fist against a warden yields −1.
     */
    private static double weaponAdvantage(String weapon, String mobType) {
        int wTier = weaponTier(weapon.toLowerCase());
        boolean highDanger = HIGH_DANGER_MOBS.stream()
                .anyMatch(mobType.toLowerCase()::contains);

        if (wTier < 0) return highDanger ? -1.0 : -0.5; // no weapon
        double base = (wTier - 2) / 3.0; // −0.67..+1.0 for tiers 0..5
        return highDanger ? Math.max(-1.0, base - 0.3) : Math.min(1.0, base + 0.2);
    }

    /** Result container for threat analysis. */
    public record ThreatResult(
            double threatScore,
            boolean hasLineOfSight,
            String bestWeapon,
            double weaponAdvantage) {}
}
