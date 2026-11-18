package io.mcpgitlab.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Base HTTP engine for GitLab REST v4: configuration, URI construction,
 * GET/POST/PUT/DELETE requests, and shared response and error types.
 *
 * <p>This class is not a standalone CDI bean; concrete REST clients
 * ({@code GitLabRestReadClient} for reads and {@code GitLabRestWriteClient} for
 * writes) inherit it to share transport without duplication.
 */
public abstract class GitLabRestClientBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @ConfigProperty(name = "gitlab.base.uri")
    protected String baseUri;

    /**
     * Sends a GET request to GitLab REST v4.
     *
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    protected RestResponse get(String privateToken, String path, Object... parameters) {
        return send(request(privateToken, path, parameters).GET().build());
    }

    /**
     * Sends a POST request with a JSON body to GitLab REST v4.
     *
     * @param body request body to serialize as JSON; {@code null} becomes an empty object
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    protected RestResponse post(String privateToken, String path, Object body, Object... parameters) {
        HttpRequest.Builder builder = request(privateToken, path, parameters)
                .header("Content-Type", "application/json");
        return send(builder.POST(bodyPublisher(body)).build());
    }

    /**
     * Sends a PUT request with a JSON body to GitLab REST v4.
     *
     * @param body request body to serialize as JSON; {@code null} becomes an empty object
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    protected RestResponse put(String privateToken, String path, Object body, Object... parameters) {
        HttpRequest.Builder builder = request(privateToken, path, parameters)
                .header("Content-Type", "application/json");
        return send(builder.PUT(bodyPublisher(body)).build());
    }

    /**
     * Sends a DELETE request to GitLab REST v4.
     *
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    protected RestResponse delete(String privateToken, String path, Object... parameters) {
        return send(request(privateToken, path, parameters).DELETE().build());
    }

    private HttpRequest.Builder request(String privateToken, String path, Object... parameters) {
        return HttpRequest.newBuilder(uri(path, parameters))
                .header("PRIVATE-TOKEN", privateToken)
                .header("Accept", "application/json");
    }

    private HttpRequest.BodyPublisher bodyPublisher(Object body) {
        try {
            String json = body == null ? "{}" : JSON.writeValueAsString(body);
            return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new GitLabRestException("Failed to serialize the GitLab request body", exception);
        }
    }

    private RestResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new RestResponse(response.statusCode(),
                    response.headers().firstValue("x-next-page").orElse(null), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitLabRestException("The GitLab request was interrupted", exception);
        } catch (IOException exception) {
            throw new GitLabRestException("Failed to connect to GitLab", exception);
        }
    }

    private URI uri(String path, Object... parameters) {
        List<String> query = new ArrayList<>();
        for (int index = 0; index < parameters.length; index += 2) {
            Object value = parameters[index + 1];
            if (value != null) {
                query.add(encode(parameters[index].toString()) + "=" + encode(value.toString()));
            }
        }
        String separator = query.isEmpty() ? "" : "?" + String.join("&", query);
        return URI.create(baseUri + path + separator);
    }

    protected String projectPath(String projectFullPath) {
        return "/api/v4/projects/" + encode(projectFullPath);
    }

    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Contains the status, next-page cursor, and REST response body.
     */
    public record RestResponse(int status, String nextPage, String body) {
    }

    /**
     * Reports a GitLab REST transport error.
     */
    public static final class GitLabRestException extends RuntimeException {
        protected GitLabRestException(String message, Throwable cause) {
            super(message, cause);
        }

        protected GitLabRestException(String message) {
            super(message);
        }
    }

}