/**
 * core/needs.ts
 * Computes urgency scores for each survival need and exposes deterministic
 * priority selection.  Higher score = more urgent.
 */
import type { WorldState } from './perception';

export type NeedType =
  | 'survival_defense'  // hostile threats / night without shelter
  | 'food'              // low hunger
  | 'shelter'           // no shelter at night
  | 'tools'             // missing or broken tools
  | 'resources';        // general resource gathering

export interface NeedScore {
  need: NeedType;
  score: number; // 0–100
  reason: string;
}

/** Returns all need scores sorted descending (most urgent first). */
export function computeNeeds(state: WorldState): NeedScore[] {
  const scores: NeedScore[] = [];

  // ── Survival / Defense ───────────────────────────────────────────────────
  const hostileCount = state.nearbyHostiles.length;
  const closestHostileDistance =
    hostileCount > 0
      ? Math.min(...state.nearbyHostiles.map((h) => h.distance))
      : Infinity;

  let defenseScore = 0;
  if (closestHostileDistance < 5) defenseScore = 100;
  else if (closestHostileDistance < 10) defenseScore = 80;
  else if (closestHostileDistance < 20) defenseScore = 50;
  else if (state.isNight && !state.hasShelter) defenseScore = 60;
  else if (state.isNight) defenseScore = 20;
  if (state.health < 6) defenseScore = Math.max(defenseScore, 90);

  scores.push({
    need: 'survival_defense',
    score: defenseScore,
    reason:
      hostileCount > 0
        ? `${hostileCount} hostile(s), closest at ${closestHostileDistance.toFixed(1)}m`
        : state.isNight
        ? 'Night-time danger'
        : 'Safe',
  });

  // ── Food ─────────────────────────────────────────────────────────────────
  let foodScore = 0;
  if (state.hunger <= 2) foodScore = 95;
  else if (state.hunger <= 6) foodScore = 70;
  else if (state.hunger <= 10) foodScore = 40;
  else if (state.hunger <= 14) foodScore = 15;

  scores.push({
    need: 'food',
    score: foodScore,
    reason: `Hunger: ${state.hunger}/20`,
  });

  // ── Shelter ───────────────────────────────────────────────────────────────
  let shelterScore = 0;
  if (state.isNight && !state.hasShelter) {
    shelterScore = state.shelterDistance < 0 ? 85 : Math.max(10, 85 - state.shelterDistance * 2);
  } else if (!state.isNight && !state.hasShelter) {
    shelterScore = 20; // prepare before nightfall
  }

  scores.push({
    need: 'shelter',
    score: shelterScore,
    reason: state.hasShelter ? 'Has shelter' : `No shelter (distance: ${state.shelterDistance})`,
  });

  // ── Tools ─────────────────────────────────────────────────────────────────
  const toolScore = state.hasTools ? 5 : 45;
  scores.push({
    need: 'tools',
    score: toolScore,
    reason: state.hasTools ? 'Tools available' : 'No usable tools',
  });

  // ── Resources ────────────────────────────────────────────────────────────
  const woodCount = state.inventory
    .filter((i) => i.name.includes('log') || i.name.includes('wood'))
    .reduce((sum, i) => sum + i.count, 0);
  const resourceScore = woodCount < 16 ? 30 : 10;
  scores.push({
    need: 'resources',
    score: resourceScore,
    reason: `Wood: ${woodCount}`,
  });

  // Sort most urgent first
  return scores.sort((a, b) => b.score - a.score);
}

/** Returns the single highest-priority need (deterministic). */
export function getTopNeed(state: WorldState): NeedScore {
  return computeNeeds(state)[0];
}
