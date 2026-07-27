/**
 * integration/botAdapter.ts
 * Abstract adapter interface for Minecraft bot interaction.
 * Concrete adapters implement this interface so the agent framework is
 * not locked to any specific Minecraft library (mineflayer, mcp, etc.).
 *
 * Also exports MockBotAdapter – a fully simulated adapter for local testing.
 */
import type { WorldState, HostileEntity, InventoryItem } from '../core/perception';

// ── Adapter interface ──────────────────────────────────────────────────────

/** Raw world state returned by the adapter before perception post-processing. */
export type RawWorldState = Omit<WorldState, 'isNight' | 'hasTools' | 'timestamp'>;

export interface BotAdapter {
  /** Returns the current raw world state from the Minecraft server/simulation. */
  getWorldState(): Promise<RawWorldState>;

  /**
   * Performs the named action.  Returns a result message string.
   * If the action fails the message should start with "Error:".
   */
  performAction(action: string): Promise<string>;

  /** Sends a chat message as this agent. */
  sendChat(message: string): Promise<void>;
}

// ── Mock / simulation adapter ──────────────────────────────────────────────

export interface MockWorldConfig {
  agentName?: string;
  gameTick?: number;
  health?: number;
  hunger?: number;
  nearbyHostiles?: HostileEntity[];
  inventory?: InventoryItem[];
  shelterDistance?: number;
  hasShelter?: boolean;
  nearbyChat?: string[];
}

/**
 * Simulated Minecraft adapter that runs entirely in-process.
 * Use this for unit tests, demonstrations, and local development.
 *
 * The mock can be mutated via `update()` to simulate world changes mid-loop.
 */
export class MockBotAdapter implements BotAdapter {
  private state: RawWorldState;
  public actionLog: Array<{ action: string; result: string }> = [];
  public chatLog: string[] = [];

  /** Map of action name → simulated result string.  Override per-test. */
  public actionResults: Record<string, string> = {};

  constructor(config: MockWorldConfig = {}) {
    this.state = buildDefaultState(config);
  }

  /** Update any fields of the simulated world state. */
  update(partial: Partial<RawWorldState>): void {
    this.state = { ...this.state, ...partial };
  }

  async getWorldState(): Promise<RawWorldState> {
    return { ...this.state };
  }

  async performAction(action: string): Promise<string> {
    const result = this.actionResults[action] ?? simulateAction(action, this.state);
    this.actionLog.push({ action, result });

    // Auto-mutate state based on action (simple simulation)
    applyActionSideEffects(action, this.state);

    return result;
  }

  async sendChat(message: string): Promise<void> {
    this.chatLog.push(message);
  }
}

// ── Mock helpers ───────────────────────────────────────────────────────────

function buildDefaultState(cfg: MockWorldConfig): RawWorldState {
  return {
    agentName: cfg.agentName ?? 'AgentMock',
    gameTick: cfg.gameTick ?? 6000,         // midday
    health: cfg.health ?? 20,
    hunger: cfg.hunger ?? 18,
    nearbyHostiles: cfg.nearbyHostiles ?? [],
    inventory: cfg.inventory ?? [
      { name: 'oak_log', count: 8 },
      { name: 'apple', count: 3 },
    ],
    shelterDistance: cfg.shelterDistance ?? 0,
    hasShelter: cfg.hasShelter ?? true,
    nearbyChat: cfg.nearbyChat ?? [],
  };
}

function simulateAction(action: string, state: RawWorldState): string {
  switch (action) {
    case 'gather_wood':
      return 'Chopped 4 oak logs from nearby tree.';
    case 'gather_food':
    case 'forage_food':
      return 'Found and collected 2 apples.';
    case 'eat_food':
      return state.inventory.some((i) => i.name.includes('apple') || i.name.includes('bread'))
        ? 'Ate food, hunger restored.'
        : 'Error: No food in inventory.';
    case 'craft_tools':
      return state.inventory.some((i) => i.name.includes('log') && i.count >= 3)
        ? 'Crafted wooden pickaxe and axe.'
        : 'Error: Insufficient wood to craft tools.';
    case 'build_shelter':
      return 'Built a simple dirt shelter.';
    case 'find_or_build_shelter':
      return 'Located and entered nearby shelter.';
    case 'attack_nearest_hostile':
      return state.nearbyHostiles.length > 0
        ? `Attacked ${state.nearbyHostiles[0].type}.`
        : 'No hostile targets nearby.';
    case 'flee_to_shelter':
      return 'Fled to nearest shelter.';
    case 'explore':
      return 'Explored surrounding area, noted terrain.';
    case 'craft_sword_or_flee':
      return 'Crafted wooden sword for defense.';
    case 'assist_neighbor':
      return 'Moved to assist nearby agent.';
    case 'share_food':
      return 'Shared food with nearby hungry agent.';
    case 'craft_and_defend':
      return 'Crafted sword and moved to defend.';
    default:
      return `Performed action: ${action}.`;
  }
}

function applyActionSideEffects(action: string, state: RawWorldState): void {
  // Simulate simple side effects on the mutable state reference
  if (action === 'gather_wood') {
    const log = state.inventory.find((i) => i.name === 'oak_log');
    if (log) log.count += 4;
    else state.inventory.push({ name: 'oak_log', count: 4 });
  }
  if (action === 'eat_food' || action === 'forage_food' || action === 'gather_food') {
    state.hunger = Math.min(20, state.hunger + 4);
  }
  if (action === 'find_or_build_shelter' || action === 'build_shelter') {
    state.hasShelter = true;
    state.shelterDistance = 0;
  }
  if (action === 'attack_nearest_hostile') {
    state.nearbyHostiles = state.nearbyHostiles.slice(1);
  }
}
