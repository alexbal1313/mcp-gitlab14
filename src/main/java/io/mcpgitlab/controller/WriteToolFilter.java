package io.mcpgitlab.controller;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Hides write tools when {@code gitlab.write.enabled=false}.
 * Read tools are not affected.
 */
@Singleton
public class WriteToolFilter implements ToolFilter {

    @ConfigProperty(name = "gitlab.write.enabled", defaultValue = "false")
    boolean writeEnabled;

    @Override
    public boolean test(ToolInfo tool, FilterContext context) {
        return isVisible(tool.name());
    }

    boolean isVisible(String toolName) {
        return writeEnabled || !McpWriteTools.NAMES.contains(toolName);
    }
}
