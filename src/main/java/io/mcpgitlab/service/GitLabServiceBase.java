package io.mcpgitlab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.mcpgitlab.client.GitLabGraphQLClient;
import io.mcpgitlab.client.GitLabRestReadClient;
import io.mcpgitlab.client.GitLabRestClientBase;
import io.mcpgitlab.client.GitLabRestWriteClient;
import io.mcpgitlab.dto.GraphQLRequest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base layer for GitLab services: configuration, GraphQL/REST transport,
 * value normalization, input validation, and a shared error-handling boundary.
 *
 * <p>This class is not a standalone CDI bean; concrete services
 * inherit this layer to share dependencies and behavior without duplication.
 */
public abstract class GitLabServiceBase {

    protected static final int DEFAULT_ITEMS = 20;

    protected static final int MAX_ITEMS = 20;

    protected static final int DEFAULT_LOG_LINES = 200;

    protected static final int MAX_LOG_LINES = 500;

    protected static final List<String> PIPELINE_STATUSES = List.of("created", "waiting_for_resource", "preparing",
            "pending", "running", "failed", "success", "canceled", "skipped", "manual", "scheduled");

    @ConfigProperty(name = "gitlab.token")
    protected String privateToken;

    @ConfigProperty(name = "gitlab.base.uri")
    protected String baseUri;

    @Inject
    @RestClient
    protected GitLabGraphQLClient graphQLClient;
    @Inject
    protected GitLabRestReadClient restClient;

    @Inject
    protected GitLabRestWriteClient restWriteClient;

    @Inject
    protected ObjectMapper objectMapper;
    /**
     * Executes functional code, handles shared errors, and builds the response.
     * @param operation
     * @return
     */
    protected ToolResponse execute(Supplier<Object> operation) {
        try {
            return structured(operation.get(), false);
        } catch (GitLabException exception) {
            return structured(error(exception.code, exception.getMessage()), true);
        } catch (ProcessingException exception) {
            return structured(error("GITLAB_ERROR", "Failed to connect to GitLab (GraphQL)"), true);
        } catch (GitLabRestClientBase.GitLabRestException exception) {
            return structured(error("GITLAB_ERROR", "Failed to connect to GitLab (REST)"), true);
        } catch (RuntimeException exception) {
            return structured(error("exception", exception.getClass().getSimpleName()), true);
        }
    }

