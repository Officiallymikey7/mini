/**
 * __tests__/governance.test.ts
 * Tests for governance/constitution.ts – voting, amendments, and violations.
 */
import {
  Constitution,
  InMemoryConstitutionStorage,
} from '../governance/constitution';

function makeConstitution() {
  return new Constitution(new InMemoryConstitutionStorage(), 0.5);
}

describe('Constitution – rules', () => {
  it('starts with no rules', async () => {
    const c = makeConstitution();
    expect(await c.getRules()).toHaveLength(0);
  });

  it('adds a rule after amendment is accepted', async () => {
    const c = makeConstitution();
    const amendment = await c.proposeAmendment(
      'agentA',
      'Agents must not steal resources from others.',
    );
    await c.vote(amendment.id, 'agentA', 'yes');
    await c.vote(amendment.id, 'agentB', 'yes');
    const result = await c.tally(amendment.id, 2);
    expect(result.status).toBe('accepted');
    const rules = await c.getRules();
    expect(rules).toHaveLength(1);
    expect(rules[0].text).toContain('steal');
  });
});

describe('Constitution – voting flow', () => {
  it('rejects when yes-votes are insufficient', async () => {
    const c = makeConstitution();
    const amendment = await c.proposeAmendment(
      'agentA',
      'Agents must share food with the community.',
    );
    await c.vote(amendment.id, 'agentA', 'yes');
    // 1 yes out of 4 voters = 25%, below 50% threshold
    const result = await c.tally(amendment.id, 4);
    expect(result.status).toBe('rejected');
    expect(await c.getRules()).toHaveLength(0);
  });

  it('does not double-apply when tally called twice', async () => {
    const c = makeConstitution();
    const amendment = await c.proposeAmendment('agentA', 'All agents must greet newcomers.');
    await c.vote(amendment.id, 'agentA', 'yes');
    await c.vote(amendment.id, 'agentB', 'yes');
    await c.tally(amendment.id, 2);
    await c.tally(amendment.id, 2); // second call – already accepted
    const rules = await c.getRules();
    expect(rules).toHaveLength(1); // should NOT add duplicate
  });

  it('overwrites an existing vote', async () => {
    const c = makeConstitution();
    const amendment = await c.proposeAmendment('agentA', 'Test rule.');
    await c.vote(amendment.id, 'agentA', 'no');
    await c.vote(amendment.id, 'agentA', 'yes'); // change mind
    // 1 yes out of 1 voter = 100%
    const result = await c.tally(amendment.id, 1);
    expect(result.status).toBe('accepted');
  });

  it('throws when voting on a non-existent amendment', async () => {
    const c = makeConstitution();
    await expect(c.vote('fake_id', 'agentX', 'yes')).rejects.toThrow();
  });
});

describe('Constitution – violation checking', () => {
  it('detects violation matching a rule keyword', async () => {
    const c = makeConstitution();
    const amendment = await c.proposeAmendment('agentA', 'Agents must not steal resources.');
    await c.vote(amendment.id, 'agentA', 'yes');
    await c.tally(amendment.id, 1);

    const violations = await c.checkViolations('Player was caught stealing items from chest');
    expect(violations.length).toBeGreaterThan(0);
  });

  it('returns empty when no rule is violated', async () => {
    const c = makeConstitution();
    const amendment = await c.proposeAmendment('agentA', 'Agents must not steal resources.');
    await c.vote(amendment.id, 'agentA', 'yes');
    await c.tally(amendment.id, 1);

    const violations = await c.checkViolations('Player planted wheat seeds.');
    expect(violations).toHaveLength(0);
  });
});

describe('Constitution – amendment replaces rule', () => {
  it('updated rule replaces old rule text', async () => {
    const c = makeConstitution();

    // Create initial rule
    const a1 = await c.proposeAmendment('agentA', 'Old rule text.');
    await c.vote(a1.id, 'agentA', 'yes');
    const a1result = await c.tally(a1.id, 1);
    const ruleId = (await c.getRules())[0].id;

    // Propose amendment to replace that rule
    const a2 = await c.proposeAmendment('agentA', 'New and improved rule text.', ruleId);
    await c.vote(a2.id, 'agentA', 'yes');
    await c.tally(a2.id, 1);

    const rules = await c.getRules();
    expect(rules).toHaveLength(1);
    expect(rules[0].text).toBe('New and improved rule text.');
  });
});
