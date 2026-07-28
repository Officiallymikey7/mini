package io.github.officiallymikey7.mini.roles;

import io.github.officiallymikey7.mini.core.NeedType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines an agent role with a community goal, need-weight multipliers, and
 * personality traits.
 *
 * <p>Need weights &gt; 1.0 boost the need's urgency; &lt; 1.0 reduce it.
 * Emergency survival needs (score ≥ 75) are never reduced below their threshold.
 *
 * <p>Use {@link #builder(String)} to construct an instance.
 */
public final class RoleDefinition {

    private final String id;
    private final String label;
    private final String communityGoal;
    private final Map<NeedType, Double> needWeights;
    private final List<String> traits;

    private RoleDefinition(Builder b) {
        this.id            = b.id;
        this.label         = b.label;
        this.communityGoal = b.communityGoal;
        this.needWeights   = Map.copyOf(b.needWeights);
        this.traits        = List.copyOf(b.traits);
    }

    public String getId()            { return id; }
    public String getLabel()         { return label; }
    public String getCommunityGoal() { return communityGoal; }
    public Map<NeedType, Double> getNeedWeights() { return needWeights; }
    public List<String> getTraits()  { return traits; }

    public static Builder builder(String id) { return new Builder(id); }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private final String id;
        private String label         = "";
        private String communityGoal = "";
        private final Map<NeedType, Double> needWeights = new HashMap<>();
        private List<String> traits  = List.of();

        private Builder(String id) { this.id = id; }

        public Builder label(String label)               { this.label = label; return this; }
        public Builder communityGoal(String goal)        { this.communityGoal = goal; return this; }
        public Builder needWeight(NeedType t, double w)  { needWeights.put(t, w); return this; }
        public Builder traits(String... traits)          { this.traits = List.of(traits); return this; }
        public RoleDefinition build()                    { return new RoleDefinition(this); }
    }
}
