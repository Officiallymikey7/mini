package io.github.officiallymikey7.mini.governance;

import java.util.List;

/**
 * Pluggable storage back-end for constitution rules and amendments.
 *
 * <p>Implement this interface to connect the {@link Constitution} to any
 * backing store (in-memory, file, database, shared document, …).
 */
public interface ConstitutionStorage {
    List<Rule>      getRules();
    void            saveRule(Rule rule);
    void            deleteRule(String ruleId);
    List<Amendment> getAmendments();
    void            saveAmendment(Amendment amendment);
}
