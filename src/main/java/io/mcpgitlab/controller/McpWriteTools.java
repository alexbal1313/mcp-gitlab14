package io.mcpgitlab.controller;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;
import io.mcpgitlab.service.GitLabWriteService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exposes the GitLab v1 MCP write tools: issues, merge requests, and CI/CD.
 *
 * <p>Visibility is controlled by the {@code gitlab.write.enabled} flag through {@link WriteToolFilter}.
 * Every {@code @Tool} in this class must be listed in {@link #NAMES}; a unit test enforces this.
 */
public class McpWriteTools {

    /** Names of all write tools; the filter hides them when the flag is off. */
    public static final Set<String> NAMES = Set.of(
            "gitlab_create_issue", "gitlab_create_issue_note", "gitlab_modify_issue_labels",
            "gitlab_assign_issue", "gitlab_assign_issue_milestone",
            "gitlab_create_merge_request", "gitlab_update_merge_request", "gitlab_merge_merge_request",
            "gitlab_approve_merge_request", "gitlab_unapprove_merge_request", "gitlab_create_merge_request_note",
            "gitlab_create_pipeline", "gitlab_retry_pipeline", "gitlab_cancel_pipeline", "gitlab_retry_job");

    /** Allowed merge request state transitions. */
    public enum StateEvent {
        close,
        reopen
    }

    /** Pipeline variable (env_var type). */
    public record PipelineVariable(String key, String value) {
    }

    @Inject
    GitLabWriteService gitLabWriteService;

    /** Creates a new issue. */
    @Tool(name = "gitlab_create_issue", description = "Creates a new GitLab issue.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse createIssue(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Issue title.") String title,
            @ToolArg(description = "Issue body.", required = false) String description,
            @ToolArg(description = "Issue labels.", required = false) String[] labels,
            @ToolArg(description = "Project milestone title.", required = false) String milestone) {
        return gitLabWriteService.createIssue(projectFullPath, title, description, labels, milestone);
    }

    /** Creates a new issue comment (note). */
    @Tool(name = "gitlab_create_issue_note", description = "Creates a new GitLab issue comment (note).", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse createIssueNote(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped issue IID.") Integer iid,
            @ToolArg(description = "Comment body.") String body) {
        return gitLabWriteService.createIssueNote(projectFullPath, iid, body);
    }

    /** Adds or removes issue labels. */
    @Tool(name = "gitlab_modify_issue_labels", description = "Adds or removes GitLab issue labels.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse modifyIssueLabels(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped issue IID.") Integer iid,
            @ToolArg(description = "Action: add_labels to add labels, remove_labels to remove labels.") McpTools.IssueLabelAction action,
            @ToolArg(description = "Comma-separated issue labels.") String labels) {
        return gitLabWriteService.modifyIssueLabels(projectFullPath, iid, action == null ? null : action.name(), labels);
    }

    /** Replaces all issue assignees. */
    @Tool(name = "gitlab_assign_issue", description = "Assigns GitLab issue assignees, fully replacing the current list.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse assignIssue(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped issue IID.") Integer iid,
            @ToolArg(description = "Assignee usernames; an empty array removes all assignees.", required = false) String[] assignees) {
        return gitLabWriteService.assignIssue(projectFullPath, iid, assignees);
    }

    /** Assigns or removes an issue milestone. */
    @Tool(name = "gitlab_assign_issue_milestone", description = "Assigns or removes a GitLab issue milestone.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse assignIssueMilestone(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped issue IID.") Integer iid,
            @ToolArg(description = "Project milestone title; an empty value removes the milestone.", required = false) String milestone) {
        return gitLabWriteService.assignIssueMilestone(projectFullPath, iid, milestone);
    }

    /** Creates a merge request. */
    @Tool(name = "gitlab_create_merge_request", description = "Creates a merge request from sourceBranch into targetBranch.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse createMergeRequest(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Source branch.") String sourceBranch,
            @ToolArg(description = "Target branch.") String targetBranch,
            @ToolArg(description = "Merge request title.") String title,
            @ToolArg(description = "Merge request description.", required = false) String description,
            @ToolArg(description = "Assignee usernames.", required = false) String[] assignees,
            @ToolArg(description = "Reviewer usernames.", required = false) String[] reviewers,
            @ToolArg(description = "Merge request labels.", required = false) String[] labels,
            @ToolArg(description = "Remove the source branch after merge.", required = false) Boolean removeSourceBranch) {
        return gitLabWriteService.createMergeRequest(projectFullPath, sourceBranch, targetBranch, title, description,
                assignees, reviewers, labels, removeSourceBranch);
    }

    /** Partially updates a merge request. */
    @Tool(name = "gitlab_update_merge_request", description = "Partially updates a merge request: title, description, state, target branch, assignees, reviewers.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse updateMergeRequest(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid,
            @ToolArg(description = "New title.", required = false) String title,
            @ToolArg(description = "New description.", required = false) String description,
            @ToolArg(description = "close to close, reopen to reopen.", required = false) StateEvent stateEvent,
            @ToolArg(description = "New target branch.", required = false) String targetBranch,
            @ToolArg(description = "Assignee usernames; replace the whole list, an empty array removes everyone.", required = false) String[] assignees,
            @ToolArg(description = "Reviewer usernames; replace the whole list, an empty array removes everyone.", required = false) String[] reviewers) {
        return gitLabWriteService.updateMergeRequest(projectFullPath, iid, title, description,
                stateEvent == null ? null : stateEvent.name(), targetBranch, assignees, reviewers);
    }

    /** Merges a merge request. */
    @Tool(name = "gitlab_merge_merge_request", description = "Merges a merge request; sha protects against merging a changed HEAD.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse mergeMergeRequest(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid,
            @ToolArg(description = "Merge commit message.", required = false) String mergeCommitMessage,
            @ToolArg(description = "Remove the source branch after merge; defaults to false.", required = false) Boolean removeSourceBranch,
            @ToolArg(description = "Expected source branch HEAD SHA; the merge is rejected on mismatch.", required = false) String sha) {
        return gitLabWriteService.mergeMergeRequest(projectFullPath, iid, mergeCommitMessage, removeSourceBranch, sha);
    }

    /** Approves a merge request. */
    @Tool(name = "gitlab_approve_merge_request", description = "Approves a merge request on behalf of the service token owner.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse approveMergeRequest(@ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath, @ToolArg(description = "Project-scoped merge request IID.") Integer iid) {
        return gitLabWriteService.approveMergeRequest(projectFullPath, iid);
    }

    /** Revokes a merge request approval. */
    @Tool(name = "gitlab_unapprove_merge_request", description = "Revokes the service token owner's merge request approval.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse unapproveMergeRequest(@ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath, @ToolArg(description = "Project-scoped merge request IID.") Integer iid) {
        return gitLabWriteService.unapproveMergeRequest(projectFullPath, iid);
    }

    /** Creates a merge request comment. */
    @Tool(name = "gitlab_create_merge_request_note", description = "Creates a merge request comment (note).", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse createMergeRequestNote(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid,
            @ToolArg(description = "Comment body.") String body) {
        return gitLabWriteService.createMergeRequestNote(projectFullPath, iid, body);
    }

    /** Starts a pipeline. */
    @Tool(name = "gitlab_create_pipeline", description = "Starts a pipeline for a ref with optional variables.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse createPipeline(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Branch, tag, or SHA.") String ref,
            @ToolArg(description = "Pipeline variables: an array of {key, value} (env_var type).", required = false) PipelineVariable[] variables) {
        List<Map<String, String>> values = null;
        if (variables != null) {
            values = new ArrayList<>();
            for (PipelineVariable variable : variables) {
                Map<String, String> item = new HashMap<>();
                if (variable != null) {
                    item.put("key", variable.key());
                    item.put("value", variable.value());
                }
                values.add(item);
            }
        }
        return gitLabWriteService.createPipeline(projectFullPath, ref, values);
    }

    /** Retries a pipeline. */
    @Tool(name = "gitlab_retry_pipeline", description = "Retries failed/canceled pipeline jobs.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse retryPipeline(@ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath, @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid) {
        return gitLabWriteService.retryPipeline(projectFullPath, pipelineIid);
    }

    /** Cancels a pipeline. */
    @Tool(name = "gitlab_cancel_pipeline", description = "Cancels active pipeline jobs.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse cancelPipeline(@ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath, @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid) {
        return gitLabWriteService.cancelPipeline(projectFullPath, pipelineIid);
    }

    /** Retries a job. */
    @Tool(name = "gitlab_retry_job", description = "Retries a pipeline job by name; a new attempt with the same name is created.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse retryJob(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid,
            @ToolArg(description = "Job name in the pipeline.") String jobName) {
        return gitLabWriteService.retryJob(projectFullPath, pipelineIid, jobName);
    }
}
