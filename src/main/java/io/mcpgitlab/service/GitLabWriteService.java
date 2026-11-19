package io.mcpgitlab.service;

import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.mcpgitlab.client.GitLabRestClientBase;
import io.mcpgitlab.client.GitLabRestReadClient;
import io.mcpgitlab.client.GitLabRestWriteClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Implements normalized MCP write contracts over GitLab REST v4 (with GraphQL lookups).
 *
 * <p>Creates and updates issues, merge requests, comments, and approvals, and starts,
 * retries, or cancels pipelines and jobs. Shared
 * transport, value normalization, input validation, and the error-handling boundary
 * are inherited from {@link GitLabServiceBase}.
 *
 * <p>Exposed through dedicated MCP tools that are published only when
 * {@code gitlab.write.enabled=true}. Public write behavior and limits are documented in the README.
 */
@ApplicationScoped
public class GitLabWriteService extends GitLabServiceBase {

    @Inject
    GitLabRestWriteClient gitLabRestWriteClient;

    @Inject
    GitLabRestReadClient gitLabRestReadClient;

    /**
     * Creates a new issue in a GitLab project.
     *
     * @param projectFullPath full project path (namespace/project)
     * @param title           issue title
     * @param description     issue description
     * @param labels          array of issue labels; null or an empty array is allowed
     * @param milestoneTitle  milestone title; null or an empty string means no milestone
     * @return normalized MCP response with fields: iid, title, labels, milestone, createdAt, webUrl, references, projectFullPath
     */
    public ToolResponse createIssue(String projectFullPath, String title, String description, String[] labels, String milestoneTitle) {
        return execute(() -> {
            if (labels != null && Arrays.stream(labels).anyMatch(label -> label == null || label.isBlank())) {
                throw new GitLabException("VALIDATION_ERROR", "label must not be empty");
            }
            required(title, "title");
            validateProject(projectFullPath);
            Integer milestoneId = optional(milestoneTitle) == null ? null : resolveMilestoneId(projectFullPath, milestoneTitle.trim());

            GitLabRestClientBase.RestResponse response = gitLabRestWriteClient.createIssue(privateToken, projectFullPath, title, description, labels, milestoneId);
            ensureWriteSuccess(response);
            Map<String, Object> issue = restObject(response);
            Map<String, Object> result = fields(issue, "iid", "title", "labels", "createdAt", "webUrl", "references");
            result.put("milestone", fields(object(issue.get("milestone")), "title"));
            result.put("projectFullPath", projectFullPath);
            return result;
        });
    }

    /**
     * Creates a new issue comment (note) in a GitLab project.
     *
     * @param projectFullPath full project path (namespace/project)
     * @param iid             project-scoped issue IID
     * @param body            comment body
     * @return normalized MCP response with fields: id, noteable_iid, createdAt
     */
    public ToolResponse createIssueNote(String projectFullPath, Integer iid, String body) {
        return execute(() -> {
            validateIid(iid);
            required(body, "body");
            validateProject(projectFullPath);

            GitLabRestClientBase.RestResponse response = gitLabRestWriteClient.createIssueNote(privateToken, projectFullPath, iid, body);
            ensureWriteSuccess(response);
            Map<String, Object> result = fields(restObject(response), "id", "noteable_iid", "createdAt");
            result.put("projectFullPath", projectFullPath);
            result.put("iid", iid);
            return result;
        });
    }

    /**
     * Adds or removes issue labels in a GitLab project.
     *
     * @param projectFullPath full project path (namespace/project)
     * @param iid             project-scoped issue IID
     * @param action          action: "add_labels" or "remove_labels"
     * @param labels          comma-separated labels
     * @return normalized MCP response with fields: iid, labels, updatedAt, projectFullPath
     */
    public ToolResponse modifyIssueLabels(String projectFullPath, Integer iid, String action, String labels) {
        return execute(() -> {
            validateIid(iid);
            required(action, "action");
            if (!"add_labels".equals(action) && !"remove_labels".equals(action)) {
                throw new GitLabException("VALIDATION_ERROR", "action must be add_labels or remove_labels");
            }
            required(labels, "labels");
            validateProject(projectFullPath);

            GitLabRestClientBase.RestResponse response = gitLabRestWriteClient.modifyIssueLabels(privateToken, projectFullPath, iid, action, labels);
            ensureWriteSuccess(response);
            Map<String, Object> result = fields(restObject(response), "iid", "labels", "updatedAt");
            result.put("projectFullPath", projectFullPath);
            return result;
        });
    }

