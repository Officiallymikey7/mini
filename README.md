# mini – Role-Driven Autonomous Minecraft Agent (Fabric Mod)

A Fabric mod for Minecraft 1.21.1 that brings an intelligent, autonomous agent into your dev world — driven by needs, roles, memory, and shared governance.

> **Platform:** Java 21 · Fabric Loader 0.15.11 · Minecraft 1.21.1

---

## Project structure

The repository is a **two-module Gradle project**:

| Module | Purpose |
|--------|---------|
| `:` (root) | Pure-Java core logic; no Minecraft/Fabric dependencies. Testable offline. |
| `:fabric` | Fabric Loom mod build; Minecraft-specific wiring. Depends on root. |

```
├── build.gradle                              # Root: plain java plugin + JUnit 5
├── fabric/
│   ├── build.gradle                          # :fabric: fabric-loom plugin
│   └── src/main/java/…/mini/
│       ├── MiniMod.java                      # Fabric ModInitializer entry-point
│       ├── body/VillagerBody.java            # In-world NPC body lifecycle (spawn/tick/despawn)
│       ├── client/
│       │   ├── MiniModClient.java            # Client entry-point; registers entity renderer
│       │   └── renderer/AiPlayerRenderer.java# BipedEntityModel renderer (PLAYER layer)
│       ├── command/AgentCommand.java         # /mini command tree
│       ├── entity/
│       │   ├── AiPlayerEntity.java           # PathAwareEntity body with ai* hooks
│       │   ├── EntityTypes.java              # Entity-type registry + attribute binding
│       │   └── goal/AiMoveToGoal.java        # Goal that reads AiPlayerEntity destinations
│       └── integration/FabricWorldAdapter.java
│
└── src/
    ├── main/java/io/github/officiallymikey7/mini/
    │   ├── agent/
    │   │   ├── Agent.java                    # Main tick-loop driver
    │   │   └── AgentConfig.java              # Per-agent configuration
    │   ├── core/
    │   │   ├── WorldState.java               # Immutable world-state snapshot
    │   │   ├── Perception.java               # Adapter → WorldState conversion
    │   │   ├── Needs.java                    # Urgency scoring (0–100)
    │   │   ├── Planner.java                  # Hierarchical goal planner
    │   │   └── Executor.java                 # Action execution
    │   ├── memory/
    │   │   ├── ReflectionMemory.java         # [Self-Reflection Block] builder
    │   │   └── SocialMemory.java             # [Social Horizon Block] builder
    │   ├── roles/
    │   │   ├── RoleDefinition.java           # Role data + need-weight map
    │   │   └── RoleRegistry.java             # Built-in roles + extensible registry
    │   ├── governance/
    │   │   └── Constitution.java             # Shared rules, voting, amendments
    │   ├── safety/
    │   │   └── StallGuard.java               # Goal-obsession / stall detector
    │   └── integration/
    │       ├── BotAdapter.java               # Adapter interface
    │       └── MockBotAdapter.java           # In-process simulation adapter
    └── test/java/io/github/officiallymikey7/mini/
        ├── NeedsTest.java
        ├── StallGuardTest.java
        ├── RolesTest.java
        ├── GovernanceTest.java
        └── PlannerTest.java
```

### Agent tick pipeline

Each agent tick runs this pipeline (same logic as the original TypeScript implementation):

```
Perception.perceive()
  → Needs.computeNeeds()
  → checkEmergency()
  → StallGuard check
  → Planner.plan()
  → Executor.execute()
  → ReflectionMemory.record()
  → Constitution.checkViolations()  (every N ticks)
```

The planner injects two memory blocks into every subgoal prompt:

- **[Self-Reflection Block]** – recent action outcomes, failures, inventory deltas.
- **[Social Horizon Block]** – nearby chat messages from other agents.

Emergency conditions (close hostiles, critical health, night/no shelter, starvation) **always override** role-biased planning.

---

## Prerequisites

| Tool | Version |
|------|---------|
| JDK  | 21      |
| Gradle (via wrapper) | 8.8 |
| Fabric Loader | 0.15.11 |
| Minecraft | 1.21.1 |

> **No Gradle installation required** if you use IntelliJ IDEA — it handles the Gradle wrapper automatically.

---

## Setup

```bash
git clone https://github.com/Officiallymikey7/mini.git
cd mini
```

If you need to bootstrap the Gradle wrapper JAR (first-time setup with a globally-installed Gradle):

```bash
gradle wrapper --gradle-version 8.8
```

---

## Build

```bash
# Full Fabric mod build (requires Java 21 + network access to maven.fabricmc.net)
./gradlew :fabric:build
```

The compiled mod JAR will appear in `fabric/build/libs/`.

---

## Running Tests

Unit tests cover core logic and run **offline** (no Minecraft runtime needed):

```bash
# Using the Gradle wrapper (recommended):
./gradlew :test --configure-on-demand

# Or with a globally-installed Gradle:
gradle :test --configure-on-demand
```

The `--configure-on-demand` flag tells Gradle to skip configuring the `:fabric`
subproject (which needs Fabric Maven) so tests run without network access.

