/**
 * memory/reflection.ts
 * Builds the [Self-Reflection Block] – a human-readable summary of the
 * agent's recent outcomes, mistakes, and inventory changes.
 */
import type { ExecutionOutcome } from '../core/executor';
import type { InventoryItem } from '../core/perception';

export interface ReflectionEntry {
  tick: number;
  subgoalId: string;
  action: string;
  status: string;
  message: string;
  inventorySnapshot: InventoryItem[];
}

export interface ReflectionBlock {
  /** Plain-text summary appended to every planner prompt. */
  summary: string;
  entries: ReflectionEntry[];
}

const MAX_ENTRIES = 10;

export class ReflectionMemory {
  private entries: ReflectionEntry[] = [];
  private previousInventory: InventoryItem[] = [];

  /**
   * Record the outcome of one execution cycle.
   * @param tick             Current simulation tick.
   * @param outcome          Result from the executor.
   * @param currentInventory Agent's inventory at cycle end.
   */
  record(
    tick: number,
    outcome: ExecutionOutcome,
    currentInventory: InventoryItem[],
  ): void {
    this.entries.push({
      tick,
      subgoalId: outcome.subgoalId,
      action: outcome.action,
      status: outcome.status,
      message: outcome.message,
      inventorySnapshot: [...currentInventory],
    });

    // Keep only the most recent entries
    if (this.entries.length > MAX_ENTRIES) {
      this.entries = this.entries.slice(this.entries.length - MAX_ENTRIES);
    }

    this.previousInventory = [...currentInventory];
  }

  /** Build the [Self-Reflection Block] summary string. */
  build(): ReflectionBlock {
    if (this.entries.length === 0) {
      return {
        summary: 'No previous actions recorded yet.',
        entries: [],
      };
    }

    const lines: string[] = [];
    lines.push(`Recent actions (last ${this.entries.length}):`);

    for (const e of this.entries) {
      const icon = e.status === 'success' ? '✓' : e.status === 'failure' ? '✗' : '⚡';
      lines.push(`  [Tick ${e.tick}] ${icon} ${e.action} → ${e.status}: ${e.message}`);
    }

    // Compute inventory delta vs. previous snapshot
    const current = this.entries[this.entries.length - 1]?.inventorySnapshot ?? [];
    const prev = this.entries.length > 1
      ? this.entries[this.entries.length - 2]?.inventorySnapshot ?? []
      : [];

    const deltas = computeInventoryDelta(prev, current);
    if (deltas.length > 0) {
      lines.push(`Inventory changes since last tick: ${deltas.join(', ')}`);
    }

    // Highlight recent mistakes (failures or timeouts)
    const mistakes = this.entries.filter(
      (e) => e.status === 'failure' || e.status === 'timeout',
    );
    if (mistakes.length > 0) {
      const mistakeActions = [...new Set(mistakes.map((m) => m.action))].join(', ');
      lines.push(`Recent mistakes / failed actions: ${mistakeActions}`);
    }

    return { summary: lines.join('\n'), entries: [...this.entries] };
  }
}

// ── Helper ────────────────────────────────────────────────────────────────

function computeInventoryDelta(
  before: InventoryItem[],
  after: InventoryItem[],
): string[] {
  const beforeMap = new Map(before.map((i) => [i.name, i.count]));
  const afterMap = new Map(after.map((i) => [i.name, i.count]));
  const allKeys = new Set([...beforeMap.keys(), ...afterMap.keys()]);

  const deltas: string[] = [];
  for (const key of allKeys) {
    const was = beforeMap.get(key) ?? 0;
    const is = afterMap.get(key) ?? 0;
    const diff = is - was;
    if (diff > 0) deltas.push(`+${diff} ${key}`);
    else if (diff < 0) deltas.push(`${diff} ${key}`);
  }
  return deltas;
}
