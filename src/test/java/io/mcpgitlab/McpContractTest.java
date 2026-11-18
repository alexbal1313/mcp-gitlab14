package io.mcpgitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(GitLabTestResource.class)
public class McpContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TestHTTPResource("/mcp")
    URI endpoint;
    GitLabStub gitlab;
    private McpTestClient client;

    @BeforeEach
    void initialize() throws Exception {
        if (client != null) client.close();
        client = new McpTestClient(endpoint);
        client.initialize();
    }

    @AfterEach
    void shutdown() throws Exception {
        if (client != null) client.close();
    }

    @TestFactory
    List<DynamicTest> everyToolMatchesItsGitLabContract() throws Exception {
        return scenarios("/contracts/tools.json");
    }

    @TestFactory
    List<DynamicTest> errorsAndBoundaryCases() throws Exception {
        return scenarios("/contracts/edge-cases.json");
    }

    private List<DynamicTest> scenarios(String resource) throws Exception {
        JsonNode scenarios;
        try (var input = getClass().getResourceAsStream(resource)) {
            scenarios = JSON.readTree(input);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode scenario : scenarios) {
            tests.add(DynamicTest.dynamicTest(scenario.get("name").asText() + ": " + scenario.path("label").asText("success"), () -> {
                initialize();
                gitlab.script(scenario.get("steps"));
                JsonNode result = client.call(scenario.get("name").asText(), scenario.get("arguments"));
                gitlab.verify(scenario.get("steps").size());
                assertEquals(scenario.has("error"), result.path("isError").asBoolean(), result::toString);
                JsonNode payload = result.get("structuredContent");
                if (scenario.has("error")) {
                    assertEquals(scenario.get("error"), payload.get("code"), result::toString);
                }
                if (scenario.has("messageContains")) {
                    assertTrue(payload.path("message").asText().contains(scenario.get("messageContains").asText()), result::toString);
                }
                scenario.path("expected").properties().forEach(entry -> {
                    JsonNode expected = entry.getValue();
                    if (expected.isTextual() && expected.asText().startsWith("$GITLAB")) {
                        expected = JSON.getNodeFactory().textNode(expected.asText().replace("$GITLAB", gitlab.url()));
                    }
                    assertEquals(expected, payload.at(entry.getKey()), entry.getKey());
                });
            }));
        }
        return tests;
    }

    @Test
    void catalogHasExactlyTheDocumentedToolsAndObjectSchemas() throws Exception {
        for (JsonNode tool : client.verifiedTools()) {
            String name = tool.get("name").asText();
            var rows = java.nio.file.Files.readString(java.nio.file.Path.of("README.md")).lines()
                    .filter(line -> line.startsWith("|") && line.contains("`" + name + "`")).toList();
            assertEquals(1, rows.size(), "README entry for " + name);
            var names = java.util.regex.Pattern.compile("`([a-zA-Z][a-zA-Z0-9_]*)`").matcher(rows.getFirst());
            while (names.find()) {
                if (!names.group(1).equals(name)) {
                    assertTrue(tool.at("/inputSchema/properties").has(names.group(1)),
                            "Unknown README argument " + names.group(1) + " for " + name);
                }
            }
            assertEquals("object", tool.at("/inputSchema/type").asText());
            assertEquals("object", tool.at("/outputSchema/type").asText());
            assertTrue(tool.at("/outputSchema/additionalProperties").asBoolean());
            assertFalse(tool.path("description").asText().isBlank());
            if (tool.get("name").asText().equals("gitlab_create_issue")) {
                var required = new HashSet<String>();
                tool.at("/inputSchema/required").forEach(value -> required.add(value.asText()));
                assertEquals(java.util.Set.of("projectFullPath", "title"), required);
                assertEquals("string", tool.at("/inputSchema/properties/title/type").asText());
                assertEquals("array", tool.at("/inputSchema/properties/labels/type").asText());
            }
        }
    }

    @Test
    void invalidMcpCallsNeverReachGitLab() throws Exception {
        gitlab.script(JSON.createArrayNode());
        for (var params : List.of(
                Map.of("name", "gitlab_create_issue", "arguments", Map.of("projectFullPath", "team/project")),
                Map.of("name", "gitlab_list_project_issues", "arguments", Map.of("projectFullPath", "team/project", "state", "invalid")),
                Map.of("name", "gitlab_get_issue", "arguments", Map.of("projectFullPath", "team/project", "iid", "not-a-number")),
                Map.of("name", "does_not_exist", "arguments", Map.of()))) {
            JsonNode response = client.request("tools/call", params);
            if (params.get("name").equals("does_not_exist")) {
                assertEquals(-32602, response.path("error").path("code").asInt(), response::toString);
            } else {
                assertTrue(response.at("/result/isError").asBoolean(), response::toString);
                assertFalse(response.at("/result/content/0/text").asText().isBlank(), response::toString);
            }
        }
        gitlab.verify(0);
    }

    @Test
    void restToolUsesRealTransportAndNormalizesGitLabResponse() throws Exception {
        gitlab.script(JSON.readTree("""
                [{"method":"GET","path":"/api/v4/projects/team%2Fproject/repository/tags",
                  "query":{"per_page":"20"},"response":[{"name":"v1","target":"abc","message":"release","protected":true}]}]
                """));
        JsonNode result = client.call("gitlab_list_tags", Map.of("projectFullPath", "team/project"));
        assertFalse(result.path("isError").asBoolean(), result::toString);
        assertEquals("v1", result.at("/structuredContent/items/0/name").asText());
        gitlab.verify(1);
    }
}