Test suites:
- `NeedsTest` – urgency scoring, priority ordering
- `StallGuardTest` – stall detection, fallback actions, timeout-based stall
- `RolesTest` – registry look-up, need weights, custom role registration
- `GovernanceTest` – proposals, voting, amendments, violation checks
- `PlannerTest` – emergency paths, social reactions, role-biased planning, prompt context

---

## Running in a Dev World (Fabric)

```bash
./gradlew :fabric:runClient      # launch Minecraft with the mod loaded
# or
./gradlew :fabric:runServer      # launch a dedicated server
```

Once in-game, use the `/mini` command:

| Command | Description |
|---------|-------------|
| `/mini start [role]` | Start the agent with the given role (default: `farmer`) |
| `/mini stop`         | Stop the running agent |
| `/mini status`       | Show current tick count and latest world state |
| `/mini roles`        | List all available roles |

The agent runs one logic cycle every **20 game ticks (1 second)** driven by the server tick event.

---

## Villager Body Runtime

When you run `/mini start`, the mod now binds the agent to a **real `VillagerEntity`** (nearest available villager) or spawns one if needed.

### Runtime behaviour

`VillagerBody` executes long-running survival actions every tick (movement, shelter seeking, wood/food gathering, and emergency fleeing) while the planner continues to choose high-level actions.

### Spawn and lifecycle

```
/mini start [role]
  ├─ AgentCommand.startAgent()
  │    ├─ creates VillagerBody
  │    ├─ calls body.ensureSpawned(player)   ← binds/spawns VillagerEntity
  │    └─ registers ActiveEntry keyed by villager UUID
  └─ server tick (every tick)
       ├─ VillagerBody.tick(player)          ← drives autonomous action task runtime
       └─ Agent.tick() (every 20 ticks)      ← plans next high-level action
```

`VillagerBody` also calls `ensureSpawned` on every tick, so if a villager is killed during a session the body automatically binds to or spawns a replacement. Note that runtime state is held in memory; a server restart will require issuing `/mini start` again to reattach an agent.

### Example session

```
/mini roles
→ [Mini] Available roles: farmer (Farmer), trader (Trader / Merchant), guard (Guard / Defender), ...

/mini start farmer
→ [Mini] Agent started as role: farmer. Use /mini stop to stop.

# After a few cycles (check server log):
[INFO] [Arlo] [Tick 1] Subgoal: [Emergency] Seek or build emergency shelter → action: find_or_build_shelter
[INFO] [Arlo] SUCCESS: Located and entered nearby shelter.
[INFO] [Arlo] [Tick 2] Subgoal: [Farmer] Gather or grow food to restore hunger → action: gather_food

/mini status
→ [Mini] Agent running – tick=3 health=20.0 hunger=18

/mini stop
→ [Mini] Agent stopped.
```

---

## Built-in Roles

| ID | Label | Boosted Needs |
|----|-------|--------------|
| `farmer` | Farmer | food ×1.4, resources ×1.2 |
| `trader` | Trader / Merchant | resources ×1.5, tools ×1.2 |
| `guard` | Guard / Defender | survival_defense ×1.5, tools ×1.3 |
| `priest` | Cultural Leader / Priest | shelter ×1.2 |
| `adventurer` | Adventurer / Explorer | resources ×1.4, tools ×1.3 |
| `blacksmith` | Blacksmith | tools ×1.6, resources ×1.3 |

---

## Adding a New Role

```java
RoleDefinition healer = RoleDefinition.builder("healer")
        .label("Healer")
        .communityGoal("Keep all community members healthy and fed.")
        .needWeight(NeedType.FOOD, 1.4)
        .needWeight(NeedType.SHELTER, 1.2)
        .traits("compassionate", "knowledgeable", "calm")
        .build();

RoleRegistry.register(healer);
```

Need weights > 1.0 boost that need's urgency; < 1.0 reduce it.  
**Emergency survival needs (score ≥ 75) are never reduced below their threshold.**

---

## Custom Adapter

Implement `BotAdapter` to connect the agent to any environment:

```java
public final class MyAdapter implements BotAdapter {

    @Override
    public RawWorldState getWorldState() {
        // Return current world data
        return new RawWorldState(...);
    }

    @Override
    public String performAction(String action) {
        // Execute the action; return "Error: ..." on failure
        return "Done.";
    }

    @Override
    public void sendChat(String message) { /* ... */ }
}
```

---

## Governance / Constitution

```java
Constitution constitution = new Constitution();

Amendment a = constitution.proposeAmendment(
        "agentA", "No agent shall destroy another agent's crops.");

constitution.vote(a.id, "agentA", "yes");
constitution.vote(a.id, "agentB", "yes");
constitution.vote(a.id, "agentC", "no");

Amendment result = constitution.tally(a.id, 3);
// result.status == ACCEPTED → rule is now active

List<Rule> violations = constitution.checkViolations("Someone was seen stealing crops");
```

To connect a shared document store, implement `ConstitutionStorage` and pass it to the `Constitution` constructor.

---

## License

MIT
