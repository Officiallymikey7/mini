/**
 * safety/stallGuard.ts
 * Detects "goal obsession" / stalling: when the agent repeatedly fails the
 * same subgoal or exceeds a time budget without progress.
 *
 * On stall detection the guard forces replanning to a prerequisite or
 * alternative subgoal rather than retrying the blocked action forever.
 */

export interface StallRecord {
  subgoalId: string;
  attempts: number;
  firstAttemptAt: number;
  lastAttemptAt: number;
}

export interface StallGuardConfig {
  /** Maximum consecutive failures before a stall is declared. */
  maxAttempts: number;
  /** Maximum elapsed time (ms) on the same subgoal before a stall is declared. */
  timeoutMs: number;
}

const DEFAULT_CONFIG: StallGuardConfig = {
  maxAttempts: 3,
  timeoutMs: 60_000, // 1 minute
};

/** Maps subgoalId → prerequisite action to fall back to on stall. */
const PREREQUISITE_MAP: Record<string, string> = {
  craft_tools: 'gather_wood',           // Can't craft without wood
  build_shelter: 'gather_wood',         // Can't build without materials
  craft_sword_or_flee: 'gather_wood',   // Can't craft without materials
  gather_food: 'find_water_source',     // Try alternate food strategy
  eat_food: 'forage_food',             // Fallback to foraging
  craft_and_defend: 'flee_to_shelter', // If we can't craft, flee
  find_or_build_shelter: 'gather_wood', // Need materials first
};

export class StallGuard {
  private records = new Map<string, StallRecord>();
  private config: StallGuardConfig;

  constructor(config: Partial<StallGuardConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Record an attempt on the given subgoal id.
   * Call this every time the agent tries to execute a subgoal.
   */
  recordAttempt(subgoalId: string): void {
    const existing = this.records.get(subgoalId);
    if (existing) {
      existing.attempts += 1;
      existing.lastAttemptAt = Date.now();
    } else {
      this.records.set(subgoalId, {
        subgoalId,
        attempts: 1,
        firstAttemptAt: Date.now(),
        lastAttemptAt: Date.now(),
      });
    }
  }

  /**
   * Record that the given subgoal succeeded and clear its stall record.
   */
  recordSuccess(subgoalId: string): void {
    this.records.delete(subgoalId);
  }

  /**
   * Returns true if the given subgoal should be considered stalled.
   */
  isStalled(subgoalId: string): boolean {
    const record = this.records.get(subgoalId);
    if (!record) return false;

    const elapsed = Date.now() - record.firstAttemptAt;
    return record.attempts >= this.config.maxAttempts || elapsed >= this.config.timeoutMs;
  }

  /**
   * Returns the fallback action to attempt when the current subgoal is stalled.
   * Falls back to 'explore' if no specific prerequisite is mapped.
   */
  getFallbackAction(subgoalId: string): string {
    // Extract the base action from compound subgoal ids (e.g. 'role_farmer_food')
    const action = subgoalId.includes('_') ? subgoalId.split('_').slice(-1)[0] : subgoalId;
    return PREREQUISITE_MAP[action] ?? PREREQUISITE_MAP[subgoalId] ?? 'explore';
  }

  /**
   * Returns all current stall records (useful for debugging / logging).
   */
  getRecords(): StallRecord[] {
    return Array.from(this.records.values());
  }

  /**
   * Clears all stall records (e.g. after a full replan).
   */
  reset(): void {
    this.records.clear();
  }
}