    /**
     * Builds a dual-content ToolResponse: a JSON payload in TextContent
     * (backward compatibility with MCP 2024-11-05 clients, SHOULD in 2025-06-18)
     * and structuredContent for machine-readable clients.
     */
    protected ToolResponse structured(Object payload, boolean isError) {
        List<Content> content;
        try {
            content = List.of(new TextContent(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            content = List.of();
        }
        return new ToolResponse(isError, content, payload, null);
    }

    protected Map<String, Object> graphQL(String query, Map<String, Object> variables, String resultField) {
        Map<String, Object> response;
        try {
            response = graphQLClient.execute(privateToken, new GraphQLRequest(query, variables));
        } catch (WebApplicationException exception) {
            try (var failure = exception.getResponse()) {
                ensureRestSuccess(failure.getStatus());
            }
            throw exception;
        }
        if (response.get("errors") instanceof Collection<?> errors && !errors.isEmpty()) {
            throw graphQLError(errors);
        }
        Map<String, Object> data = object(response.get("data"));
        Object result = data.get(resultField);
        if (result == null) {
            throw new GitLabException("NOT_FOUND", "Object not found");
        }
        return object(result);
    }

    protected Object restPage(GitLabRestClientBase.RestResponse response, Function<Map<String, Object>, Object> mapper) {
        ensureRestSuccess(response.status());
        try {
            List<Map<String, Object>> values = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            List<Object> items = new ArrayList<>();
            for (Map<String, Object> value : values) {
                items.add(mapper.apply(value));
            }
            String nextPage = response.nextPage();
            return pageResult(items, nextPage, nextPage != null && !nextPage.isBlank());
        } catch (JsonProcessingException exception) {
            throw new GitLabException("GITLAB_ERROR", "GitLab returned invalid JSON");
        }
    }

    protected List<Map<String, Object>> restArray(GitLabRestClientBase.RestResponse response) {
        ensureRestSuccess(response.status());
        try {
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new GitLabException("GITLAB_ERROR", "GitLab returned invalid JSON");
        }
    }

    protected Map<String, Object> restObject(GitLabRestClientBase.RestResponse response) {
        ensureRestSuccess(response.status());
        try {
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new GitLabException("GITLAB_ERROR", "GitLab returned invalid JSON");
        }
    }

    protected void ensureRestSuccess(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 404) {
            throw new GitLabException("NOT_FOUND", "Object not found");
        }
        if (status == 401 || status == 403) {
            throw new GitLabException("ACCESS_DENIED", "GitLab denied access");
        }
        throw new GitLabException("GITLAB_ERROR", "GitLab returned error " + status);
    }

    /**
     * Checks write response success and, for 400/405/406/409/422 errors,
     * preserves the GitLab response body with the specific rejection reason.
     *
     * <p>Unlike {@link #ensureRestSuccess(int)}, which sees only the status
     * code, GitLab write errors contain a structured {@code message} field
     * (such as "title is missing"). Without parsing the body, the agent cannot know what to fix.
     *
     * @param response GitLab response to a write operation
     */
    protected void ensureWriteSuccess(GitLabRestClientBase.RestResponse response) {
        int status = response.status();
        if (status >= 200 && status < 300) {
            return;
        }
        String detail = writeErrorDetail(response.body());
        if (status == 404) {
            throw new GitLabException("NOT_FOUND", "Object not found: " + detail);
        }
        if (status == 401 || status == 403) {
            throw new GitLabException("ACCESS_DENIED", "GitLab denied access");
        }
        // 405/406: the object is in an unsuitable state (MR not mergeable, pipeline/job not cancelable or retryable).
        if (status == 400 || status == 405 || status == 406 || status == 409 || status == 422) {
            throw new GitLabException("VALIDATION_ERROR",
                    detail == null ? "GitLab rejected the change" : "GitLab rejected the change: " + detail);
        }
        throw new GitLabException("GITLAB_ERROR",
                detail == null ? "GitLab returned error " + status : "GitLab returned error " + status + ": " + detail);
    }

    private String writeErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<>() {
            });
            Object message = value(parsed, "message", "error");
            return message == null ? body : message.toString();
        } catch (JsonProcessingException exception) {
            return body;
        }
    }

    /**
     * Extracts the numeric id from a GraphQL global id ({@code gid://gitlab/Ci::Build/456}).
     */
    protected int numericId(Object gid) {
        if (!(gid instanceof String id) || !id.contains("/")) {
            throw new GitLabException("GITLAB_ERROR", "GitLab did not return a numeric id");
        }
        String tail = id.substring(id.lastIndexOf('/') + 1);
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException exception) {
            throw new GitLabException("GITLAB_ERROR", "GitLab did not return a numeric id");
        }
    }

    protected GitLabException graphQLError(Collection<?> errors) {
        String text = errors.toString().toLowerCase();
        if (text.contains("not found")) {
            return new GitLabException("NOT_FOUND", "Object not found");
        }
        if (text.contains("access") || text.contains("permission") || text.contains("authorized")) {
            return new GitLabException("ACCESS_DENIED", "GitLab denied access");
        }
        return new GitLabException("GITLAB_ERROR", "GitLab failed to execute the request");
    }

    protected Object page(Map<String, Object> connection, Function<Map<String, Object>, Object> mapper) {
        List<Object> items = new ArrayList<>();
        for (Map<String, Object> value : objects(connection.get("nodes"))) {
            items.add(mapper.apply(value));
        }
        Map<String, Object> pageInfo = object(connection.get("pageInfo"));
        return pageResult(items, value(pageInfo, "endCursor"), Boolean.TRUE.equals(pageInfo.get("hasNextPage")));
    }

    protected Map<String, Object> pageResult(List<Object> items, Object endCursor, boolean hasNextPage) {
        Map<String, Object> pageInfo = map();
        pageInfo.put("endCursor", endCursor);
        pageInfo.put("hasNextPage", hasNextPage);
        Map<String, Object> result = map();
        result.put("items", items);
        result.put("pageInfo", pageInfo);
        return result;
    }

    protected List<Object> stringList(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof Collection<?> values) {
            for (Object item : values) {
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    protected String lower(Object value) {
        return value == null ? null : value.toString().toLowerCase();
    }

    protected String statusEnum(String status) {
        String value = optional(status);
        return value == null ? null : value.toUpperCase();
    }

    protected List<String> oneEnum(String status) {
        String value = statusEnum(status);
        return value == null ? null : List.of(value);
    }

    protected void validatePipelineStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        if (!PIPELINE_STATUSES.contains(status)) {
            throw new GitLabException("VALIDATION_ERROR", "Invalid status value");
        }
    }

    protected int logLines(Integer lines) {
        if (lines == null) {
            return DEFAULT_LOG_LINES;
        }
        if (lines < 1 || lines > MAX_LOG_LINES) {
            throw new GitLabException("VALIDATION_ERROR", "lines must be between 1 and 500");
        }
        return lines;
    }

    protected int pageSize(Integer items) {
        if (items == null) {
            return DEFAULT_ITEMS;
        }
        if (items < 1 || items > MAX_ITEMS) {
            throw new GitLabException("VALIDATION_ERROR", "items must be between 1 and 20");
        }
        return items;
    }

    protected void validateLineRange(Integer startLine, Integer endLine) {
        if (startLine != null && startLine < 1) {
            throw new GitLabException("VALIDATION_ERROR", "startLine must be a positive number");
        }
        if (endLine != null && endLine < 1) {
            throw new GitLabException("VALIDATION_ERROR", "endLine must be a positive number");
        }
        if (startLine != null && endLine != null && endLine < startLine) {
            throw new GitLabException("VALIDATION_ERROR", "endLine must be greater than or equal to startLine");
        }
    }

    protected Integer restPageNumber(String afterCursor) {
        if (afterCursor == null || afterCursor.isBlank()) {
            return null;
        }
        try {
            int page = Integer.parseInt(afterCursor);
            if (page < 1) {
                throw new NumberFormatException();
            }
            return page;
        } catch (NumberFormatException exception) {
            throw new GitLabException("VALIDATION_ERROR", "REST afterCursor must be a positive page number");
        }
    }

    protected String cursor(String afterCursor) {
        return afterCursor == null || afterCursor.isBlank() ? null : afterCursor;
    }

    protected void validateProject(String projectFullPath) {
        String value = required(projectFullPath, "projectFullPath");
        if (value.startsWith("/") || value.endsWith("/") || !value.contains("/") || value.contains("//")) {
            throw new GitLabException("VALIDATION_ERROR", "projectFullPath must be a full group/project path");
        }
    }

    protected void validateIid(Integer iid) {
        if (iid == null || iid < 1) {
            throw new GitLabException("VALIDATION_ERROR", "iid must be a positive number");
        }
    }

    protected void validateState(String state, String... allowed) {
        if (state == null || state.isBlank()) {
            return;
        }
        for (String value : allowed) {
            if (value.equals(state)) {
                return;
            }
        }
        throw new GitLabException("VALIDATION_ERROR", "Invalid state value");
    }

    protected void validateDate(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new GitLabException("VALIDATION_ERROR", "Date must be ISO 8601 with a timezone");
        }
    }

    protected String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new GitLabException("VALIDATION_ERROR", name + " is required");
        }
        return value;
    }

    protected String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    protected List<String> one(String value) {
        return optional(value) == null ? null : List.of(value);
    }

    protected Map<String, Object> vars(Object... values) {
        Map<String, Object> result = map();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    protected Map<String, Object> fields(Map<String, Object> source, String... names) {
        Map<String, Object> result = map();
        for (String name : names) {
            result.put(name, value(source, name));
        }
        return result;
    }

    protected void copy(Map<String, Object> target, Map<String, Object> source, String... names) {
        for (String name : names) {
            target.put(name, value(source, name));
        }
    }

    protected Object value(Map<String, Object> source, String... names) {
        for (String name : names) {
            if (source.containsKey(name)) {
                return source.get(name);
            }
            String snake = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            if (source.containsKey(snake)) {
                return source.get(snake);
            }
        }
        return null;
    }

    protected Map<String, Object> person(Map<String, Object> source) {
        return fields(source, "username", "name");
    }

    protected List<Object> people(Object source) {
        List<Object> result = new ArrayList<>();
        for (Map<String, Object> user : objects(source)) {
            result.add(person(user));
        }
        return result;
    }

    protected List<Object> titles(Object source) {
        List<Object> result = new ArrayList<>();
        if (source instanceof Collection<?> values) {
            for (Object value : values) {
                if (value instanceof String title) {
                    result.add(title);
                }
            }
            if (!result.isEmpty() || values.isEmpty()) {
                return result;
            }
        }
        for (Map<String, Object> label : objects(source)) {
            result.add(value(label, "title", "name"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> found ? (Map<String, Object>) found : map();
    }

    protected List<Map<String, Object>> objects(Object value) {
        Object collection = value instanceof Map<?, ?> found && found.containsKey("nodes") ? found.get("nodes") : value;
        if (!(collection instanceof Collection<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?>) {
                result.add(object(item));
            }
        }
        return result;
    }

    protected Map<String, Object> single(Object value, String message) {
        List<Map<String, Object>> values = objects(value);
        if (values.isEmpty()) {
            throw new GitLabException("NOT_FOUND", message);
        }
        return values.getFirst();
    }

    protected Map<String, Object> error(String code, String message) {
        Map<String, Object> result = map();
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    protected Map<String, Object> error(Object detailValue) {
        Map<String, Object> result = error("GITLAB_ERROR", "GitLab returned an unexpected response");
        result.put("details", Map.of("exception", detailValue));
        return result;
    }

    protected Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    protected static final class GitLabException extends RuntimeException {
        private final String code;

        protected GitLabException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
