package io.mcpgitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mcpgitlab.controller.McpWriteTools;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code gitlab.write.enabled=false}: write tools are absent from {@code tools/list}, and
 * {@code tools/call} for them is rejected at the MCP protocol level before reaching GitLab.
 */
@QuarkusTest
@TestProfile(WriteToolsDisabledTest.WriteDisabled.class)
class WriteToolsDisabledTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final class WriteDisabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gitlab.write.enabled", "false");
        }
    }

    @TestHTTPResource("/mcp")
    URI endpoint;
    GitLabStub gitlab;

    @Test
    void writeToolsAreHiddenAndCallsRejected() throws Exception {
        gitlab.script(JSON.createArrayNode());
        try (var client = new McpTestClient(endpoint)) {
            client.initialize();
            JsonNode tools = client.request("tools/list", Map.of()).at("/result/tools");
            Set<String> names = new HashSet<>();
            tools.forEach(tool -> names.add(tool.get("name").asText()));
            assertEquals(34, names.size(), names::toString);
            assertTrue(names.stream().noneMatch(McpWriteTools.NAMES::contains), names::toString);

            for (String name : McpWriteTools.NAMES) {
                JsonNode response = client.request("tools/call",
                        Map.of("name", name, "arguments", Map.of("projectFullPath", "team/project")));
                assertFalse(response.has("result"), name);
                assertEquals(-32602, response.path("error").path("code").asInt(), name);
            }

            JsonNode read = client.request("tools/call",
                    Map.of("name", "gitlab_list_project_pipelines", "arguments", Map.of("projectFullPath", "bad")));
            assertEquals("VALIDATION_ERROR", read.at("/result/structuredContent/code").asText(), read::toString);
        }
        gitlab.verify(0);
    }
}
