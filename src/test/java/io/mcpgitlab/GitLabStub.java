package io.mcpgitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scripted external GitLab boundary; no application classes are replaced. */
public class GitLabStub implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    private final ConcurrentLinkedQueue<JsonNode> replies = new ConcurrentLinkedQueue<>();
    private final List<String> failures = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();

    public GitLabStub() throws IOException {
        this(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    }

    public GitLabStub(HttpServer server) {
        this.server = server;
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            JsonNode expected = replies.poll();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = 500;
            byte[] response = "Unexpected GitLab request".getBytes(StandardCharsets.UTF_8);
            try {
                if (expected == null) {
                    throw new AssertionError("Unexpected request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
                }
                assertEquals("test-token", exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
                assertEquals(expected.path("method").asText(), exchange.getRequestMethod());
                assertEquals(expected.path("path").asText(), exchange.getRequestURI().getRawPath());
                if (expected.has("query")) {
                    var query = new java.util.HashMap<String, String>();
                    String raw = exchange.getRequestURI().getRawQuery();
                    if (raw != null) {
                        for (String pair : raw.split("&")) {
                            String[] parts = pair.split("=", 2);
                            query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                        }
                    }
                    expected.get("query").properties().forEach(entry ->
                            assertEquals(entry.getValue().asText(), query.get(entry.getKey()), entry.getKey()));
                }
                if (expected.has("operation")) {
                    JsonNode payload = JSON.readTree(body);
                    assertTrue(payload.path("query").asText().matches("(?s).*query\\s+" + expected.get("operation").asText() + "[\\s(].*"), payload.toString());
                    if (expected.has("variables")) {
                        expected.get("variables").properties().forEach(entry ->
                                assertEquals(entry.getValue(), payload.path("variables").get(entry.getKey()), entry.getKey()));
                    }
                }
                if (expected.has("body")) {
                    assertEquals(expected.get("body"), JSON.readTree(body));
                }
                status = expected.path("status").asInt(200);
                response = expected.has("raw") ? expected.get("raw").asText().getBytes(StandardCharsets.UTF_8)
                        : JSON.writeValueAsBytes(expected.get("response"));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (expected.has("headers")) {
                    expected.get("headers").properties().forEach(entry ->
                            exchange.getResponseHeaders().set(entry.getKey(), entry.getValue().asText()));
                }
                if (expected.path("disconnect").asBoolean()) {
                    exchange.close();
                    return;
                }
            } catch (AssertionError | RuntimeException exception) {
                synchronized (failures) {
                    failures.add(exception.toString());
                }
            }
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return (server instanceof com.sun.net.httpserver.HttpsServer ? "https" : "http")
                + "://127.0.0.1:" + server.getAddress().getPort();
    }

    public void script(JsonNode steps) {
        replies.clear();
        synchronized (failures) {
            failures.clear();
        }
        requests.set(0);
        steps.forEach(replies::add);
    }

    public void verify(int count) {
        synchronized (failures) {
            assertEquals(List.of(), failures, "GitLab request contract");
        }
        assertEquals(count, requests.get(), "GitLab request count");
        assertTrue(replies.isEmpty(), "Unconsumed GitLab responses: " + replies);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
