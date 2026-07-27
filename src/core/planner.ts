/**
 * core/planner.ts
 * Hierarchical planning loop:
 *   community goal  →  current subgoal  →  executable action
 *
 * Template-based subgoal generation:
 *   "Suppose you are {name}... find one subgoal aligned with {community_goal}
 *    based on identity, traits, current situation, observed others."
 */
import type { WorldState } from './perception';
import type { NeedScore } from './needs';
import type { RoleDefinition } from '../roles/registry';
import type { ReflectionBlock } from '../memory/reflection';
import type { SocialBlock } from '../memory/social';

export interface Subgoal {
  id: string;
  description: string;
  /** Top-level action string sent to the executor. */
  action: string;
  /** Role-specific or emergency context tag. */
  tag: 'emergency' | 'role' | 'survival' | 'social';
  priority: number; // higher = more urgent
}

export interface PlannerInput {
  state: WorldState;
  topNeeds: NeedScore[];
  role: RoleDefinition;
  reflection: ReflectionBlock;
  social: SocialBlock;
  communityGoal: string;
}

export interface PlannerOutput {
  subgoal: Subgoal;
  /** Full prompt context used (useful for debugging / logging). */
  promptContext: string;
}

// ── Emergency detection ───────────────────────────────────────────────────

/**
 * Returns a forced emergency subgoal when the situation is life-threatening.
 * Emergency subgoals ALWAYS override role-biased planning.
 */
function checkEmergency(
  state: WorldState,
  topNeeds: NeedScore[],
): Subgoal | null {
  const defense = topNeeds.find((n) => n.need === 'survival_defense');
  const food = topNeeds.find((n) => n.need === 'food');

  // Critical health – check BEFORE defense score so health emergency is not
  // masked by the defense-score boost that health < 6 applies in needs.ts.
  if (state.health < 4) {
    return {
      id: 'emergency_heal',
      description: 'Critically low health – eat or heal immediately',
      action: 'eat_food',
      tag: 'emergency',
      priority: 95,
    };
  }

  // Imminent hostile threat (only when actual hostiles are present)
  if (defense && defense.score >= 80 && state.nearbyHostiles.length > 0) {
    const hostile = state.nearbyHostiles[0];
    return {
      id: 'emergency_defend',
      description: `Defend against nearby ${hostile?.type ?? 'hostile'}`,
      action: hostile && hostile.distance < 5 ? 'attack_nearest_hostile' : 'flee_to_shelter',
      tag: 'emergency',
      priority: 100,
    };
  }

  // Night with no shelter
  if (state.isNight && !state.hasShelter) {
    return {
      id: 'emergency_shelter',
      description: 'Seek or build emergency shelter – it is night',
      action: 'find_or_build_shelter',
      tag: 'emergency',
      priority: 90,
    };
  }

  // Starvation
  if (food && food.score >= 90) {
    return {
      id: 'emergency_eat',
      description: 'Starving – find and eat food immediately',
      action: 'forage_food',
      tag: 'emergency',
      priority: 88,
    };
  }

  return null;
}

// ── Social-horizon reactive planning ─────────────────────────────────────

/**
 * Scans recent social messages for urgent keywords and returns a reactive
 * subgoal if one is warranted.
 */
function checkSocialReaction(social: SocialBlock): Subgoal | null {
  const urgentKeywords = [
    { words: ['zombie', 'broke', 'door', 'attacked'], action: 'craft_and_defend', description: 'Neighbor reports hostile breach – craft weapon and assist' },
    { words: ['help', 'dying', 'dying!'], action: 'assist_neighbor', description: 'Neighbor requesting assistance' },
    { words: ['food', 'starving', 'hungry'], action: 'share_food', description: 'Neighbor is hungry – share food if available' },
  ];

  for (const msg of social.messages) {
    const lower = msg.text.toLowerCase();
    for (const pattern of urgentKeywords) {
      if (pattern.words.some((w) => lower.includes(w))) {
        return {
          id: `social_react_${pattern.action}`,
          description: pattern.description,
          action: pattern.action,
          tag: 'social',
          priority: 70,
        };
      }
    }
  }
  return null;
}

