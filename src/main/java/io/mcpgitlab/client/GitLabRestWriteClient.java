package io.mcpgitlab.client;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
}