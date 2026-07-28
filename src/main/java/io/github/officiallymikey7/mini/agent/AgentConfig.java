package io.github.officiallymikey7.mini.agent;

import io.github.officiallymikey7.mini.roles.RoleDefinition;

/**
 * Configuration for a single agent instance.
 */
public final class AgentConfig {
    public final String name;
    public final String roleId;
    /** Run governance checks every N ticks (default 10). */
    public final int governanceTickInterval;
    /** Optional inline role definition that overrides the registry look-up. */
    public final RoleDefinition customRole;

    private AgentConfig(Builder b) {
        this.name                   = b.name;
        this.roleId                 = b.roleId;
        this.governanceTickInterval = b.governanceTickInterval;
        this.customRole             = b.customRole;
    }

    public static Builder builder(String name, String roleId) {
        return new Builder(name, roleId);
    }

    public static final class Builder {
        private final String name;
        private final String roleId;
        private int            governanceTickInterval = 10;
        private RoleDefinition customRole             = null;

        private Builder(String name, String roleId) {
            this.name   = name;
            this.roleId = roleId;
        }

        public Builder governanceTickInterval(int n) { this.governanceTickInterval = n; return this; }
        public Builder customRole(RoleDefinition r)  { this.customRole = r; return this; }
        public AgentConfig build()                   { return new AgentConfig(this); }
    }
}