    /**
     * Replaces all issue assignees in a GitLab project.
     *
     * @param projectFullPath full project path (namespace/project)
     * @param iid             project-scoped issue IID
     * @param usernames       assignee usernames; null or an empty array removes all assignees
     * @return normalized MCP response with fields: iid, assignees, updatedAt, projectFullPath
     */
    public ToolResponse assignIssue(String projectFullPath, Integer iid, String[] usernames) {
        return execute(() -> {
            validateIid(iid);
            validateProject(projectFullPath);

            List<Integer> assigneeIds = new ArrayList<>();
            if (usernames != null) {
                for (String username : usernames) {
                    if (username == null || username.isBlank()) {
                        throw new GitLabException("VALIDATION_ERROR", "assignee must not be empty");
                    }
                    assigneeIds.add(resolveUserId(username.trim()));
                }
            }

            GitLabRestClientBase.RestResponse response = gitLabRestWriteClient.assignIssue(privateToken, projectFullPath, iid, assigneeIds);
            ensureWriteSuccess(response);
            Map<String, Object> issue = restObject(response);
            Map<String, Object> result = fields(issue, "iid", "updatedAt");
            result.put("assignees", people(issue.get("assignees")));
            result.put("projectFullPath", projectFullPath);
            return result;
        });
    }

    /**
     * Assigns or removes an issue milestone in a GitLab project.
     *
     * @param projectFullPath full project path (namespace/project)
     * @param iid             project-scoped issue IID
     * @param milestoneTitle  milestone title; null or an empty string removes the milestone
     * @return normalized MCP response with fields: iid, milestone, updatedAt, projectFullPath
     */
    public ToolResponse assignIssueMilestone(String projectFullPath, Integer iid, String milestoneTitle) {
        return execute(() -> {
            validateIid(iid);
            validateProject(projectFullPath);
            Integer milestoneId = optional(milestoneTitle) == null ? null : resolveMilestoneId(projectFullPath, milestoneTitle.trim());

            GitLabRestClientBase.RestResponse response = gitLabRestWriteClient.assignIssueMilestone(privateToken, projectFullPath, iid, milestoneId);
            ensureWriteSuccess(response);
            Map<String, Object> issue = restObject(response);
            Map<String, Object> result = fields(issue, "iid", "updatedAt");
            result.put("milestone", fields(object(issue.get("milestone")), "title"));
            result.put("projectFullPath", projectFullPath);
            return result;
        });
    }

    // ******************** Merge requests ********************

    /**
     * Creates a merge request.
     *
     * @return MR write result with assignees/reviewers or an MCP error
     */
    public ToolResponse createMergeRequest(String projectFullPath, String sourceBranch, String targetBranch, String title,
                                           String description, String[] assignees, String[] reviewers, String[] labels,
                                           Boolean removeSourceBranch) {
        return execute(() -> {
            validateProject(projectFullPath);
            required(sourceBranch, "sourceBranch");
            required(targetBranch, "targetBranch");
            required(title, "title");
            validateNonBlank(labels, "label");
            Map<String, Object> body = map();
            body.put("source_branch", sourceBranch);
            body.put("target_branch", targetBranch);
            body.put("title", title);
            putIfPresent(body, "description", optional(description));
            putIfPresent(body, "assignee_ids", resolveUserIds(assignees, "assignee"));
            putIfPresent(body, "reviewer_ids", resolveUserIds(reviewers, "reviewer"));
            putIfPresent(body, "labels", labels == null || labels.length == 0 ? null : String.join(",", labels));
            putIfPresent(body, "remove_source_branch", removeSourceBranch);
            Map<String, Object> mergeRequest = writeObject(gitLabRestWriteClient.createMergeRequest(privateToken, projectFullPath, body));
            return mergeRequestPeopleResult(mergeRequest, projectFullPath);
        });
    }

