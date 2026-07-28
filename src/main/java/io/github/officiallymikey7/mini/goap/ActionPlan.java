package io.github.officiallymikey7.mini.goap;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * An ordered sequence of concrete action strings resolved by {@link GOAPPlanner#buildPlan}.
 *
 * <p>Actions are consumed one per brain tick via {@link #getNextAction()}.  When the
 * plan is exhausted, {@link #hasNextAction()} returns {@code false} and the planner
 * should be re-invoked on the next tick to select a fresh goal.
 *
 * <p>Example plan for {@code GEAR_UP → iron pickaxe}:
 * <pre>
 *   gather_wood → craft_planks → craft_sticks → mine_stone → mine_iron → craft_iron_pickaxe
 * </pre>
 */
public final class ActionPlan {

    private final List<String> actions;
    private final Iterator<String> cursor;

    /** The goal type this plan was built for (informational). */
    public final GoalScore goal;

    public ActionPlan(GoalScore goal, List<String> actions) {
        this.goal    = goal;
        this.actions = List.copyOf(actions);
        this.cursor  = this.actions.iterator();
    }

    /** Returns {@code true} when at least one unexecuted action remains. */
    public boolean hasNextAction() {
        return cursor.hasNext();
    }

    /**
     * Returns the next action string and advances the cursor.
     *
     * @throws java.util.NoSuchElementException if no actions remain
     */
    public String getNextAction() {
        return cursor.next();
    }

    /** Returns an unmodifiable view of all actions in this plan. */
    public List<String> allActions() {
        return Collections.unmodifiableList(actions);
    }

    /** Returns the number of actions in this plan. */
    public int size() {
        return actions.size();
    }

    /** Returns an empty plan (no goal active). */
    public static ActionPlan empty() {
        return new ActionPlan(new GoalScore(
                io.github.officiallymikey7.mini.core.NeedType.EXPLORE, 0.0, "idle"), List.of());
    }
}
