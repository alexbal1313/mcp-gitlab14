package io.mcpgitlab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.mcp.server.OutputSchemaGenerator;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Exposes a free-form object output schema for tools with structuredContent.
 *
 * <p>By default, victools generates a schema from ToolResponse, where structuredContent
 * is declared as Object and mapped to an empty {} schema (without validation keywords).
 * That schema is valid but not portable across MCP clients and carries no contract,
 * so we explicitly expose {@code {"type": "object", "additionalProperties": true}}:
 * all structuredContent values (payloads and errors) are objects.</p>
 */
@ApplicationScoped
public class FreeFormOutputSchemaGenerator implements OutputSchemaGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ObjectNode generate(Class<?> from) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", true);
        return schema;
    }
}
