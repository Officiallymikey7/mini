package io.github.officiallymikey7.mini.integration;

import io.github.officiallymikey7.mini.core.HostileEntity;
import io.github.officiallymikey7.mini.core.InventoryItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully simulated Minecraft adapter that runs entirely in-process.
 *
 * <p>Use this for unit tests, demonstrations, and local agent development
 * without a running Minecraft server. World state can be mutated via the
 * various setter methods to simulate world changes during a test.
 */
public final class MockBotAdapter implements BotAdapter {

    private String agentName;
    private int gameTick;
    private float health;
    private float hunger;
    private List<HostileEntity> nearbyHostiles;
    private List<InventoryItem> inventory;
    private int shelterDistance;
    private boolean hasShelter;
    private List<String> nearbyChat;

    /** Chronological log of all actions performed. */
    public final List<ActionRecord> actionLog = new ArrayList<>();
    /** Log of all chat messages sent by the agent. */
    public final List<String> chatLog = new ArrayList<>();
    /**
     * Per-action result overrides. When an action key is present here its
     * value is returned instead of the built-in simulation result.
     */
    public final Map<String, String> actionResults = new HashMap<>();

    // ── Constructors ─────────────────────────────────────────────────────────

    public MockBotAdapter() {
        this(new Config());
    }

    public MockBotAdapter(Config config) {
        this.agentName       = config.agentName;
        this.gameTick        = config.gameTick;
        this.health          = config.health;
        this.hunger          = config.hunger;
        this.nearbyHostiles  = new ArrayList<>(config.nearbyHostiles);
        this.inventory       = new ArrayList<>(config.inventory);
        this.shelterDistance = config.shelterDistance;
        this.hasShelter      = config.hasShelter;
        this.nearbyChat      = new ArrayList<>(config.nearbyChat);
    }

    // ── BotAdapter ───────────────────────────────────────────────────────────

    @Override
    public RawWorldState getWorldState() {
        return new RawWorldState(agentName, gameTick, health, hunger,
                List.copyOf(nearbyHostiles), List.copyOf(inventory),
                shelterDistance, hasShelter, List.copyOf(nearbyChat));
    }

    @Override
    public String performAction(String action) {
        String result = actionResults.getOrDefault(action, simulateAction(action));
        actionLog.add(new ActionRecord(action, result));
        if (!result.startsWith("Error:")) {
            applyActionSideEffects(action);
        }
        return result;
    }

    @Override
    public void sendChat(String message) {
        chatLog.add(message);
    }

    // ── State mutators (for testing) ─────────────────────────────────────────

    public void setGameTick(int gameTick)                          { this.gameTick = gameTick; }
    public void setHealth(float health)                            { this.health = health; }
    public void setHunger(float hunger)                            { this.hunger = hunger; }
    public void setHasShelter(boolean hasShelter)                  { this.hasShelter = hasShelter; }
    public void setShelterDistance(int distance)                   { this.shelterDistance = distance; }
    public void setNearbyHostiles(List<HostileEntity> hostiles)    { this.nearbyHostiles = new ArrayList<>(hostiles); }
    public void setInventory(List<InventoryItem> inventory)        { this.inventory = new ArrayList<>(inventory); }
    public void setNearbyChat(List<String> chat)                   { this.nearbyChat = new ArrayList<>(chat); }

    // ── Simulation internals ─────────────────────────────────────────────────

    private String simulateAction(String action) {
        return switch (action) {
            case "gather_wood"            -> "Chopped 4 oak logs from nearby tree.";
            case "gather_food",
                 "forage_food"            -> "Found and collected 2 apples.";
            case "eat_food"               -> inventory.stream()
                    .anyMatch(i -> i.name.contains("apple") || i.name.contains("bread"))
                    ? "Ate food, hunger restored."
                    : "Error: No food in inventory.";
            case "craft_tools"            -> inventory.stream()
                    .anyMatch(i -> i.name.contains("log") && i.count >= 3)
                    ? "Crafted wooden pickaxe and axe."
                    : "Error: Insufficient wood to craft tools.";
            case "build_shelter"          -> "Built a simple dirt shelter.";
            case "find_or_build_shelter"  -> "Located and entered nearby shelter.";
            case "attack_nearest_hostile" -> !nearbyHostiles.isEmpty()
                    ? "Attacked " + nearbyHostiles.get(0).type + "."
                    : "No hostile targets nearby.";
            case "flee_to_shelter"        -> "Fled to nearest shelter.";
            case "explore"                -> "Explored surrounding area, noted terrain.";
            case "craft_sword_or_flee"    -> "Crafted wooden sword for defense.";
            case "assist_neighbor"        -> "Moved to assist nearby agent.";
            case "share_food"             -> "Shared food with nearby hungry agent.";
            case "craft_and_defend"       -> "Crafted sword and moved to defend.";
            default                       -> "Performed action: " + action + ".";
        };
    }

    private void applyActionSideEffects(String action) {
        switch (action) {
            case "gather_wood" -> {
                boolean found = false;
                for (int i = 0; i < inventory.size(); i++) {
                    InventoryItem item = inventory.get(i);
                    if (item.name.equals("oak_log")) {
                        inventory.set(i, new InventoryItem("oak_log", item.count + 4));
                        found = true;
                        break;
                    }
                }
                if (!found) inventory.add(new InventoryItem("oak_log", 4));
            }
            case "eat_food", "forage_food", "gather_food" ->
                    hunger = Math.min(20, hunger + 4);
            case "find_or_build_shelter", "build_shelter" -> {
                hasShelter      = true;
                shelterDistance = 0;
            }
            case "attack_nearest_hostile" -> {
                if (!nearbyHostiles.isEmpty()) nearbyHostiles.remove(0);
            }
        }
    }

    // ── Helper types ─────────────────────────────────────────────────────────

    /** Configuration used by the mock adapter. All fields have sensible defaults. */
    public static final class Config {
        public String agentName        = "AgentMock";
        public int    gameTick         = 6000; // midday
        public float  health           = 20;
        public float  hunger           = 18;
        public List<HostileEntity>  nearbyHostiles = List.of();
        public List<InventoryItem>  inventory      = new ArrayList<>(List.of(
                new InventoryItem("oak_log", 8),
                new InventoryItem("apple",   3)));
        public int     shelterDistance = 0;
        public boolean hasShelter      = true;
        public List<String> nearbyChat = List.of();
    }

    /** A single entry in the action log. */
    public record ActionRecord(String action, String result) {}
}
