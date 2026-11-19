package io.mcpgitlab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.ToolResponse;
import org.junit.jupiter.api.Test;
import io.mcpgitlab.client.GitLabRestClientBase.RestResponse;
import io.mcpgitlab.client.GitLabRestWriteClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicitly supplied blank values must not silently drop out of an MR write request.
 */
class MergeRequestInputTest {

    private static final String MR = "{\"iid\":1,\"title\":\"t\",\"state\":\"opened\",\"source_branch\":\"s\","
            + "\"target_branch\":\"m\",\"web_url\":\"u\",\"updated_at\":\"x\",\"assignees\":[],\"reviewers\":[]}";

    /** Substitute REST client: records the request body and never calls GitLab. */
    private static final class RecordingClient extends GitLabRestWriteClient {
        Map<String, Object> body;
        int calls;

        @Override
        public RestResponse updateMergeRequest(String token, String project, int iid, Map<String, Object> body) {
            calls++;
            this.body = body;
            return new RestResponse(200, null, MR);
        }

        @Override
        public RestResponse mergeMergeRequest(String token, String project, int iid, Map<String, Object> body) {
            calls++;
            this.body = body;
            return new RestResponse(200, null, MR);
        }
    }

    private final RecordingClient client = new RecordingClient();
    private final GitLabWriteService service = new GitLabWriteService();

    MergeRequestInputTest() {
        service.objectMapper = new ObjectMapper();
        service.gitLabRestWriteClient = client;
    }

    private static String code(ToolResponse response) {
        assertTrue(response.isError());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.structuredContent();
        return (String) payload.get("code");
    }

    @Test
    void mergeRejectsBlankSha() {
        assertEquals("VALIDATION_ERROR", code(service.mergeMergeRequest("team/project", 1, null, null, " ")));
        assertEquals(0, client.calls);
    }

    @Test
    void mergeWithoutShaIsAllowed() {
        ToolResponse response = service.mergeMergeRequest("team/project", 1, null, null, null);
        assertFalse(response.isError());
        assertFalse(client.body.containsKey("sha"));
    }

    @Test
    void mergePassesSha() {
        assertFalse(service.mergeMergeRequest("team/project", 1, null, null, "abc").isError());
        assertEquals("abc", client.body.get("sha"));
    }

    @Test
    void updateRejectsBlankTitleAndTargetBranch() {
        assertEquals("VALIDATION_ERROR",
                code(service.updateMergeRequest("team/project", 1, " ", "changed", null, null, null, null)));
        assertEquals("VALIDATION_ERROR",
                code(service.updateMergeRequest("team/project", 1, null, "changed", null, "", null, null)));
        assertEquals(0, client.calls);
    }

    @Test
    void updateAllowsBlankDescriptionToClear() {
        assertFalse(service.updateMergeRequest("team/project", 1, null, "", null, null, null, null).isError());
        assertEquals(Map.of("description", ""), client.body);
        assertNull(client.body.get("title"));
    }
}
