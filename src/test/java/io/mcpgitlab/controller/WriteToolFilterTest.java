package io.mcpgitlab.controller;

import io.quarkiverse.mcp.server.Tool;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Write flag: the filter and completeness of the write tool name set.
 */
class WriteToolFilterTest {

    private static Set<String> toolNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(tool -> tool != null)
                .map(Tool::name)
                .collect(Collectors.toSet());
    }

    @Test
    void namesMatchWriteToolsClass() {
        assertEquals(McpWriteTools.NAMES, toolNames(McpWriteTools.class));
        assertEquals(15, McpWriteTools.NAMES.size());
    }

    @Test
    void readToolsAreNotWriteNames() {
        Set<String> read = toolNames(McpTools.class);
        assertEquals(34, read.size());
        assertTrue(read.stream().noneMatch(McpWriteTools.NAMES::contains));
    }

    @Test
    void disabledHidesOnlyWriteTools() {
        WriteToolFilter filter = new WriteToolFilter();
        filter.writeEnabled = false;
        assertFalse(filter.isVisible("gitlab_create_issue"));
        assertFalse(filter.isVisible("gitlab_merge_merge_request"));
        assertTrue(filter.isVisible("gitlab_list_projects"));
    }

    @Test
    void enabledShowsAll() {
        WriteToolFilter filter = new WriteToolFilter();
        filter.writeEnabled = true;
        for (String name : McpWriteTools.NAMES) {
            assertTrue(filter.isVisible(name), name);
        }
        assertTrue(filter.isVisible("gitlab_list_projects"));
    }

}
