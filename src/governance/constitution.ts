/**
 * governance/constitution.ts
 * Shared governance layer: agents can read rules, check for violations,
 * propose amendments, vote, and apply accepted changes.
 *
 * The storage back-end is abstracted behind ConstitutionStorage so the
 * system can be backed by in-memory data (default), a file, or a shared
 * document service.
 */

// ── Data shapes ────────────────────────────────────────────────────────────

export interface Rule {
  id: string;
  text: string;
  addedAt: number;
}

export interface Amendment {
  id: string;
  proposedBy: string;
  proposedAt: number;
  ruleId: string | null;   // null = new rule; existing id = replace rule
  proposedText: string;
  status: 'open' | 'accepted' | 'rejected';
  votes: Record<string, 'yes' | 'no'>;
}

// ── Storage interface ──────────────────────────────────────────────────────

/**
 * Abstract storage adapter.  Implement this interface to connect to any
 * backing store (in-memory, file, Google Doc, database, …).
 */
export interface ConstitutionStorage {
  getRules(): Promise<Rule[]>;
  saveRule(rule: Rule): Promise<void>;
  deleteRule(ruleId: string): Promise<void>;

  getAmendments(): Promise<Amendment[]>;
  saveAmendment(amendment: Amendment): Promise<void>;
}

// ── In-memory storage adapter (default) ────────────────────────────────────

export class InMemoryConstitutionStorage implements ConstitutionStorage {
  private rules: Map<string, Rule> = new Map();
  private amendments: Map<string, Amendment> = new Map();

  async getRules(): Promise<Rule[]> {
    return Array.from(this.rules.values());
  }

  async saveRule(rule: Rule): Promise<void> {
    this.rules.set(rule.id, rule);
  }

  async deleteRule(ruleId: string): Promise<void> {
    this.rules.delete(ruleId);
  }

  async getAmendments(): Promise<Amendment[]> {
    return Array.from(this.amendments.values());
  }

  async saveAmendment(amendment: Amendment): Promise<void> {
    this.amendments.set(amendment.id, amendment);
  }
}

// ── Document adapter stub ──────────────────────────────────────────────────

/**
 * Stub interface for an external document-backed storage adapter
 * (e.g. Google Docs).  Implement DocConstitutionStorage to connect a real
 * document service without changing Constitution logic.
 */
export interface DocConstitutionStorage extends ConstitutionStorage {
  /** Sync rules from the remote document into local state. */
  syncFromDoc(): Promise<void>;
  /** Push local changes back to the remote document. */
  flushToDoc(): Promise<void>;
}

// ── Constitution ────────────────────────────────────────────────────────────

export class Constitution {
  private storage: ConstitutionStorage;
  /** Fraction of yes-votes required to accept an amendment (default 0.5). */
  private acceptThreshold: number;

  constructor(storage?: ConstitutionStorage, acceptThreshold = 0.5) {
    this.storage = storage ?? new InMemoryConstitutionStorage();
    this.acceptThreshold = acceptThreshold;
  }

  // ── Rule access ───────────────────────────────────────────────────────────

  /** Returns all current rules. */
  async getRules(): Promise<Rule[]> {
    return this.storage.getRules();
  }

  // ── Violation checking ────────────────────────────────────────────────────

  /**
   * Evaluates whether an observed event text might violate any current rule.
   * Deterministic keyword-match approach so it can be tested without an LLM.
   * Uses prefix matching so inflected forms (e.g. "stole" matches "steal") are caught.
   *
   * @returns List of potentially violated rules (empty = no violations).
   */
  async checkViolations(observedEvent: string): Promise<Rule[]> {
    const rules = await this.storage.getRules();
    const eventWords = observedEvent
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, '')
      .split(/\s+/)
      .filter((w) => w.length > 3);

