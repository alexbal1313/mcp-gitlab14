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
 * Implements normalized MCP issue write contracts over GitLab REST v4.
 *
 * <p>Creates issues and comments and updates issue assignments and labels. Shared
 * transport, value normalization, input validation, and the error-handling boundary
 * are inherited from {@link GitLabServiceBase}.
 *
 * <p>Exposed through dedicated MCP tools without a separate profile.
 * Public write behavior and limits are documented in the README.
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