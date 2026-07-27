/**
 * __tests__/stallGuard.test.ts
 * Unit tests for safety/stallGuard.ts – stall detection and fallback behaviour.
 */
import { StallGuard } from '../safety/stallGuard';

describe('StallGuard – basic recording', () => {
  it('is not stalled before any attempts', () => {
    const guard = new StallGuard();
    expect(guard.isStalled('craft_tools')).toBe(false);
  });

  it('detects stall after maxAttempts failures', () => {
    const guard = new StallGuard({ maxAttempts: 3, timeoutMs: 60_000 });
    guard.recordAttempt('craft_tools');
    guard.recordAttempt('craft_tools');
    expect(guard.isStalled('craft_tools')).toBe(false);
    guard.recordAttempt('craft_tools');
    expect(guard.isStalled('craft_tools')).toBe(true);
  });

  it('clears stall on success', () => {
    const guard = new StallGuard({ maxAttempts: 2, timeoutMs: 60_000 });
    guard.recordAttempt('gather_food');
    guard.recordAttempt('gather_food');
    expect(guard.isStalled('gather_food')).toBe(true);
    guard.recordSuccess('gather_food');
    expect(guard.isStalled('gather_food')).toBe(false);
  });

  it('detects stall after timeout', () => {
    // Use a very short timeout
    const guard = new StallGuard({ maxAttempts: 100, timeoutMs: 0 });
    guard.recordAttempt('build_shelter');
    expect(guard.isStalled('build_shelter')).toBe(true);
  });
});

describe('StallGuard – fallback actions', () => {
  const guard = new StallGuard();

  it('returns prerequisite for craft_tools → gather_wood', () => {
    expect(guard.getFallbackAction('craft_tools')).toBe('gather_wood');
  });

  it('returns prerequisite for build_shelter → gather_wood', () => {
    expect(guard.getFallbackAction('build_shelter')).toBe('gather_wood');
  });

  it('returns explore for unknown subgoal', () => {
    expect(guard.getFallbackAction('unknown_exotic_action')).toBe('explore');
  });

  it('handles compound role subgoal ids (extracts last segment)', () => {
    // 'role_farmer_food' → last segment 'food' → not in map → 'explore'
    expect(guard.getFallbackAction('role_farmer_food')).toBe('explore');
  });
});

describe('StallGuard – reset', () => {
  it('clears all records on reset', () => {
    const guard = new StallGuard({ maxAttempts: 1, timeoutMs: 60_000 });
    guard.recordAttempt('gather_wood');
    guard.recordAttempt('eat_food');
    expect(guard.getRecords()).toHaveLength(2);
    guard.reset();
    expect(guard.getRecords()).toHaveLength(0);
  });
});
