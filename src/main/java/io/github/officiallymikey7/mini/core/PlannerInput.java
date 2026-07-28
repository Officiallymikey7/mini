package io.github.officiallymikey7.mini.core;

import io.github.officiallymikey7.mini.memory.ReflectionBlock;
import io.github.officiallymikey7.mini.memory.SocialBlock;
import io.github.officiallymikey7.mini.roles.RoleDefinition;

import java.util.List;

/** All inputs consumed by the planner in one tick. */
public final class PlannerInput {
    public final WorldState state;
    public final List<NeedScore> topNeeds;
    public final RoleDefinition role;
    public final ReflectionBlock reflection;
    public final SocialBlock social;
    public final String communityGoal;

    public PlannerInput(WorldState state, List<NeedScore> topNeeds,
                        RoleDefinition role, ReflectionBlock reflection,
                        SocialBlock social, String communityGoal) {
        this.state = state;
        this.topNeeds = List.copyOf(topNeeds);
        this.role = role;
        this.reflection = reflection;
        this.social = social;
        this.communityGoal = communityGoal;
    }
}
