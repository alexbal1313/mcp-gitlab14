package io.mcpgitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "docker.smoke", matches = "true")
class DockerSmokeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void packagedContainerServesMcpReadAndWrite() throws Exception {
        String image = System.getProperty("docker.image", "mcp-gitlab14:local");
        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        try (var gitlab = new GitLabStub(server)) {
            String container = docker("run", "--detach", "--rm", "--add-host", "host.docker.internal:host-gateway",
                    "-p", "127.0.0.1::8004", "-e", "TOKEN_GITLAB=test-token", "-e", "GITLAB_WRITE_ENABLED=true",
                    "-e", "GITLAB_BASE_URI=http://host.docker.internal:" + server.getAddress().getPort(), image).strip();
            try {
                String port = docker("port", container, "8004/tcp").strip().substring("127.0.0.1:".length());
                URI endpoint = URI.create("http://127.0.0.1:" + port + "/mcp");
                waitForStartup(endpoint);
                try (var client = new McpTestClient(endpoint)) {
                    client.initialize();
                    client.verifiedTools();
                    gitlab.script(JSON.readTree("""
                            [{"method":"GET","path":"/api/v4/projects/team%2Fproject/repository/tags",
                              "response":[{"name":"v1","target":"abc","message":"Release","protected":true}]},
                             {"method":"POST","path":"/api/v4/projects/team%2Fproject/issues",
                              "body":{"title":"Smoke test"},"status":201,
                              "response":{"iid":3,"title":"Smoke test","labels":[],"milestone":null}}]
                            """));
                    JsonNode read = client.call("gitlab_list_tags", Map.of("projectFullPath", "team/project"));
                    assertFalse(read.path("isError").asBoolean(), read::toString);
                    assertEquals("v1", read.at("/structuredContent/items/0/name").asText());
                    JsonNode write = client.call("gitlab_create_issue",
                            Map.of("projectFullPath", "team/project", "title", "Smoke test"));
                    assertFalse(write.path("isError").asBoolean(), write::toString);
                    assertEquals(3, write.at("/structuredContent/iid").asInt());
                    gitlab.verify(2);
                }
            } finally {
                docker("rm", "--force", container);
            }
        }
    }

    private void waitForStartup(URI endpoint) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                try {
                    var response = client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(1)).GET().build(),
                            HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() != 503) return;
                } catch (java.io.IOException ignored) {
                    // The container has not opened its HTTP port yet.
                }
                Thread.sleep(200);
            }
        }
        fail("Container did not start within 30 seconds");
    }

    private String docker(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("docker");
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Docker command timed out: " + command);
        }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), "Docker command failed: " + command);
        return output;
    }
}