// ── Need-to-action mapping ─────────────────────────────────────────────────

const NEED_ACTION_MAP: Record<string, { action: string; description: string }> = {
  survival_defense: { action: 'craft_sword_or_flee', description: 'Prepare for defense or flee danger' },
  food: { action: 'gather_food', description: 'Gather or grow food to restore hunger' },
  shelter: { action: 'build_shelter', description: 'Construct or improve shelter' },
  tools: { action: 'craft_tools', description: 'Gather wood and craft basic tools' },
  resources: { action: 'gather_wood', description: 'Chop trees and gather wood resources' },
};

// ── Role bias ─────────────────────────────────────────────────────────────

/**
 * Applies role-specific need-weight multipliers to the scored needs list
 * and returns the top adjusted need.
 * Emergency needs (score >= 75) are NEVER reduced below 75.
 */
function applyRoleBias(needs: NeedScore[], role: RoleDefinition): NeedScore {
  const weights = role.needWeights ?? {};
  const adjusted = needs.map((n) => {
    const multiplier = weights[n.need] ?? 1.0;
    // Protect emergency thresholds
    const rawAdjusted = n.score * multiplier;
    const adjusted = n.score >= 75 ? Math.max(n.score, rawAdjusted) : rawAdjusted;
    return { ...n, score: adjusted };
  });
  adjusted.sort((a, b) => b.score - a.score);
  return adjusted[0];
}

// ── Subgoal template generation ───────────────────────────────────────────

/**
 * Builds the template-based subgoal description used in LLM prompts.
 * Template:
 *   "Suppose you are {name}... find one subgoal aligned with {communityGoal}
 *    based on identity, traits, current situation, observed others."
 */
export function buildSubgoalPrompt(input: PlannerInput): string {
  const { state, role, reflection, social, communityGoal } = input;
  const traits = role.traits?.join(', ') ?? 'resourceful';

  return [
    `Suppose you are the person, ${state.agentName}, described below.`,
    `Your goal is: ${communityGoal}.`,
    `You need to find one subgoal aligned with your goal based on your identity,`,
    `traits, current situation, and the observed behaviour of others.`,
    ``,
    `Identity: ${role.label}`,
    `Traits: ${traits}`,
    ``,
    `[Self-Reflection Block]`,
    reflection.summary,
    ``,
    `[Social Horizon Block]`,
    social.summary,
    ``,
    `Current situation:`,
    `  Health: ${state.health}/20  Hunger: ${state.hunger}/20  Night: ${state.isNight}`,
    `  Hostiles nearby: ${state.nearbyHostiles.length}  Has shelter: ${state.hasShelter}`,
    `  Has tools: ${state.hasTools}`,
  ].join('\n');
}

// ── Main planner entry-point ───────────────────────────────────────────────

/**
 * Selects the next subgoal following this hierarchy:
 *   1. Emergency override (life-threatening conditions)
 *   2. Social-horizon reactive goal (neighbour in danger)
 *   3. Role-biased top need
 */
export function plan(input: PlannerInput): PlannerOutput {
  const { state, topNeeds, role } = input;

  const promptContext = buildSubgoalPrompt(input);

  // 1. Emergency override
  const emergency = checkEmergency(state, topNeeds);
  if (emergency) {
    return { subgoal: emergency, promptContext };
  }

  // 2. Social-reactive goal
  const socialReact = checkSocialReaction(input.social);
  if (socialReact) {
    return { subgoal: socialReact, promptContext };
  }

  // 3. Role-biased planning
  const topNeed = applyRoleBias(topNeeds, role);
  const mapped = NEED_ACTION_MAP[topNeed.need] ?? {
    action: 'explore',
    description: 'Explore surroundings for opportunities',
  };

  const subgoal: Subgoal = {
    id: `role_${role.id}_${topNeed.need}`,
    description: `[${role.label}] ${mapped.description}`,
    action: mapped.action,
    tag: 'role',
    priority: topNeed.score,
  };

  return { subgoal, promptContext };
}
