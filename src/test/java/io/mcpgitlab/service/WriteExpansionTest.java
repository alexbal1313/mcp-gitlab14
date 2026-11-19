package io.mcpgitlab.service;

import org.junit.jupiter.api.Test;
import io.mcpgitlab.client.GitLabRestClientBase.RestResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared write expansion foundation: error mapping, global ids, result normalizers.
 */
class WriteExpansionTest {

    private final GitLabWriteService service = new GitLabWriteService();

    WriteExpansionTest() {
        service.objectMapper = new ObjectMapper();
    }

    private String code(int status, String body) {
        GitLabServiceBase.GitLabException exception = assertThrows(GitLabServiceBase.GitLabException.class,
                () -> service.ensureWriteSuccess(new RestResponse(status, null, body)));
        return codeOf(exception);
    }

    private static String codeOf(GitLabServiceBase.GitLabException exception) {
        try {
            var field = GitLabServiceBase.GitLabException.class.getDeclaredField("code");
            field.setAccessible(true);
            return (String) field.get(exception);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void stateErrorsAreValidation() {
        assertEquals("VALIDATION_ERROR", code(405, "{\"message\":\"405 Method Not Allowed\"}"));
        assertEquals("VALIDATION_ERROR", code(406, "{\"message\":\"Branch cannot be merged\"}"));
        assertEquals("VALIDATION_ERROR", code(409, "{\"message\":\"SHA does not match HEAD\"}"));
        assertEquals("VALIDATION_ERROR", code(400, null));
        assertEquals("VALIDATION_ERROR", code(422, null));
        assertEquals("NOT_FOUND", code(404, null));
        assertEquals("ACCESS_DENIED", code(401, null));
        assertEquals("ACCESS_DENIED", code(403, null));
        assertEquals("GITLAB_ERROR", code(500, null));
    }

    @Test
    void numericIdFromGlobalId() {
        assertEquals(123, service.numericId("gid://gitlab/Ci::Pipeline/123"));
        assertEquals(456, service.numericId("gid://gitlab/Ci::Build/456"));
        assertEquals("GITLAB_ERROR", codeOf(assertThrows(GitLabServiceBase.GitLabException.class,
                () -> service.numericId("gid://gitlab/Ci::Build/x"))));
        assertEquals("GITLAB_ERROR", codeOf(assertThrows(GitLabServiceBase.GitLabException.class,
                () -> service.numericId(null))));
    }

    private static final Map<String, Object> REST_MR = Map.of("iid", 7, "title", "T", "state", "opened",
            "source_branch", "src", "target_branch", "main", "web_url", "https://gitlab.example.com/p/-/merge_requests/7",
            "updated_at", "2026-11-19T10:00:00Z", "merge_commit_sha", "abc",
            "assignees", List.of(Map.of("username", "a", "name", "A", "id", 1)),
            "reviewers", List.of(Map.of("username", "r", "name", "R", "id", 2)));

    @Test
    void mergeRequestResults() {
        Map<String, Object> result = service.mergeRequestPeopleResult(REST_MR, "g/p");
        assertEquals(7, result.get("iid"));
        assertEquals("src", result.get("sourceBranch"));
        assertEquals("https://gitlab.example.com/p/-/merge_requests/7", result.get("webUrl"));
        assertEquals("g/p", result.get("projectFullPath"));
        assertEquals(List.of(Map.of("username", "a", "name", "A")), result.get("assignees"));
        assertEquals(List.of(Map.of("username", "r", "name", "R")), result.get("reviewers"));
    }

    @Test
    void approvalWithAndWithoutApprovedField() {
        Map<String, Object> approvals = Map.of("approvals_left", 0,
                "approved_by", List.of(Map.of("user", Map.of("username", "u1"))));
        Map<String, Object> result = service.approvalResult(approvals, REST_MR, "g/p");
        assertEquals(true, result.get("approved"));
        assertEquals(0, result.get("approvalsLeft"));
        assertEquals(List.of("u1"), result.get("approvedBy"));
        assertEquals("main", result.get("targetBranch"));

        Map<String, Object> explicit = service.approvalResult(Map.of("approved", false, "approvals_left", 1,
                "approved_by", List.of()), REST_MR, "g/p");
        assertEquals(false, explicit.get("approved"));
        assertEquals(List.of(), explicit.get("approvedBy"));

        assertNull(service.approvalResult(Map.of(), REST_MR, "g/p").get("approved"));
    }

    @Test
    void pipelineAndJobResults() {
        Map<String, Object> pipeline = service.pipelineWriteResult(Map.of("id", 900, "iid", 3, "status", "pending",
                "ref", "main", "sha", "abc", "web_url", "https://gitlab.example.com/p/-/pipelines/900"), "g/p");
        assertEquals(Map.of("pipelineIid", 3, "status", "pending", "ref", "main", "sha", "abc",
                "webUrl", "https://gitlab.example.com/p/-/pipelines/900", "projectFullPath", "g/p"), pipeline);

        Map<String, Object> job = service.jobWriteResult(Map.of("id", 55, "name", "quick", "status", "PENDING",
                "web_url", "https://gitlab.example.com/p/-/jobs/55"), 3, "g/p");
        assertEquals(Map.of("jobName", "quick", "status", "pending", "pipelineIid", 3,
                "webUrl", "https://gitlab.example.com/p/-/jobs/55", "projectFullPath", "g/p"), job);
    }
}