    /**
     * Partially updates a merge request: only the supplied fields are sent.
     *
     * @return MR write result with assignees/reviewers or an MCP error
     */
    public ToolResponse updateMergeRequest(String projectFullPath, Integer iid, String title, String description,
                                           String stateEvent, String targetBranch, String[] assignees, String[] reviewers) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(iid);
            if (optional(stateEvent) != null && !"close".equals(stateEvent) && !"reopen".equals(stateEvent)) {
                throw new GitLabException("VALIDATION_ERROR", "stateEvent must be close or reopen");
            }
            Map<String, Object> body = map();
            putIfPresent(body, "title", nonBlankIfPresent(title, "title"));
            putIfPresent(body, "description", description);
            putIfPresent(body, "state_event", optional(stateEvent));
            putIfPresent(body, "target_branch", nonBlankIfPresent(targetBranch, "targetBranch"));
            putIfPresent(body, "assignee_ids", resolveUserIds(assignees, "assignee"));
            putIfPresent(body, "reviewer_ids", resolveUserIds(reviewers, "reviewer"));
            if (body.isEmpty()) {
                throw new GitLabException("VALIDATION_ERROR", "Specify at least one field to change");
            }
            Map<String, Object> mergeRequest = writeObject(gitLabRestWriteClient.updateMergeRequest(privateToken, projectFullPath, iid, body));
            return mergeRequestPeopleResult(mergeRequest, projectFullPath);
        });
    }

    /**
     * Merges a merge request; {@code sha} protects against merging an unseen HEAD.
     *
     * @return MR write result with mergeCommitSha or an MCP error
     */
    public ToolResponse mergeMergeRequest(String projectFullPath, Integer iid, String mergeCommitMessage,
                                          Boolean removeSourceBranch, String sha) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(iid);
            Map<String, Object> body = map();
            putIfPresent(body, "merge_commit_message", optional(mergeCommitMessage));
            body.put("should_remove_source_branch", Boolean.TRUE.equals(removeSourceBranch));
            putIfPresent(body, "sha", nonBlankIfPresent(sha, "sha"));
            Map<String, Object> mergeRequest = writeObject(gitLabRestWriteClient.mergeMergeRequest(privateToken, projectFullPath, iid, body));
            Map<String, Object> result = mergeRequestWriteResult(mergeRequest, projectFullPath);
            result.put("mergeCommitSha", value(mergeRequest, "mergeCommitSha"));
            return result;
        });
    }

    /**
     * Approves a merge request on behalf of the service token owner.
     *
     * @return MR write result with approval state or an MCP error
     */
    public ToolResponse approveMergeRequest(String projectFullPath, Integer iid) {
        return approval(projectFullPath, iid, true);
    }

    /**
     * Revokes the service token owner's merge request approval.
     *
     * @return MR write result with approval state or an MCP error
     */
    public ToolResponse unapproveMergeRequest(String projectFullPath, Integer iid) {
        return approval(projectFullPath, iid, false);
    }

    /**
     * Creates a merge request comment.
     *
     * @return normalized note: id, noteable_iid, iid, createdAt, projectFullPath
     */
    public ToolResponse createMergeRequestNote(String projectFullPath, Integer iid, String body) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(iid);
            required(body, "body");
            Map<String, Object> note = writeObject(gitLabRestWriteClient.createMergeRequestNote(privateToken, projectFullPath, iid, body));
            Map<String, Object> result = fields(note, "id", "noteable_iid", "createdAt");
            result.put("projectFullPath", projectFullPath);
            result.put("iid", iid);
            return result;
        });
    }

    // ******************** CI/CD ********************

    /**
     * Starts a pipeline for a ref with env_var variables.
     *
     * @param variables {key, value} pairs; null means no variables
     * @return pipeline write result or an MCP error
     */
    public ToolResponse createPipeline(String projectFullPath, String ref, List<Map<String, String>> variables) {
        return execute(() -> {
            validateProject(projectFullPath);
            required(ref, "ref");
            Map<String, Object> body = map();
            body.put("ref", ref);
            if (variables != null && !variables.isEmpty()) {
                List<Object> values = new ArrayList<>();
                for (Map<String, String> variable : variables) {
                    String key = variable == null ? null : variable.get("key");
                    if (key == null || key.isBlank()) {
                        throw new GitLabException("VALIDATION_ERROR", "variable key is required");
                    }
                    Map<String, Object> item = map();
                    item.put("key", key);
                    item.put("value", variable.get("value") == null ? "" : variable.get("value"));
                    item.put("variable_type", "env_var");
                    values.add(item);
                }
                body.put("variables", values);
            }
            return pipelineWriteResult(writeObject(gitLabRestWriteClient.createPipeline(privateToken, projectFullPath, body)), projectFullPath);
        });
    }

    /**
     * Retries failed/canceled pipeline jobs.
     *
     * @return pipeline write result or an MCP error
     */
    public ToolResponse retryPipeline(String projectFullPath, Integer pipelineIid) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            int pipelineId = resolvePipelineId(projectFullPath, pipelineIid);
            return pipelineWriteResult(writeObject(gitLabRestWriteClient.retryPipeline(privateToken, projectFullPath, pipelineId)), projectFullPath);
        });
    }

    /**
     * Cancels active pipeline jobs.
     *
     * @return pipeline write result or an MCP error
     */
    public ToolResponse cancelPipeline(String projectFullPath, Integer pipelineIid) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            int pipelineId = resolvePipelineId(projectFullPath, pipelineIid);
            return pipelineWriteResult(writeObject(gitLabRestWriteClient.cancelPipeline(privateToken, projectFullPath, pipelineId)), projectFullPath);
        });
    }

    /**
     * Retries a pipeline job by name; GitLab creates a new attempt with the same name.
     *
     * @return job write result or an MCP error
     */
    public ToolResponse retryJob(String projectFullPath, Integer pipelineIid, String jobName) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            required(jobName, "jobName");
            int jobId = resolveJobId(projectFullPath, pipelineIid, jobName);
            return jobWriteResult(writeObject(gitLabRestWriteClient.retryJob(privateToken, projectFullPath, jobId)), pipelineIid, projectFullPath);
        });
    }

    // ******************** helpers ********************

    private ToolResponse approval(String projectFullPath, Integer iid, boolean approve) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(iid);
            GitLabRestClientBase.RestResponse response = approve
                    ? gitLabRestWriteClient.approveMergeRequest(privateToken, projectFullPath, iid)
                    : gitLabRestWriteClient.unapproveMergeRequest(privateToken, projectFullPath, iid);
            Map<String, Object> approvals = writeObject(response);
            Map<String, Object> mergeRequest = restObject(gitLabRestWriteClient.getMergeRequest(privateToken, projectFullPath, iid));
            return approvalResult(approvals, mergeRequest, projectFullPath);
        });
    }

    private Map<String, Object> writeObject(GitLabRestClientBase.RestResponse response) {
        ensureWriteSuccess(response);
        return response.body() == null || response.body().isBlank() ? map() : restObject(response);
    }

    private int resolvePipelineId(String projectFullPath, int pipelineIid) {
        Map<String, Object> project = graphQL("""
                query PipelineId($fullPath: ID!, $iid: ID) {
                  project(fullPath: $fullPath) { pipeline(iid: $iid) { id } }
                }
                """, vars("fullPath", projectFullPath, "iid", Integer.toString(pipelineIid)), "project");
        Map<String, Object> pipeline = object(project.get("pipeline"));
        if (pipeline.isEmpty()) {
            throw new GitLabException("NOT_FOUND", "Pipeline not found");
        }
        return numericId(pipeline.get("id"));
    }

    private int resolveJobId(String projectFullPath, int pipelineIid, String jobName) {
        Map<String, Object> project = graphQL("""
                query PipelineJobId($fullPath: ID!, $iid: ID, $jobName: String) {
                  project(fullPath: $fullPath) { pipeline(iid: $iid) { job(name: $jobName) { id } } }
                }
                """, vars("fullPath", projectFullPath, "iid", Integer.toString(pipelineIid), "jobName", jobName), "project");
        Map<String, Object> pipeline = object(project.get("pipeline"));
        if (pipeline.isEmpty()) {
            throw new GitLabException("NOT_FOUND", "Pipeline not found");
        }
        Map<String, Object> job = object(pipeline.get("job"));
        if (job.isEmpty()) {
            throw new GitLabException("NOT_FOUND", "Job not found");
        }
        return numericId(job.get("id"));
    }

    /** null means the field is not sent; an empty array sends an empty id list (removes everyone). */
    private List<Integer> resolveUserIds(String[] usernames, String label) {
        if (usernames == null) {
            return null;
        }
        validateNonBlank(usernames, label);
        List<Integer> ids = new ArrayList<>();
        for (String username : usernames) {
            ids.add(resolveUserId(username.trim()));
        }
        return ids;
    }

    private void validateNonBlank(String[] values, String label) {
        if (values != null && Arrays.stream(values).anyMatch(value -> value == null || value.isBlank())) {
            throw new GitLabException("VALIDATION_ERROR", label + " must not be empty");
        }
    }

    /**
     * Optional parameter: {@code null} means not supplied; supplied blank means {@code VALIDATION_ERROR}
     * (otherwise a blank value would silently drop out of the request, e.g. removing the merge {@code sha} guard).
     */
    private String nonBlankIfPresent(String value, String name) {
        if (value != null && value.isBlank()) {
            throw new GitLabException("VALIDATION_ERROR", name + " must not be blank");
        }
        return value;
    }

    private void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    Map<String, Object> mergeRequestWriteResult(Map<String, Object> source, String projectFullPath) {
        Map<String, Object> result = fields(source, "iid", "title", "state", "sourceBranch", "targetBranch", "webUrl", "updatedAt");
        result.put("projectFullPath", projectFullPath);
        return result;
    }

    Map<String, Object> mergeRequestPeopleResult(Map<String, Object> source, String projectFullPath) {
        Map<String, Object> result = mergeRequestWriteResult(source, projectFullPath);
        result.put("assignees", people(source.get("assignees")));
        result.put("reviewers", people(source.get("reviewers")));
        return result;
    }

    Map<String, Object> approvalResult(Map<String, Object> approvals, Map<String, Object> mergeRequest, String projectFullPath) {
        Map<String, Object> result = mergeRequestWriteResult(mergeRequest, projectFullPath);
        Object approvalsLeft = value(approvals, "approvalsLeft");
        Object approved = value(approvals, "approved");
        result.put("approved", approved instanceof Boolean flag ? flag
                : approvalsLeft instanceof Number left ? left.intValue() == 0 : null);
        result.put("approvalsLeft", approvalsLeft);
        List<Object> approvedBy = new ArrayList<>();
        for (Map<String, Object> entry : objects(approvals.get("approved_by"))) {
            approvedBy.add(value(object(entry.get("user")), "username"));
        }
        result.put("approvedBy", approvedBy);
        return result;
    }

    Map<String, Object> pipelineWriteResult(Map<String, Object> source, String projectFullPath) {
        Map<String, Object> result = map();
        result.put("pipelineIid", value(source, "iid"));
        result.put("status", lower(value(source, "status")));
        result.put("ref", value(source, "ref"));
        result.put("sha", value(source, "sha"));
        result.put("webUrl", value(source, "webUrl"));
        result.put("projectFullPath", projectFullPath);
        return result;
    }

    Map<String, Object> jobWriteResult(Map<String, Object> source, Integer pipelineIid, String projectFullPath) {
        Map<String, Object> result = map();
        result.put("jobName", value(source, "name"));
        result.put("status", lower(value(source, "status")));
        result.put("pipelineIid", pipelineIid);
        result.put("webUrl", value(source, "webUrl"));
        result.put("projectFullPath", projectFullPath);
        return result;
    }


    /**
     * Resolves a milestone title to an ID using the project milestone list.
     */
    private Integer resolveMilestoneId(String projectFullPath, String title) {
        List<Map<String, Object>> milestones = restArray(gitLabRestReadClient.listProjectMilestones(privateToken, projectFullPath, 100, 1));
        for (Map<String, Object> milestone : milestones) {
            if (title.equals(milestone.get("title"))) {
                return (Integer) milestone.get("id");
            }
        }
        throw new GitLabException("NOT_FOUND", "Milestone not found: " + title);
    }

    /**
     * Resolves a username to a user ID using an exact search.
     */
    private Integer resolveUserId(String username) {
        List<Map<String, Object>> users = restArray(gitLabRestReadClient.getUserByUsername(privateToken, username));
        if (users.isEmpty()) {
            throw new GitLabException("NOT_FOUND", "User not found: " + username);
        }
        return (Integer) users.get(0).get("id");
    }
}