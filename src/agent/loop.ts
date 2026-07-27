/**
 * agent/loop.ts
 * Main agent tick loop.
 *
 * Each tick:
 *   1. Perceive world state
 *   2. Compute need priorities
 *   3. Check for emergencies
 *   4. Run stall guard (force replan if stalled)
 *   5. Plan next subgoal (with reflection + social context)
 *   6. Execute subgoal action
 *   7. Record memory
 *   8. Occasionally run governance checks
 */
import * as dotenv from 'dotenv';
dotenv.config();

import { perceive } from '../core/perception';
import { computeNeeds } from '../core/needs';
import { plan } from '../core/planner';
import { execute } from '../core/executor';
import { StallGuard } from '../safety/stallGuard';
import { ReflectionMemory } from '../memory/reflection';
import { SocialMemory } from '../memory/social';
import { getRole, registerRole } from '../roles/registry';
import { Constitution, InMemoryConstitutionStorage } from '../governance/constitution';
import type { BotAdapter } from '../integration/botAdapter';
import type { RoleDefinition } from '../roles/registry';
import type { WorldState } from '../core/perception';

export interface AgentConfig {
  name: string;
  roleId: string;
  /** Tick interval in milliseconds. */
  tickIntervalMs?: number;
  /** Run governance checks every N ticks. */
  governanceTickInterval?: number;
  /** Custom role definition (overrides registry lookup). */
  customRole?: RoleDefinition;
}

export interface AgentHooks {
  /** Called before each tick with the current world state. */
  onTickStart?: (tick: number, state: WorldState) => void;
  /** Called after each tick with the execution outcome message. */
  onTickEnd?: (tick: number, actionResult: string) => void;
  /** Called when the agent logs a message. */
  onLog?: (level: 'info' | 'warn' | 'error', message: string) => void;
}

export class Agent {
  private config: AgentConfig;
  private adapter: BotAdapter;
  private hooks: AgentHooks;

  private stallGuard: StallGuard;
  private reflection: ReflectionMemory;
  private social: SocialMemory;
  private constitution: Constitution;

  private tickCount = 0;
  private running = false;
  private latestState: WorldState | null = null;

  constructor(
    adapter: BotAdapter,
    config: AgentConfig,
    hooks: AgentHooks = {},
  ) {
    this.adapter = adapter;
    this.config = {
      tickIntervalMs: 5000,
      governanceTickInterval: 10,
      ...config,
    };
    this.hooks = hooks;
    this.stallGuard = new StallGuard();
    this.reflection = new ReflectionMemory();
    this.social = new SocialMemory();
    this.constitution = new Constitution(new InMemoryConstitutionStorage());

    // Seed with a starter rule
    void this.constitution.proposeAmendment(
      'system',
      'Agents must not steal resources from other agents without consent.',
    ).then((a) => this.constitution.tally(a.id, 1));
  }

  /** Start the tick loop (non-blocking – returns immediately). */
  start(): void {
    if (this.running) return;
    this.running = true;
    this.log('info', `Agent ${this.config.name} starting (role: ${this.config.roleId})`);
    void this.loop();
  }

  /** Stop the tick loop gracefully. */
  stop(): void {
    this.running = false;
    this.log('info', `Agent ${this.config.name} stopped after ${this.tickCount} ticks.`);
  }

  /** Returns the last perceived world state. */
  getLatestState(): WorldState | null {
    return this.latestState;
  }

  // ── Main loop ──────────────────────────────────────────────────────────────

  private async loop(): Promise<void> {
    while (this.running) {
      try {
        await this.tick();
      } catch (err) {
        this.log('error', `Tick ${this.tickCount} error: ${String(err)}`);
      }

      await sleep(this.config.tickIntervalMs ?? 5000);
    }
  }

