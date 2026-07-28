package io.github.officiallymikey7.mini.roles;

import io.github.officiallymikey7.mini.core.NeedType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extensible registry of agent roles.
 *
 * <p>Six built-in roles are registered at class load time. Call
 * {@link #register(RoleDefinition)} to add custom roles at runtime.
 */
public final class RoleRegistry {

    private static final Map<String, RoleDefinition> REGISTRY = new LinkedHashMap<>();

    static {
        register(RoleDefinition.builder("farmer")
                .label("Farmer")
                .communityGoal("Maintain and expand the community farm to ensure a stable food supply for all agents.")
                .needWeight(NeedType.FOOD, 1.4)
                .needWeight(NeedType.RESOURCES, 1.2)
                .traits("diligent", "patient", "earth-connected", "community-minded")
                .build());

        register(RoleDefinition.builder("trader")
                .label("Trader / Merchant")
                .communityGoal("Facilitate fair resource exchange between agents using emeralds, improving collective efficiency.")
                .needWeight(NeedType.RESOURCES, 1.5)
                .needWeight(NeedType.TOOLS, 1.2)
                .traits("entrepreneurial", "negotiator", "opportunistic", "fair-minded")
                .build());

        register(RoleDefinition.builder("guard")
                .label("Guard / Defender")
                .communityGoal("Protect the settlement and its residents from hostile mobs and external threats.")
                .needWeight(NeedType.SURVIVAL_DEFENSE, 1.5)
                .needWeight(NeedType.TOOLS, 1.3)
                .traits("courageous", "vigilant", "protective", "disciplined")
                .build());

        register(RoleDefinition.builder("priest")
                .label("Cultural Leader / Priest")
                .communityGoal("Preserve community values, share doctrine, mediate disputes, and foster cultural cohesion.")
                .needWeight(NeedType.SHELTER, 1.2)
                .traits("wise", "eloquent", "spiritual", "empathetic", "influential")
                .build());

        register(RoleDefinition.builder("adventurer")
                .label("Adventurer / Explorer")
                .communityGoal("Explore new territories, map resources, and bring back rare materials for the community.")
                .needWeight(NeedType.RESOURCES, 1.4)
                .needWeight(NeedType.TOOLS, 1.3)
                .traits("bold", "curious", "self-reliant", "resilient")
                .build());

        register(RoleDefinition.builder("blacksmith")
                .label("Blacksmith")
                .communityGoal("Craft and maintain tools, weapons, and armour to keep all agents well-equipped.")
                .needWeight(NeedType.TOOLS, 1.6)
                .needWeight(NeedType.RESOURCES, 1.3)
                .traits("skilled", "methodical", "practical", "reliable")
                .build());
    }

    private RoleRegistry() {}

    /** Register (or replace) a role definition. */
    public static void register(RoleDefinition role) {
        REGISTRY.put(role.getId(), role);
    }

    /** Retrieve a role by its ID. */
    public static Optional<RoleDefinition> getRole(String id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    /** Returns all registered roles in insertion order. */
    public static List<RoleDefinition> listRoles() {
        return new ArrayList<>(REGISTRY.values());
    }
}
