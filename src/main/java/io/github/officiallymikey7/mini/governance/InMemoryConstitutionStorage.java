package io.github.officiallymikey7.mini.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default in-process storage – suitable for tests and single-agent use. */
public final class InMemoryConstitutionStorage implements ConstitutionStorage {

    private final Map<String, Rule>      rules      = new LinkedHashMap<>();
    private final Map<String, Amendment> amendments = new LinkedHashMap<>();

    @Override public List<Rule>      getRules()       { return new ArrayList<>(rules.values()); }
    @Override public void            saveRule(Rule r)  { rules.put(r.id, r); }
    @Override public void            deleteRule(String id) { rules.remove(id); }
    @Override public List<Amendment> getAmendments()  { return new ArrayList<>(amendments.values()); }
    @Override public void            saveAmendment(Amendment a) { amendments.put(a.id, a); }
}