    // Flag a rule when a keyword is found in the event (substring) or shares
    // a 4-character prefix with an event word (handles simple inflections).
    return rules.filter((rule) => {
      const keywords = extractKeywords(rule.text);
      const eventLower = observedEvent.toLowerCase().replace(/[^a-z0-9\s]/g, '');
      return keywords.some((kw) => {
        // Direct substring match (e.g. "steal" in "no stealing allowed")
        if (eventLower.includes(kw)) return true;
        // Prefix match on individual words for common inflections
        const stem = kw.slice(0, 4);
        return eventLower.split(/\s+/).some((ew) => ew.startsWith(stem));
      });
    });
  }

  // ── Proposals ────────────────────────────────────────────────────────────

  /**
   * Proposes an amendment to an existing rule (ruleId != null) or a new rule.
   * @returns The created Amendment.
   */
  async proposeAmendment(
    proposedBy: string,
    proposedText: string,
    ruleId: string | null = null,
  ): Promise<Amendment> {
    const amendment: Amendment = {
      id: `amend_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      proposedBy,
      proposedAt: Date.now(),
      ruleId,
      proposedText,
      status: 'open',
      votes: {},
    };
    await this.storage.saveAmendment(amendment);
    return amendment;
  }

  // ── Voting ────────────────────────────────────────────────────────────────

  /**
   * Cast a vote on an open amendment.
   * Each agent may vote once; subsequent calls overwrite the previous vote.
   *
   * @returns Updated amendment.
   */
  async vote(
    amendmentId: string,
    agentName: string,
    decision: 'yes' | 'no',
  ): Promise<Amendment> {
    const amendments = await this.storage.getAmendments();
    const amendment = amendments.find((a) => a.id === amendmentId);
    if (!amendment) {
      throw new Error(`Amendment ${amendmentId} not found`);
    }
    if (amendment.status !== 'open') {
      throw new Error(`Amendment ${amendmentId} is already ${amendment.status}`);
    }

    amendment.votes[agentName] = decision;
    await this.storage.saveAmendment(amendment);
    return amendment;
  }

  // ── Tallying & applying ───────────────────────────────────────────────────

  /**
   * Tallies votes and applies the amendment if the accept threshold is met.
   * Marks the amendment as 'accepted' or 'rejected' accordingly.
   *
   * @param totalVoters  Total number of eligible voters (for quorum calculation).
   * @returns Updated amendment.
   */
  async tally(amendmentId: string, totalVoters: number): Promise<Amendment> {
    const amendments = await this.storage.getAmendments();
    const amendment = amendments.find((a) => a.id === amendmentId);
    if (!amendment) {
      throw new Error(`Amendment ${amendmentId} not found`);
    }
    if (amendment.status !== 'open') {
      return amendment; // already resolved
    }

    const yesCount = Object.values(amendment.votes).filter((v) => v === 'yes').length;
    const ratio = totalVoters > 0 ? yesCount / totalVoters : 0;

    if (ratio > this.acceptThreshold) {
      amendment.status = 'accepted';
      await this.applyAmendment(amendment);
    } else {
      amendment.status = 'rejected';
    }

    await this.storage.saveAmendment(amendment);
    return amendment;
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  private async applyAmendment(amendment: Amendment): Promise<void> {
    if (amendment.ruleId) {
      // Replace existing rule
      await this.storage.deleteRule(amendment.ruleId);
    }
    const newRule: Rule = {
      id: amendment.ruleId ?? `rule_${Date.now()}`,
      text: amendment.proposedText,
      addedAt: Date.now(),
    };
    await this.storage.saveRule(newRule);
  }
}

// ── Helpers ────────────────────────────────────────────────────────────────

/**
 * Extracts simple keyword tokens from a rule text for violation matching.
 * Ignores common stop-words.
 */
function extractKeywords(text: string): string[] {
  const stopWords = new Set([
    'a', 'an', 'the', 'is', 'are', 'must', 'shall', 'not', 'no', 'and', 'or',
    'to', 'be', 'it', 'in', 'at', 'of', 'for', 'all', 'any', 'agents', 'agent',
  ]);
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, '')
    .split(/\s+/)
    .filter((w) => w.length > 3 && !stopWords.has(w));
}
