package io.github.officiallymikey7.mini.governance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared governance layer: agents can read rules, check for violations,
 * propose amendments, vote, and apply accepted changes.
 *
 * <p>The storage back-end is abstracted behind {@link ConstitutionStorage} so
 * the system can be backed by in-memory data (default), a file, or any shared
 * document service.
 */
public final class Constitution {

    private final ConstitutionStorage storage;
    /** Fraction of yes-votes required to accept an amendment (default 0.5). */
    private final double acceptThreshold;

    public Constitution() {
        this(new InMemoryConstitutionStorage(), 0.5);
    }

    public Constitution(ConstitutionStorage storage) {
        this(storage, 0.5);
    }

    public Constitution(ConstitutionStorage storage, double acceptThreshold) {
        this.storage         = storage;
        this.acceptThreshold = acceptThreshold;
    }

    // ── Rule access ──────────────────────────────────────────────────────────

    /** Returns all currently active rules. */
    public List<Rule> getRules() {
        return storage.getRules();
    }

    // ── Violation checking ───────────────────────────────────────────────────

    /**
     * Deterministic keyword-match check for potential rule violations.
     * Uses prefix matching so inflected forms (e.g. "stole" matches "steal") are caught.
     *
     * @return Rules that the observed event may be violating (empty = clean).
     */
    public List<Rule> checkViolations(String observedEvent) {
        String eventLower = observedEvent.toLowerCase().replaceAll("[^a-z0-9\\s]", "");
        List<Rule> violated = new ArrayList<>();
        for (Rule rule : storage.getRules()) {
            List<String> keywords = extractKeywords(rule.text);
            boolean matches = false;
            for (String kw : keywords) {
                if (eventLower.contains(kw)) { matches = true; break; }
                // Prefix match for common inflections (stem length 4)
                if (kw.length() >= 4) {
                    String stem = kw.substring(0, 4);
                    if (Arrays.stream(eventLower.split("\\s+")).anyMatch(w -> w.startsWith(stem))) {
                        matches = true;
                        break;
                    }
                }
            }
            if (matches) violated.add(rule);
        }
        return violated;
    }

    // ── Proposals ────────────────────────────────────────────────────────────

    /** Propose a brand-new rule. */
    public Amendment proposeAmendment(String proposedBy, String proposedText) {
        return proposeAmendment(proposedBy, proposedText, null);
    }

    /**
     * Propose an amendment.
     *
     * @param ruleId {@code null} for a new rule; existing rule ID to replace.
     */
    public Amendment proposeAmendment(String proposedBy, String proposedText, String ruleId) {
        String id = "amend_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        Amendment amendment = new Amendment(id, proposedBy, System.currentTimeMillis(),
                ruleId, proposedText);
        storage.saveAmendment(amendment);
        return amendment;
    }

    // ── Voting ────────────────────────────────────────────────────────────────

    /**
     * Cast or update a vote on an open amendment.
     * Each agent may vote once; subsequent calls overwrite the previous vote.
     */
    public Amendment vote(String amendmentId, String agentName, String decision) {
        Amendment amendment = findAmendment(amendmentId);
        if (amendment.status != AmendmentStatus.OPEN) {
            throw new IllegalStateException(
                    "Amendment " + amendmentId + " is already " + amendment.status);
        }
        amendment.votes.put(agentName, decision);
        storage.saveAmendment(amendment);
        return amendment;
    }

    // ── Tallying ─────────────────────────────────────────────────────────────

    /**
     * Tallies votes and applies the amendment if the accept threshold is met.
     *
     * @param totalVoters Total eligible voters (used for ratio calculation).
     */
    public Amendment tally(String amendmentId, int totalVoters) {
        Amendment amendment = findAmendment(amendmentId);
        if (amendment.status != AmendmentStatus.OPEN) return amendment;

        long yesCount = amendment.votes.values().stream().filter("yes"::equals).count();
        double ratio  = totalVoters > 0 ? (double) yesCount / totalVoters : 0;

        if (ratio > acceptThreshold) {
            amendment.status = AmendmentStatus.ACCEPTED;
            applyAmendment(amendment);
        } else {
            amendment.status = AmendmentStatus.REJECTED;
        }
        storage.saveAmendment(amendment);
        return amendment;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private void applyAmendment(Amendment amendment) {
        if (amendment.ruleId != null) storage.deleteRule(amendment.ruleId);
        String ruleId = amendment.ruleId != null ? amendment.ruleId : "rule_" + System.currentTimeMillis();
        storage.saveRule(new Rule(ruleId, amendment.proposedText, System.currentTimeMillis()));
    }

    private Amendment findAmendment(String id) {
        return storage.getAmendments().stream()
                .filter(a -> a.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Amendment not found: " + id));
    }

    // Common stop-words excluded from keyword extraction
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "must", "shall", "not", "no", "and", "or",
            "to", "be", "it", "in", "at", "of", "for", "all", "any", "agents", "agent"));

    private static List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        for (String word : text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+")) {
            if (word.length() > 3 && !STOP_WORDS.contains(word)) keywords.add(word);
        }
        return keywords;
    }
}
