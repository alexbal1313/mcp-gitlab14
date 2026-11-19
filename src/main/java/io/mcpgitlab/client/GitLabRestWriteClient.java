package io.mcpgitlab.client;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST v4 adapter for GitLab WRITE operations: POST, PUT, and DELETE with a JSON body.
 *
 * <p>Separated from the {@link GitLabRestReadClient} read adapter to isolate the
 * risk of operations that modify GitLab state. Shared HTTP transport, the response type
 * {@link GitLabRestClientBase.RestResponse}, and the transport error
 * {@link GitLabRestClientBase.GitLabRestException} are inherited from
 * {@link GitLabRestClientBase}.
 *
 * <p>Provides issue write endpoints and transport building blocks used by the
 * write service.
 */
@ApplicationScoped
public class GitLabRestWriteClient extends GitLabRestClientBase {

    /**
     * Sends a POST with a JSON body. Returns the unparsed GitLab response so
     * the calling service can normalize the success or error body.
     *
     * @param body       request body to serialize as JSON
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse postJson(String privateToken, String path, Object body, Object... parameters) {
        return post(privateToken, path, body, parameters);
    }

    /**
     * Sends a PUT with a JSON body. Returns the unparsed GitLab response.
     *
     * @param body       request body to serialize as JSON
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse putJson(String privateToken, String path, Object body, Object... parameters) {
        return put(privateToken, path, body, parameters);
    }

    /**
     * Sends a DELETE. Returns the unparsed GitLab response.
     *
     * @param parameters alternating parameter name/value pairs; null values are omitted
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse deletePath(String privateToken, String path, Object... parameters) {
        return delete(privateToken, path, parameters);
    }

    /**
     * Creates a new issue
     *
     * @param privateToken    - GitLab token
     * @param projectFullPath - full project path
     * @param title           - issue title
     * @param description     - issue body
     * @return
     */
    public RestResponse createIssue(String privateToken, String projectFullPath, String title, String description, String[] labels, Integer milestoneId) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        if (labels != null && labels.length > 0) {
            body.put("labels", String.join(",", Arrays.stream(labels).filter(s -> s != null && !s.isBlank()).toList()));
        }
        if (milestoneId != null) {
            body.put("milestone_id", milestoneId);
        }
        return post(
                privateToken,
                projectPath(projectFullPath) + "/issues",
                body);
    }

    public RestResponse createIssueNote(String privateToken, String projectFullPath, Integer iid, String body) {
        HashMap<String, String> noteBody = new HashMap<>();
        noteBody.put("body", body);
        return post(
                privateToken,
                projectPath(projectFullPath) + "/issues/" + iid + "/notes",
                noteBody);
    }

    /**
     * Adds or removes issue labels.
     *
     * @param privateToken    - GitLab token
     * @param projectFullPath - full project path
     * @param iid             - project-scoped issue IID
     * @param action          - "add_labels" or "remove_labels"
     * @param labels          - comma-separated labels
     * @return GitLab HTTP response
     */
    public RestResponse modifyIssueLabels(String privateToken, String projectFullPath, Integer iid, String action, String labels) {
        HashMap<String, String> body = new HashMap<>();
        body.put(action, labels);
        return put(
                privateToken,
                projectPath(projectFullPath) + "/issues/" + iid,
                body);
    }

    /**
     * Replaces all issue assignees.
     *
     * @param privateToken    - GitLab token
     * @param projectFullPath - full project path
     * @param iid             - project-scoped issue IID
     * @param assigneeIds     - assignee IDs; an empty list removes all assignees
     * @return GitLab HTTP response
     */
    public RestResponse assignIssue(String privateToken, String projectFullPath, Integer iid, List<Integer> assigneeIds) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("assignee_ids", assigneeIds);
        return put(
                privateToken,
                projectPath(projectFullPath) + "/issues/" + iid,
                body);
    }

    /**
     * Assigns or removes an issue milestone.
     *
     * @param privateToken    - GitLab token
     * @param projectFullPath - full project path
     * @param iid             - project-scoped issue IID
     * @param milestoneId     - milestone ID; null removes the milestone
     * @return GitLab HTTP response
     */
    public RestResponse assignIssueMilestone(String privateToken, String projectFullPath, Integer iid, Integer milestoneId) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("milestone_id", milestoneId);
        return put(
                privateToken,
                projectPath(projectFullPath) + "/issues/" + iid,
                body);
    }

    /**
     * Creates a merge request; the service builds the body (only supplied fields).
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse createMergeRequest(String privateToken, String projectFullPath, Map<String, Object> body) {
        return post(privateToken, projectPath(projectFullPath) + "/merge_requests", body);
    }

    /**
     * Partially updates a merge request; the body contains only the fields to change.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse updateMergeRequest(String privateToken, String projectFullPath, int iid, Map<String, Object> body) {
        return put(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid, body);
    }

    /**
     * Merges a merge request.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse mergeMergeRequest(String privateToken, String projectFullPath, int iid, Map<String, Object> body) {
        return put(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid + "/merge", body);
    }

    /**
     * Approves a merge request on behalf of the token owner.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse approveMergeRequest(String privateToken, String projectFullPath, int iid) {
        return post(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid + "/approve", null);
    }

    /**
     * Revokes the token owner's merge request approval.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse unapproveMergeRequest(String privateToken, String projectFullPath, int iid) {
        return post(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid + "/unapprove", null);
    }

    /**
     * Creates a merge request comment.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse createMergeRequestNote(String privateToken, String projectFullPath, int iid, String body) {
        return post(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid + "/notes", Map.of("body", body));
    }

    /**
     * Starts a pipeline for a ref.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse createPipeline(String privateToken, String projectFullPath, Map<String, Object> body) {
        return post(privateToken, projectPath(projectFullPath) + "/pipeline", body);
    }

    /**
     * Retries failed/canceled pipeline jobs by numeric pipeline id.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse retryPipeline(String privateToken, String projectFullPath, int pipelineId) {
        return post(privateToken, projectPath(projectFullPath) + "/pipelines/" + pipelineId + "/retry", null);
    }

    /**
     * Cancels active pipeline jobs by numeric pipeline id.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse cancelPipeline(String privateToken, String projectFullPath, int pipelineId) {
        return post(privateToken, projectPath(projectFullPath) + "/pipelines/" + pipelineId + "/cancel", null);
    }

    /**
     * Retries a job by numeric id.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse retryJob(String privateToken, String projectFullPath, int jobId) {
        return post(privateToken, projectPath(projectFullPath) + "/jobs/" + jobId + "/retry", null);
    }

    /**
     * Returns a merge request (used to enrich approve/unapprove responses).
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getMergeRequest(String privateToken, String projectFullPath, int iid) {
        return get(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid);
    }
}
