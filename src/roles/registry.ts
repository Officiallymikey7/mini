/**
 * roles/registry.ts
 * Extensible role registry.  Each role definition provides a label, a set of
 * goal-weight multipliers, and the community goal string used in planning
 * templates.
 *
 * To add a new role:
 *   1. Define a RoleDefinition object below.
 *   2. Call registerRole(yourRole) or add it to DEFAULT_ROLES.
 */
import type { NeedType } from '../core/needs';

export interface RoleDefinition {
  /** Unique identifier used in agent config. */
  id: string;
  /** Human-readable label. */
  label: string;
  /** Community goal injected into the planner template for this role. */
  communityGoal: string;
  /**
   * Optional per-need weight multipliers.  Values > 1.0 boost the need's
   * urgency; values < 1.0 reduce it.  Emergency survival needs are NEVER
   * reduced below their emergency threshold regardless of these weights.
   */
  needWeights?: Partial<Record<NeedType, number>>;
  /** Optional trait list included in planner prompts for flavour. */
  traits?: string[];
}

const roleRegistry = new Map<string, RoleDefinition>();

/** Register a role definition (idempotent; last write wins). */
export function registerRole(role: RoleDefinition): void {
  roleRegistry.set(role.id, role);
}

/** Retrieve a role by id.  Returns undefined if not found. */
export function getRole(id: string): RoleDefinition | undefined {
  return roleRegistry.get(id);
}

/** Returns all registered roles. */
export function listRoles(): RoleDefinition[] {
  return Array.from(roleRegistry.values());
}

// ── Built-in role definitions ─────────────────────────────────────────────

const FARMER: RoleDefinition = {
  id: 'farmer',
  label: 'Farmer',
  communityGoal:
    'Maintain and expand the community farm to ensure a stable food supply for all agents.',
  needWeights: { food: 1.4, resources: 1.2 },
  traits: ['diligent', 'patient', 'earth-connected', 'community-minded'],
};

const TRADER: RoleDefinition = {
  id: 'trader',
  label: 'Trader / Merchant',
  communityGoal:
    'Facilitate fair resource exchange between agents using emeralds, improving collective efficiency.',
  needWeights: { resources: 1.5, tools: 1.2 },
  traits: ['entrepreneurial', 'negotiator', 'opportunistic', 'fair-minded'],
};

const GUARD: RoleDefinition = {
  id: 'guard',
  label: 'Guard / Defender',
  communityGoal:
    'Protect the settlement and its residents from hostile mobs and external threats.',
  needWeights: { survival_defense: 1.5, tools: 1.3 },
  traits: ['courageous', 'vigilant', 'protective', 'disciplined'],
};

const PRIEST: RoleDefinition = {
  id: 'priest',
  label: 'Cultural Leader / Priest',
  communityGoal:
    'Preserve community values, share doctrine, mediate disputes, and foster cultural cohesion.',
  needWeights: { shelter: 1.2 },
  traits: ['wise', 'eloquent', 'spiritual', 'empathetic', 'influential'],
};

const ADVENTURER: RoleDefinition = {
  id: 'adventurer',
  label: 'Adventurer / Explorer',
  communityGoal:
    'Explore new territories, map resources, and bring back rare materials for the community.',
  needWeights: { resources: 1.4, tools: 1.3 },
  traits: ['bold', 'curious', 'self-reliant', 'resilient'],
};

const BLACKSMITH: RoleDefinition = {
  id: 'blacksmith',
  label: 'Blacksmith',
  communityGoal:
    'Craft and maintain tools, weapons, and armour to keep all agents well-equipped.',
  needWeights: { tools: 1.6, resources: 1.3 },
  traits: ['skilled', 'methodical', 'practical', 'reliable'],
};

/** Default roles pre-registered on module load. */
export const DEFAULT_ROLES: RoleDefinition[] = [
  FARMER,
  TRADER,
  GUARD,
  PRIEST,
  ADVENTURER,
  BLACKSMITH,
];

// Auto-register defaults
DEFAULT_ROLES.forEach(registerRole);
