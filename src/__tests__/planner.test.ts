/**
 * __tests__/planner.test.ts
 * Tests for core/planner.ts – subgoal generation, reflection/social context,
 * emergency override, and template prompt building.
 */
import { plan, buildSubgoalPrompt } from '../core/planner';
import { computeNeeds } from '../core/needs';
import { ReflectionMemory } from '../memory/reflection';
import { SocialMemory } from '../memory/social';
import { getRole } from '../roles/registry';
import type { WorldState } from '../core/perception';

function makeState(overrides: Partial<WorldState> = {}): WorldState {
  return {
    agentName: 'Mira',
    gameTick: 6000,
    isNight: false,
    health: 20,
    hunger: 16,
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

function makeBlocks() {
  return {
    reflection: new ReflectionMemory().build(),
    social: new SocialMemory().build(),
  };
}

describe('Planner – emergency override', () => {
  it('returns emergency_defend when hostile is within 5 blocks', () => {
    const state = makeState({ nearbyHostiles: [{ type: 'zombie', distance: 3 }] });
    const role = getRole('trader')!;
    const { reflection, social } = makeBlocks();
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    expect(subgoal.id).toBe('emergency_defend');
    expect(subgoal.tag).toBe('emergency');
  });

  it('returns emergency_shelter at night without shelter', () => {
    const state = makeState({ isNight: true, hasShelter: false });
    const role = getRole('farmer')!;
    const { reflection, social } = makeBlocks();
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    expect(subgoal.id).toBe('emergency_shelter');
    expect(subgoal.action).toBe('find_or_build_shelter');
  });

  it('returns emergency_heal with critically low health', () => {
    const state = makeState({ health: 3 });
    const role = getRole('priest')!;
    const { reflection, social } = makeBlocks();
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    expect(subgoal.id).toBe('emergency_heal');
  });
});

describe('Planner – social horizon reactive goals', () => {
  it('reacts to zombie door breach message from neighbor', () => {
    const state = makeState();
    const role = getRole('guard')!;
    const { reflection } = makeBlocks();
    const social = new SocialMemory();
    social.ingest(['Alice: zombie broke my door down help']);
    const socialBlock = social.build();
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social: socialBlock, communityGoal: role.communityGoal });
    // Should react socially (craft_and_defend) before role-based planning
    expect(['social_react_craft_and_defend', 'social_react_assist_neighbor'].some(
      (id) => subgoal.id === id || subgoal.tag === 'social'
    )).toBe(true);
  });
});

describe('Planner – prompt context includes memory blocks', () => {
  it('buildSubgoalPrompt includes [Self-Reflection Block]', () => {
    const state = makeState();
    const role = getRole('farmer')!;
    const reflection = new ReflectionMemory().build();
    const social = new SocialMemory().build();
    const needs = computeNeeds(state);
    const prompt = buildSubgoalPrompt({
      state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal,
    });
    expect(prompt).toContain('[Self-Reflection Block]');
    expect(prompt).toContain('[Social Horizon Block]');
  });

  it('buildSubgoalPrompt includes agent name and community goal', () => {
    const state = makeState();
    const role = getRole('farmer')!;
    const reflection = new ReflectionMemory().build();
    const social = new SocialMemory().build();
    const needs = computeNeeds(state);
    const prompt = buildSubgoalPrompt({
      state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal,
    });
    expect(prompt).toContain('Mira');
    expect(prompt).toContain(role.communityGoal);
  });

  it('plannerOutput includes promptContext', () => {
    const state = makeState();
    const role = getRole('farmer')!;
    const { reflection, social } = makeBlocks();
    const needs = computeNeeds(state);
    const output = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    expect(output.promptContext).toBeTruthy();
    expect(typeof output.promptContext).toBe('string');
  });
});

describe('Planner – role-biased normal planning', () => {
  it('blacksmith prioritizes tools when none available', () => {
    const state = makeState({ hasTools: false });
    const role = getRole('blacksmith')!;
    const { reflection, social } = makeBlocks();
    const needs = computeNeeds(state);
    const { subgoal } = plan({ state, topNeeds: needs, role, reflection, social, communityGoal: role.communityGoal });
    // Blacksmith should prioritize tool crafting
    expect(subgoal.action).toBe('craft_tools');
  });
});
