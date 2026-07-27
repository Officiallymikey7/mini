/**
 * __tests__/needs.test.ts
 * Unit tests for core/needs.ts – need scoring and priority selection.
 */
import { computeNeeds, getTopNeed } from '../core/needs';
import type { WorldState } from '../core/perception';

// Helper to build a minimal WorldState
function makeState(overrides: Partial<WorldState> = {}): WorldState {
  return {
    agentName: 'TestAgent',
    gameTick: 6000,
    isNight: false,
    health: 20,
    hunger: 18,
    nearbyHostiles: [],
    inventory: [{ name: 'oak_log', count: 32 }],
    shelterDistance: 0,
    hasShelter: true,
    hasTools: true,
    nearbyChat: [],
    timestamp: Date.now(),
    ...overrides,
  };
}

describe('computeNeeds', () => {
  it('returns all 5 need categories', () => {
    const needs = computeNeeds(makeState());
    const types = needs.map((n) => n.need);
    expect(types).toContain('survival_defense');
    expect(types).toContain('food');
    expect(types).toContain('shelter');
    expect(types).toContain('tools');
    expect(types).toContain('resources');
  });

  it('scores are sorted descending', () => {
    const needs = computeNeeds(makeState());
    for (let i = 1; i < needs.length; i++) {
      expect(needs[i - 1].score).toBeGreaterThanOrEqual(needs[i].score);
    }
  });

  it('gives max defense score when hostile is within 5 blocks', () => {
    const state = makeState({
      nearbyHostiles: [{ type: 'zombie', distance: 3 }],
    });
    const defense = computeNeeds(state).find((n) => n.need === 'survival_defense')!;
    expect(defense.score).toBe(100);
  });

  it('gives high defense score when hostile is 8 blocks away', () => {
    const state = makeState({
      nearbyHostiles: [{ type: 'skeleton', distance: 8 }],
    });
    const defense = computeNeeds(state).find((n) => n.need === 'survival_defense')!;
    expect(defense.score).toBe(80);
  });

  it('gives high food score when starving (hunger <= 2)', () => {
    const state = makeState({ hunger: 1 });
    const food = computeNeeds(state).find((n) => n.need === 'food')!;
    expect(food.score).toBe(95);
  });

  it('gives high shelter score at night without shelter', () => {
    const state = makeState({ isNight: true, hasShelter: false, shelterDistance: -1 });
    const shelter = computeNeeds(state).find((n) => n.need === 'shelter')!;
    expect(shelter.score).toBe(85);
  });

  it('gives high tool score when no tools available', () => {
    const state = makeState({ hasTools: false });
    const tools = computeNeeds(state).find((n) => n.need === 'tools')!;
    expect(tools.score).toBe(45);
  });

  it('defense is top priority over food when hostile is very close', () => {
    const state = makeState({
      hunger: 1,
      nearbyHostiles: [{ type: 'zombie', distance: 2 }],
    });
    const top = getTopNeed(state);
    expect(top.need).toBe('survival_defense');
  });
});

describe('getTopNeed', () => {
  it('returns single highest-priority need', () => {
    const state = makeState({ health: 3 });
    const top = getTopNeed(state);
    // With health=3 defense score should be very high
    expect(top.need).toBe('survival_defense');
  });

  it('returns resources need when everything else is fine', () => {
    const state = makeState({
      inventory: [{ name: 'oak_log', count: 2 }], // low wood
      hasTools: true,
      hunger: 18,
      hasShelter: true,
      nearbyHostiles: [],
      isNight: false,
    });
    const needs = computeNeeds(state);
    // Resources should be relatively high with low wood
    const resourceNeed = needs.find((n) => n.need === 'resources')!;
    expect(resourceNeed.score).toBe(30);
  });
});
