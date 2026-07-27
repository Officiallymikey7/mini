/**
 * __tests__/roles.test.ts
 * Tests for role biasing, registry, and emergency override behaviour.
 */
import { computeNeeds } from '../core/needs';
import { plan } from '../core/planner';
import { getRole, registerRole, listRoles } from '../roles/registry';
import { ReflectionMemory } from '../memory/reflection';
import { SocialMemory } from '../memory/social';
import type { WorldState } from '../core/perception';

function makeState(overrides: Partial<WorldState> = {}): WorldState {
  return {
    agentName: 'Rollo',
    gameTick: 6000,
    isNight: false,
    health: 20,
    hunger: 15,
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

describe('Role registry', () => {
  it('default roles are registered', () => {
    const roles = listRoles().map((r) => r.id);
    expect(roles).toContain('farmer');
    expect(roles).toContain('trader');
    expect(roles).toContain('guard');
    expect(roles).toContain('priest');
    expect(roles).toContain('adventurer');
    expect(roles).toContain('blacksmith');
  });

  it('can register a new custom role', () => {
    registerRole({
      id: 'healer',
      label: 'Healer',
      communityGoal: 'Keep all agents healthy',
      needWeights: { food: 1.3 },
      traits: ['compassionate'],
    });
    expect(getRole('healer')).toBeDefined();
    expect(getRole('healer')?.label).toBe('Healer');
  });

  it('returns undefined for unknown role', () => {
    expect(getRole('unknown_role_xyz')).toBeUndefined();
  });
});

describe('Role biasing', () => {
  const reflection = new ReflectionMemory().build();
  const social = new SocialMemory().build();

  it('guard role produces defense-related subgoal when hostiles present', () => {
    const state = makeState({
      nearbyHostiles: [{ type: 'zombie', distance: 8 }],
    });
    const role = getRole('guard')!;
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    // Guard with hostiles nearby should trigger defense
    expect(['survival_defense', 'emergency_defend'].some((id) =>
      subgoal.id.includes('defense') || subgoal.id.includes('defend') || subgoal.action.includes('hostile') || subgoal.action.includes('flee')
    )).toBe(true);
  });

  it('farmer role produces food-related subgoal when food is low', () => {
    const state = makeState({ hunger: 5, nearbyHostiles: [], isNight: false, hasShelter: true });
    const role = getRole('farmer')!;
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    // With hunger=5 and farmer boost (1.4x), food should be top priority
    expect(subgoal.action).toContain('food');
  });
});

describe('Emergency override ignores role bias', () => {
  const reflection = new ReflectionMemory().build();
  const social = new SocialMemory().build();

  it('even the trader role triggers emergency defense when hostile is <5 blocks', () => {
    const state = makeState({
      nearbyHostiles: [{ type: 'creeper', distance: 2 }],
    });
    const role = getRole('trader')!;
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    // Emergency should always override role biasing
    expect(subgoal.tag).toBe('emergency');
    expect(subgoal.id).toContain('emergency');
  });

  it('priest role gets emergency shelter at night with no shelter', () => {
    const state = makeState({ isNight: true, hasShelter: false, nearbyHostiles: [] });
    const role = getRole('priest')!;
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    expect(subgoal.tag).toBe('emergency');
    expect(subgoal.action).toBe('find_or_build_shelter');
  });
});
