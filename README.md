# mini – Secure, Role-Driven Autonomous Minecraft Agent Framework

A fully modular, TypeScript-first framework for building intelligent, autonomous Minecraft agents that survive, adapt, and form functional societies — driven by needs, roles, memory, and shared governance.

---

## Architecture

```
mini/
├── src/
│   ├── agent/
│   │   └── loop.ts              # Main tick loop + demo entry-point
│   ├── config/
│   │   └── secrets.ts           # Env-only secret loading (OPENAI_API_KEY)
│   ├── core/
│   │   ├── perception.ts        # World-state snapshot collector
│   │   ├── needs.ts             # Urgency scoring & priority selection
│   │   ├── planner.ts           # Hierarchical goal planner
│   │   └── executor.ts          # Action execution + interruption
│   ├── memory/
│   │   ├── reflection.ts        # [Self-Reflection Block] builder
│   │   └── social.ts            # [Social Horizon Block] builder
│   ├── roles/
│   │   └── registry.ts          # Role definitions + extensible registry
│   ├── governance/
│   │   └── constitution.ts      # Shared rules, voting, amendments
│   ├── safety/
│   │   └── stallGuard.ts        # Goal-obsession / stall detector
│   └── integration/
│       └── botAdapter.ts        # Abstract adapter interface + mock adapter
└── src/__tests__/
    ├── needs.test.ts
    ├── stallGuard.test.ts
    ├── roles.test.ts
    ├── governance.test.ts
    └── planner.test.ts
```

### How it fits together

Each **agent tick** runs this pipeline:

```
perceive() → computeNeeds() → checkEmergency() → stallGuard check
           → plan()          → execute()         → record memory
           → governance check (every N ticks)
```

The planner injects two dynamic memory blocks into every subgoal prompt:

- **[Self-Reflection Block]** – recent action outcomes, failures, inventory deltas.
- **[Social Horizon Block]** – nearby chat messages from other agents.

```
Template:
  "Suppose you are {name}. Your goal is: {communityGoal}.
   Find one subgoal based on your identity, traits, current situation,
   and observed behaviour of others."
```

Emergency conditions (close hostiles, critical health, night/no shelter, starvation) **always override** role-biased planning.

---

## Setup

### Prerequisites

- Node.js ≥ 18
- npm ≥ 9

### Install

```bash
git clone https://github.com/Officiallymikey7/mini.git
cd mini
npm install
```

### Environment Variables

⚠️ **Never commit API keys to version control.**

```bash
cp .env.example .env
# Edit .env and fill in your values
```

| Variable | Required | Default | Description |
|---|---|---|---|
| `OPENAI_API_KEY` | Yes | – | OpenAI API key (read-only, env-only) |
| `AGENT_TICK_MS` | No | `5000` | Tick interval in milliseconds |
| `GOVERNANCE_TICK_INTERVAL` | No | `10` | Run governance checks every N ticks |
| `LOG_LEVEL` | No | `info` | Log verbosity: debug / info / warn / error |

### Build

```bash
npm run build   # Compiles TypeScript → dist/
```

---

## Running the Simulation

### Mock demo (no Minecraft server required)

```bash
npm start
# or: npx ts-node src/agent/loop.ts
```

The demo creates a simulated agent named **Arlo** using the `MockBotAdapter`.  
No network connection or Minecraft server is needed — everything runs in-process.

Sample output:
```
=== Minecraft Agent Demo ===
Running 5 ticks…

[INFO ] Agent Arlo starting (role: farmer)
[INFO ] [Tick 1] Subgoal: [Emergency] Seek or build emergency shelter → action: find_or_build_shelter
[INFO ] [Tick 2] Subgoal: [Farmer] Gather or grow food to restore hunger → action: gather_food
...

Action log:
  • find_or_build_shelter: Located and entered nearby shelter.
  • gather_food: Found and collected 2 apples.
```

---

## Running Tests

```bash
npm test            # Run all tests with coverage
npm test -- --watch # Watch mode
```

All 43 tests should pass across 5 test suites:
- `needs.test.ts` – urgency scoring, priority ordering
- `stallGuard.test.ts` – stall detection, fallback actions
- `roles.test.ts` – registry, role biasing, emergency override
- `governance.test.ts` – proposals, voting, amendments, violation checks
- `planner.test.ts` – emergency paths, social reactions, memory blocks

---

## Adding a New Role

1. Open `src/roles/registry.ts`
2. Define a `RoleDefinition` object:

```typescript
const HEALER: RoleDefinition = {
  id: 'healer',
  label: 'Healer',
  communityGoal: 'Keep all community members healthy and fed.',
  needWeights: {
    food: 1.4,    // Boosted: healers care about food supply
    shelter: 1.2, // Boosted: safe space to treat patients
  },
  traits: ['compassionate', 'knowledgeable', 'calm'],
};
```

3. Add it to `DEFAULT_ROLES` (or call `registerRole(HEALER)` at startup).
4. Assign the role id `'healer'` in your `AgentConfig`.

Need weights > 1.0 boost that need's urgency; < 1.0 reduce it.  
**Emergency survival needs (score ≥ 75) are never reduced below their emergency threshold.**

---

## Integration: Connecting a Real Minecraft Server

The framework is adapter-agnostic. Implement the `BotAdapter` interface in `src/integration/botAdapter.ts`:

```typescript
export interface BotAdapter {
  getWorldState(): Promise<RawWorldState>;
  performAction(action: string): Promise<string>;
  sendChat(message: string): Promise<void>;
}
```

Example with [mineflayer](https://github.com/PrismarineJS/mineflayer):

```typescript
import mineflayer from 'mineflayer';
import type { BotAdapter, RawWorldState } from './integration/botAdapter';

export class MineflayerAdapter implements BotAdapter {
  constructor(private bot: mineflayer.Bot) {}

  async getWorldState(): Promise<RawWorldState> {
    // Map mineflayer world state to RawWorldState
    return { ... };
  }

  async performAction(action: string): Promise<string> {
    // Dispatch action string to mineflayer pathfinding/crafting APIs
    return 'done';
  }

  async sendChat(message: string): Promise<void> {
    this.bot.chat(message);
  }
}
```

---

## Governance / Constitution

Agents share a `Constitution` that stores community rules and handles amendments via a voting loop:

```typescript
const constitution = new Constitution(new InMemoryConstitutionStorage());

// Agent proposes a new rule
const amendment = await constitution.proposeAmendment(
  'agentA',
  'No agent shall destroy another agent's crops.',
);

// Agents vote
await constitution.vote(amendment.id, 'agentA', 'yes');
await constitution.vote(amendment.id, 'agentB', 'yes');
await constitution.vote(amendment.id, 'agentC', 'no');

// Tally (pass threshold = 50%)
const result = await constitution.tally(amendment.id, 3);
// result.status === 'accepted' → rule is now active

// Check for violations
const violations = await constitution.checkViolations('Player was seen stealing crops');
```

To connect a real shared document (e.g. Google Docs), implement `DocConstitutionStorage` and pass it to the `Constitution` constructor.

---

## Security Note

⚠️ **API Key Safety**

- The `OPENAI_API_KEY` is **only ever read from `process.env`** (see `src/config/secrets.ts`).
- The key is **never** accepted as a function argument, constructor parameter, config file value, or hardcoded string.
- `.env` is in `.gitignore` — it will not be committed.
- If you accidentally expose a key in a commit, **revoke it immediately** at [platform.openai.com/api-keys](https://platform.openai.com/api-keys) and generate a new one.
- Run `git log --all -S 'sk-'` to scan your history for accidentally committed keys.

---

## License

MIT