  async tick(): Promise<void> {
    this.tickCount++;
    const tick = this.tickCount;

    // ── Step 1: Perceive ──────────────────────────────────────────────────
    const state = await perceive(this.adapter);
    this.latestState = state;
    this.hooks.onTickStart?.(tick, state);

    // ── Step 2: Ingest social messages ────────────────────────────────────
    this.social.ingest(state.nearbyChat, 'nearby');

    // ── Step 3: Compute needs ─────────────────────────────────────────────
    const topNeeds = computeNeeds(state);

    // ── Step 4: Resolve role ──────────────────────────────────────────────
    const role = this.config.customRole
      ?? getRole(this.config.roleId)
      ?? getRole('farmer')!;

    // ── Step 5: Build memory blocks ───────────────────────────────────────
    const reflectionBlock = this.reflection.build();
    const socialBlock = this.social.build();

    // ── Step 6: Plan ──────────────────────────────────────────────────────
    let { subgoal } = plan({
      state,
      topNeeds,
      role,
      reflection: reflectionBlock,
      social: socialBlock,
      communityGoal: role.communityGoal,
    });

    // ── Step 7: Stall guard ───────────────────────────────────────────────
    if (this.stallGuard.isStalled(subgoal.id)) {
      const fallback = this.stallGuard.getFallbackAction(subgoal.id);
      this.log('warn', `Stall detected on "${subgoal.id}" → falling back to: ${fallback}`);
      this.stallGuard.reset();
      subgoal = {
        id: `fallback_${fallback}`,
        description: `Fallback: ${fallback}`,
        action: fallback,
        tag: 'survival',
        priority: subgoal.priority,
      };
    }

    this.stallGuard.recordAttempt(subgoal.id);
    this.log('info', `[Tick ${tick}] Subgoal: ${subgoal.description} → action: ${subgoal.action}`);

    // ── Step 8: Execute ───────────────────────────────────────────────────
    const outcome = await execute(
      this.adapter,
      subgoal,
      () => this.latestState ?? state,
    );

    const resultMsg = `${outcome.status.toUpperCase()}: ${outcome.message}`;
    this.hooks.onTickEnd?.(tick, resultMsg);

    // ── Step 9: Update stall guard on success ─────────────────────────────
    if (outcome.status === 'success') {
      this.stallGuard.recordSuccess(subgoal.id);
    }

    // ── Step 10: Record memory ────────────────────────────────────────────
    const endState = await perceive(this.adapter);
    this.latestState = endState;
    this.reflection.record(tick, outcome, endState.inventory);

    // ── Step 11: Governance check (periodic) ──────────────────────────────
    const govInterval = this.config.governanceTickInterval ?? 10;
    if (tick % govInterval === 0) {
      await this.runGovernanceCheck(state);
    }
  }

  // ── Governance ─────────────────────────────────────────────────────────────

  private async runGovernanceCheck(state: WorldState): Promise<void> {
    // Check each recent chat message for potential rule violations
    for (const msg of state.nearbyChat) {
      const violations = await this.constitution.checkViolations(msg);
      if (violations.length > 0) {
        const ruleTexts = violations.map((r) => `"${r.text}"`).join(', ');
        this.log('warn', `Possible rule violation detected: "${msg}" → rules: ${ruleTexts}`);
      }
    }
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  private log(level: 'info' | 'warn' | 'error', message: string): void {
    if (this.hooks.onLog) {
      this.hooks.onLog(level, message);
    } else {
      const tag = level.toUpperCase().padEnd(5);
      console.log(`[${tag}] ${message}`);
    }
  }
}

// ── Utility ────────────────────────────────────────────────────────────────

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// ── Demo entry-point ───────────────────────────────────────────────────────

/** Run a quick simulation demo using the mock adapter. */
async function runDemo(): Promise<void> {
  const { MockBotAdapter } = await import('../integration/botAdapter');

  const adapter = new MockBotAdapter({
    agentName: 'Arlo',
    gameTick: 14000, // Night
    health: 18,
    hunger: 8,
    hasShelter: false,
    shelterDistance: 30,
    nearbyHostiles: [],
    nearbyChat: [],
  });

  const agent = new Agent(adapter, {
    name: 'Arlo',
    roleId: 'farmer',
    tickIntervalMs: 200,
    governanceTickInterval: 3,
  });

  console.log('=== Minecraft Agent Demo ===');
  console.log('Running 5 ticks…\n');

  agent.start();

  await sleep(1200); // Run 5 ticks @ 200ms each
  agent.stop();

  console.log('\nAction log:');
  adapter.actionLog.forEach(({ action, result }) => {
    console.log(`  • ${action}: ${result}`);
  });
}

// Run demo if executed directly
if (require.main === module) {
  runDemo().catch(console.error);
}

// Export for programmatic use
export { registerRole };
