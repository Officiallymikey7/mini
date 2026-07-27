/**
 * core/perception.ts
 * Collects a snapshot of the agent's current world state from the bot adapter.
 */
import type { BotAdapter } from '../integration/botAdapter';

export interface HostileEntity {
  type: string;
  distance: number;
}

export interface InventoryItem {
  name: string;
  count: number;
}

/** Full world-state snapshot perceived by the agent each tick. */
export interface WorldState {
  agentName: string;
  /** Current Minecraft game tick (0–23999). */
  gameTick: number;
  /** true when gameTick >= 13000 (nighttime). */
  isNight: boolean;
  /** Agent health 0–20. */
  health: number;
  /** Agent food level 0–20. */
  hunger: number;
  /** List of hostile mobs within detection radius. */
  nearbyHostiles: HostileEntity[];
  /** Current inventory snapshot. */
  inventory: InventoryItem[];
  /** Distance to nearest known shelter (-1 if unknown). */
  shelterDistance: number;
  /** true when the agent is currently inside a shelter. */
  hasShelter: boolean;
  /** Whether the agent has a usable tool (axe, pickaxe, sword). */
  hasTools: boolean;
  /** Recent chat messages from nearby players/agents. */
  nearbyChat: string[];
  /** Timestamp of this perception snapshot. */
  timestamp: number;
}

/**
 * Gathers current world state from the provided bot adapter.
 * All heavy world queries are delegated to the adapter so this
 * module stays testable without a live Minecraft connection.
 */
export async function perceive(adapter: BotAdapter): Promise<WorldState> {
  const raw = await adapter.getWorldState();

  const isNight = raw.gameTick >= 13000 && raw.gameTick <= 23000;
  const hasTools = raw.inventory.some((item) =>
    ['wooden_axe', 'stone_axe', 'iron_axe', 'diamond_axe',
      'wooden_pickaxe', 'stone_pickaxe', 'iron_pickaxe', 'diamond_pickaxe',
      'wooden_sword', 'stone_sword', 'iron_sword', 'diamond_sword',
      'netherite_sword', 'netherite_pickaxe'].includes(item.name),
  );

  return {
    agentName: raw.agentName,
    gameTick: raw.gameTick,
    isNight,
    health: raw.health,
    hunger: raw.hunger,
    nearbyHostiles: raw.nearbyHostiles,
    inventory: raw.inventory,
    shelterDistance: raw.shelterDistance,
    hasShelter: raw.hasShelter,
    hasTools,
    nearbyChat: raw.nearbyChat,
    timestamp: Date.now(),
  };
}
