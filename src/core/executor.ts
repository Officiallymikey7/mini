/**
 * core/executor.ts
 * Executes the selected action via the bot adapter and returns an outcome.
 * Supports interruption by higher-priority emergency events detected mid-action.
 */
import type { BotAdapter } from '../integration/botAdapter';
import type { Subgoal } from './planner';
import type { WorldState } from './perception';

export type ExecutionStatus = 'success' | 'failure' | 'interrupted' | 'timeout';

export interface ExecutionOutcome {
  subgoalId: string;
  action: string;
  status: ExecutionStatus;
  message: string;
  durationMs: number;
  /** State observed at the END of execution (may differ from start state). */
  endState?: Partial<WorldState>;
}

/** Maximum time allowed for a single action before it is forcibly timed out (ms). */
const DEFAULT_ACTION_TIMEOUT_MS = 15_000;

/**
 * Executes a subgoal action.
 *
 * The executor polls for emergencies while the action runs and interrupts
 * immediately if conditions become life-threatening.
 *
 * @param adapter   Bot adapter for world interaction.
 * @param subgoal   The subgoal to execute.
 * @param getState  Callback that returns the latest world state (used for interruption checks).
 * @param timeoutMs Optional per-action timeout in ms.
 */
export async function execute(
  adapter: BotAdapter,
  subgoal: Subgoal,
  getState: () => WorldState,
  timeoutMs: number = DEFAULT_ACTION_TIMEOUT_MS,
): Promise<ExecutionOutcome> {
  const start = Date.now();

  const actionPromise = adapter.performAction(subgoal.action);

  // Race the action against a timeout and an emergency interrupt poller
  const result = await Promise.race([
    actionPromise.then((msg) => ({ kind: 'done' as const, msg })),
    timeout(timeoutMs).then(() => ({ kind: 'timeout' as const })),
    pollEmergency(getState, 500).then(() => ({ kind: 'interrupt' as const })),
  ]);

  const durationMs = Date.now() - start;

  if (result.kind === 'timeout') {
    return {
      subgoalId: subgoal.id,
      action: subgoal.action,
      status: 'timeout',
      message: `Action timed out after ${timeoutMs}ms`,
      durationMs,
    };
  }

  if (result.kind === 'interrupt') {
    return {
      subgoalId: subgoal.id,
      action: subgoal.action,
      status: 'interrupted',
      message: 'Action interrupted by higher-priority emergency',
      durationMs,
    };
  }

  // 'done' – check if the action reported an error
  const success = !result.msg.toLowerCase().startsWith('error:');
  return {
    subgoalId: subgoal.id,
    action: subgoal.action,
    status: success ? 'success' : 'failure',
    message: result.msg,
    durationMs,
  };
}

// ── Helpers ───────────────────────────────────────────────────────────────

function timeout(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Resolves when an emergency condition is detected in the current state.
 * Polls at the given interval so it doesn't busy-loop.
 */
async function pollEmergency(
  getState: () => WorldState,
  intervalMs: number,
): Promise<void> {
  return new Promise((resolve) => {
    const check = () => {
      const state = getState();
      const isEmergency =
        state.health < 4 ||
        (state.nearbyHostiles.length > 0 &&
          Math.min(...state.nearbyHostiles.map((h) => h.distance)) < 5);
      if (isEmergency) {
        resolve();
      } else {
        setTimeout(check, intervalMs);
      }
    };
    // Start first check after one interval to give the action a chance to begin
    setTimeout(check, intervalMs);
  });
}
