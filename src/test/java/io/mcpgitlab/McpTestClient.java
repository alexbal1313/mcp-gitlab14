package io.mcpgitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real Streamable HTTP protocol, including session cleanup. */
class McpTestClient implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final URI endpoint;
    private String session;
    private int id;

    McpTestClient(URI endpoint) {
        this.endpoint = endpoint;
    }

    void initialize() throws Exception {
        JsonNode response = request("initialize", Map.of(
                "protocolVersion", "2025-06-18", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "contract-tests", "version", "1")));
        assertEquals("2025-06-18", response.path("result").path("protocolVersion").asText());
        request("notifications/initialized", null);
    }

    JsonNode request(String method, Object params) throws Exception {
        var message = new java.util.LinkedHashMap<String, Object>();
        message.put("jsonrpc", "2.0");
        if (!method.startsWith("notifications/")) {
            message.put("id", ++id);
        }
        message.put("method", method);
        if (params != null) message.put("params", params);
        var builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-06-18")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(message)));
        if (session != null) builder.header("Mcp-Session-Id", session);
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        session = response.headers().firstValue("Mcp-Session-Id").orElse(session);
        assertTrue(response.statusCode() == 200 || response.statusCode() == 202,
                () -> response.statusCode() + ": " + response.body());
        String body = response.body();
        if (body.isBlank()) return JSON.createObjectNode();
        if (body.startsWith("event:") || body.startsWith("data:")) {
            body = body.lines().filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).stripLeading()).findFirst().orElseThrow();
        }
        return JSON.readTree(body);
    }

    JsonNode verifiedTools() throws Exception {
        var expected = new java.util.HashSet<String>();
        try (var input = McpTestClient.class.getResourceAsStream("/contracts/tools.json")) {
            assertNotNull(input, "Missing contracts/tools.json");
            JSON.readTree(input).forEach(scenario -> expected.add(scenario.get("name").asText()));
        }
        assertFalse(expected.isEmpty(), "Tool contracts must not be empty");
        JsonNode response = request("tools/list", Map.of());
        assertFalse(response.has("error"), response::toString);
        JsonNode tools = response.at("/result/tools");
        assertTrue(tools.isArray(), response::toString);
        var actual = new java.util.HashSet<String>();
        tools.forEach(tool -> assertTrue(actual.add(tool.get("name").asText()), "Duplicate tool name"));
        assertEquals(expected, actual);
        return tools;
    }

    JsonNode call(String name, Object arguments) throws Exception {
        JsonNode response = request("tools/call", Map.of("name", name, "arguments", arguments));
        assertFalse(response.has("error"), response::toString);
        JsonNode result = response.get("result");
        assertEquals(result.get("structuredContent"), JSON.readTree(result.path("content").get(0).path("text").asText()));
        return result;
    }


    @Override
    public void close() throws Exception {
        try {
            if (session != null) {
                var response = http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                        .header("Mcp-Session-Id", session).DELETE().build(), HttpResponse.BodyHandlers.discarding());
                assertTrue(response.statusCode() == 200 || response.statusCode() == 204,
                        "MCP session cleanup: " + response.statusCode());
            }
        } finally {
            session = null;
            http.close();
        }
    }
}
