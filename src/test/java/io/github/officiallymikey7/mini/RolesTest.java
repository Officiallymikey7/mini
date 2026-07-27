package io.github.officiallymikey7.mini;

import io.github.officiallymikey7.mini.core.NeedType;
import io.github.officiallymikey7.mini.roles.RoleDefinition;
import io.github.officiallymikey7.mini.roles.RoleRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RoleRegistry} and {@link RoleDefinition}.
 *
 * <p>Mirrors the TypeScript {@code roles.test.ts} suite.
 */
class RolesTest {

    // ── Built-in roles are present ────────────────────────────────────────────

    @Test
    void farmerRoleExists() {
        Optional<RoleDefinition> role = RoleRegistry.getRole("farmer");
        assertTrue(role.isPresent());
        assertEquals("farmer", role.get().getId());
    }

    @Test
    void allSixBuiltinRolesPresent() {
        List<String> expected = List.of("farmer", "trader", "guard", "priest", "adventurer", "blacksmith");
        for (String id : expected) {
            assertTrue(RoleRegistry.getRole(id).isPresent(), "Missing built-in role: " + id);
        }
    }

    @Test
    void listRolesReturnsAllSix() {
        assertTrue(RoleRegistry.listRoles().size() >= 6);
    }

    // ── Role fields ───────────────────────────────────────────────────────────

    @Test
    void farmerHasFoodWeight14() {
        RoleDefinition farmer = RoleRegistry.getRole("farmer").orElseThrow();
        assertEquals(1.4, farmer.getNeedWeights().getOrDefault(NeedType.FOOD, 1.0), 0.001);
    }

    @Test
    void guardHasDefenseWeight15() {
        RoleDefinition guard = RoleRegistry.getRole("guard").orElseThrow();
        assertEquals(1.5, guard.getNeedWeights().getOrDefault(NeedType.SURVIVAL_DEFENSE, 1.0), 0.001);
    }

    @Test
    void blacksmithHasToolsWeight16() {
        RoleDefinition bs = RoleRegistry.getRole("blacksmith").orElseThrow();
        assertEquals(1.6, bs.getNeedWeights().getOrDefault(NeedType.TOOLS, 1.0), 0.001);
    }

    @Test
    void farmerHasTraits() {
        RoleDefinition farmer = RoleRegistry.getRole("farmer").orElseThrow();
        assertFalse(farmer.getTraits().isEmpty());
    }

    @Test
    void farmerHasCommunityGoal() {
        RoleDefinition farmer = RoleRegistry.getRole("farmer").orElseThrow();
        assertFalse(farmer.getCommunityGoal().isBlank());
    }

    // ── Custom role registration ───────────────────────────────────────────────

    @Test
    void registerCustomRole() {
        RoleDefinition healer = RoleDefinition.builder("healer_test")
                .label("Healer")
                .communityGoal("Keep all community members healthy and fed.")
                .needWeight(NeedType.FOOD, 1.4)
                .needWeight(NeedType.SHELTER, 1.2)
                .traits("compassionate", "knowledgeable", "calm")
                .build();
        RoleRegistry.register(healer);
        assertTrue(RoleRegistry.getRole("healer_test").isPresent());
        assertEquals("Healer", RoleRegistry.getRole("healer_test").get().getLabel());
    }

    @Test
    void unknownRoleReturnsEmpty() {
        assertTrue(RoleRegistry.getRole("no_such_role").isEmpty());
    }

    // ── Builder fluent API ────────────────────────────────────────────────────

    @Test
    void builderCreatesRoleCorrectly() {
        RoleDefinition r = RoleDefinition.builder("miner")
                .label("Miner")
                .communityGoal("Mine deep resources.")
                .needWeight(NeedType.TOOLS, 1.5)
                .traits("hardy", "determined")
                .build();
        assertEquals("miner", r.getId());
        assertEquals("Miner", r.getLabel());
        assertEquals(1.5, r.getNeedWeights().getOrDefault(NeedType.TOOLS, 1.0), 0.001);
        assertEquals(2, r.getTraits().size());
    }
}
