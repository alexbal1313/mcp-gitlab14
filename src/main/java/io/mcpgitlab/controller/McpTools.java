package io.mcpgitlab.controller;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;
import io.mcpgitlab.service.GitLabReadService;

/**
 * Exposes the GitLab v1 MCP read tools. Write tools live in {@link McpWriteTools}
 * (published only when {@code gitlab.write.enabled=true}).
 */
public class McpTools {

    @Inject
    GitLabReadService gitLabReadService;

    /**
     * Allowed issue states in GitLab MCP contracts.
     */
    public enum IssueState {
        opened,
        closed,
        all
    }

    /**
     * Allowed merge request states in GitLab MCP contracts.
     */
    public enum MergeRequestState {
        opened,
        closed,
        merged,
        all
    }

    /**
     * Allowed pipeline and CI job states in GitLab MCP contracts.
     */
    public enum PipelineStatus {
        created,
        waiting_for_resource,
        preparing,
        pending,
        running,
        failed,
        success,
        canceled,
        skipped,
        manual,
        scheduled
    }

    /**
     * Allowed issue label actions in GitLab MCP contracts.
     */
    public enum IssueLabelAction {
        add_labels,
        remove_labels
    }

    /** Returns accessible projects. */
    @Tool(name = "gitlab_list_projects", description = "Returns a compact list of accessible GitLab projects.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjects(
            @ToolArg(description = "Project search string.", required = false) String query,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listProjects(query, items, afterCursor);
    }

    /** Returns project details. */
    @Tool(name = "gitlab_get_project", description = "Returns GitLab project details.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getProject(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath) {
        return gitLabReadService.getProject(projectFullPath);
    }

    /** Returns issues for a single project. */
    @Tool(name = "gitlab_list_project_issues", description = "Returns a compact list of issues in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectIssues(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Issue search string.", required = false) String query,
            @ToolArg(description = "State: opened, closed, or all.", required = false) IssueState state,
            @ToolArg(description = "Author username.", required = false) String authorUsername,
            @ToolArg(description = "Assignee username.", required = false) String assigneeUsername,
            @ToolArg(description = "Label name.", required = false) String label,
            @ToolArg(description = "Lower bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedAfter,
            @ToolArg(description = "Upper bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedBefore,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectIssues(projectFullPath, query, state == null ? null : state.name(), authorUsername, assigneeUsername, label,
                updatedAfter, updatedBefore, items, afterCursor);
    }

    /** Returns issue details. */
    @Tool(name = "gitlab_get_issue", description = "Returns issue details for a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getIssue(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped issue IID.") Integer iid) {
        return gitLabReadService.getIssue(projectFullPath, iid);
    }

    /** Performs server-side issue search across all accessible projects. */
    @Tool(name = "gitlab_search_issues", description = "Searches GitLab issues across all accessible projects.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse searchIssues(
            @ToolArg(description = "Issue search string.", required = false) String query,
            @ToolArg(description = "State: opened, closed, or all.", required = false) IssueState state,
            @ToolArg(description = "Author username.", required = false) String authorUsername,
            @ToolArg(description = "Assignee username.", required = false) String assigneeUsername,
            @ToolArg(description = "Comma-separated labels in GitLab REST format.", required = false) String labels,
            @ToolArg(description = "Lower bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedAfter,
            @ToolArg(description = "Upper bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedBefore,
            @ToolArg(description = "Lower bound for createdAt in ISO 8601 with a timezone.", required = false) String createdAfter,
            @ToolArg(description = "Upper bound for createdAt in ISO 8601 with a timezone.", required = false) String createdBefore,
            @ToolArg(description = "Milestone title; GitLab REST supports None or Any.", required = false) String milestone,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.searchIssues(query, state == null ? null : state.name(), authorUsername, assigneeUsername, labels,
                updatedAfter, updatedBefore, createdAfter, createdBefore, milestone, items, afterCursor);
    }

    /** Returns issue discussions with their notes. */
    @Tool(name = "gitlab_list_issue_discussions", description = "Returns issue discussions and notes for a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listIssueDiscussions(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped issue IID.") Integer iid,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listIssueDiscussions(projectFullPath, iid, items, afterCursor);
    }

    /** Returns merge requests for a single project. */
    @Tool(name = "gitlab_list_project_merge_requests", description = "Returns a compact list of merge requests in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectMergeRequests(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "State: opened, closed, merged, or all.", required = false) MergeRequestState state,
            @ToolArg(description = "Author username.", required = false) String authorUsername,
            @ToolArg(description = "Assignee username.", required = false) String assigneeUsername,
            @ToolArg(description = "Label name.", required = false) String label,
            @ToolArg(description = "Lower bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedAfter,
            @ToolArg(description = "Upper bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedBefore,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectMergeRequests(projectFullPath, state == null ? null : state.name(), authorUsername, assigneeUsername, label,
                updatedAfter, updatedBefore, items, afterCursor);
    }

    /** Returns merge request details. */
    @Tool(name = "gitlab_get_merge_request", description = "Returns merge request details for a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getMergeRequest(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid) {
        return gitLabReadService.getMergeRequest(projectFullPath, iid);
    }

    /** Returns the current merge request diff with pagination. */
    @Tool(name = "gitlab_get_merge_request_diff", description = "Returns the current GitLab merge request diff.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getMergeRequestDiff(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid,
            @ToolArg(description = "Number of files from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next diff page.", required = false) String afterCursor) {
        return gitLabReadService.getMergeRequestDiff(projectFullPath, iid, items, afterCursor);
    }

    /** Performs server-side merge request search across all accessible projects. */
    @Tool(name = "gitlab_search_merge_requests", description = "Searches GitLab merge requests across all accessible projects.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse searchMergeRequests(
            @ToolArg(description = "Merge request search string.", required = false) String query,
            @ToolArg(description = "State: opened, closed, merged, or all.", required = false) MergeRequestState state,
            @ToolArg(description = "Author username.", required = false) String authorUsername,
            @ToolArg(description = "Assignee username.", required = false) String assigneeUsername,
            @ToolArg(description = "Comma-separated labels in GitLab REST format.", required = false) String labels,
            @ToolArg(description = "Lower bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedAfter,
            @ToolArg(description = "Upper bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedBefore,
            @ToolArg(description = "Lower bound for createdAt in ISO 8601 with a timezone.", required = false) String createdAfter,
            @ToolArg(description = "Upper bound for createdAt in ISO 8601 with a timezone.", required = false) String createdBefore,
            @ToolArg(description = "Milestone title; GitLab REST supports None or Any.", required = false) String milestone,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.searchMergeRequests(query, state == null ? null : state.name(), authorUsername, assigneeUsername, labels, updatedAfter,
                updatedBefore, createdAfter, createdBefore, items, milestone, afterCursor);
    }

    /** Searches code snippets in a single project using the GitLab Search API. */
    @Tool(name = "gitlab_search_code", description = "Searches GitLab code snippets in the specified project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse searchCode(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Search string or a regular expression supported by GitLab.") String query,
            @ToolArg(description = "Branch or tag name; defaults to the project default branch.", required = false) String ref,
            @ToolArg(description = "Path or pattern supported by the GitLab Search API.", required = false) String path,
            @ToolArg(description = "File extension supported by the GitLab Search API.", required = false) String extension) {
        return gitLabReadService.searchCode(projectFullPath, query, ref, path, extension);
    }

    /** Returns a bounded range of text file lines from a specific revision. */
    @Tool(name = "gitlab_get_file_content", description = "Returns GitLab text file context from the specified revision.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getFileContent(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "File path within the repository.") String path,
            @ToolArg(description = "Commit SHA, branch name, or tag name.") String ref,
            @ToolArg(description = "First line of the range, starting at 1.", required = false) Integer startLine,
            @ToolArg(description = "Last line of the range, inclusive.", required = false) Integer endLine) {
        return gitLabReadService.getFileContent(projectFullPath, path, ref, startLine, endLine);
    }

    /** Returns merge request discussions with their notes. */
    @Tool(name = "gitlab_list_merge_request_discussions", description = "Returns merge request discussions and notes for a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listMergeRequestDiscussions(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listMergeRequestDiscussions(projectFullPath, iid, items, afterCursor);
    }

    /** Returns project commits. */
    @Tool(name = "gitlab_list_project_commits", description = "Returns a compact list of commits in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectCommits(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Branch or tag name to filter by.", required = false) String refName,
            @ToolArg(description = "Lower date bound in ISO 8601 with a timezone.", required = false) String since,
            @ToolArg(description = "Upper date bound in ISO 8601 with a timezone.", required = false) String until,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectCommits(projectFullPath, refName, since, until, items, afterCursor);
    }

    /** Returns a commit by SHA, branch, or tag. */
    @Tool(name = "gitlab_get_commit", description = "Returns GitLab commit details.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getCommit(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "SHA, branch name, or tag name.") String sha) {
        return gitLabReadService.getCommit(projectFullPath, sha);
    }

    /** Returns a commit diff with pagination. */
    @Tool(name = "gitlab_get_commit_diff", description = "Returns a GitLab commit diff.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getCommitDiff(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "SHA, branch name, or tag name.") String sha,
            @ToolArg(description = "Number of files from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next diff page.", required = false) String afterCursor) {
        return gitLabReadService.getCommitDiff(projectFullPath, sha, items, afterCursor);
    }

    /** Returns project tags. */
    @Tool(name = "gitlab_list_tags", description = "Returns a compact list of tags in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listTags(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listTags(projectFullPath, items, afterCursor);
    }

    /** Returns a project tag by name. */
    @Tool(name = "gitlab_get_tag", description = "Returns tag details for a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getTag(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Tag name.") String tagName) {
        return gitLabReadService.getTag(projectFullPath, tagName);
    }

    /** Returns files and folders in a repository directory; non-recursive by default. */
    @Tool(name = "gitlab_list_dir", description = "Returns a compact list of files and folders in a GitLab repository directory; non-recursive by default.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listDir(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Directory path within the repository; defaults to the root directory.", required = false) String path,
            @ToolArg(description = "Commit SHA, branch name, or tag name; defaults to the project default branch.", required = false) String ref,
            @ToolArg(description = "true returns entries from all nested levels; defaults to false.", required = false) Boolean recursive,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listDir(projectFullPath, path, ref, recursive, items, afterCursor);
    }

    @Tool(name = "gitlab_list_branches", description = "Returns a compact list of GitLab repository branches.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listBranches(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Branch name substring; ^ and $ anchor the start and end of the name.", required = false) String search,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listBranches(projectFullPath, search, items, afterCursor);
    }

    @Tool(name = "gitlab_get_branch", description = "Returns detailed information about a GitLab repository branch.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getBranch(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Branch name, for example main or feature/x.") String branchName) {
        return gitLabReadService.getBranch(projectFullPath, branchName);
    }

    @Tool(name = "gitlab_compare_refs", description = "Compares two refs (branch, tag, or SHA): commits and changed file paths without diff content.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse compareRefs(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Base ref: branch, tag, or SHA.") String from,
            @ToolArg(description = "Ref to compare: branch, tag, or SHA.") String to,
            @ToolArg(description = "Maximum number of commits from 1 to 20; defaults to 20.", required = false) Integer items) {
        return gitLabReadService.compareRefs(projectFullPath, from, to, items);
    }

    @Tool(name = "gitlab_list_project_members", description = "Returns direct and inherited GitLab project members with their access level.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectMembers(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Search string for name or username.", required = false) String search,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor of the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectMembers(projectFullPath, search, items, afterCursor);
    }

    @Tool(name = "gitlab_get_merge_request_commits", description = "Returns commits of a GitLab merge request.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getMergeRequestCommits(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped merge request IID.") Integer iid,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor of the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.getMergeRequestCommits(projectFullPath, iid, items, afterCursor);
    }

    @Tool(name = "gitlab_server_info", description = "Returns the GitLab version and instance address; a successful response confirms connectivity and token validity.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse serverInfo() {
        return gitLabReadService.serverInfo();
    }

    @Tool(name="gitlab_search_users", description = "Searches users by name or username. Only Latin letters are supported.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse searchUsers(
            @ToolArg(description = "User name or username in Latin letters, at least 3 characters.") String userName,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.searchUsers(userName, items, afterCursor);
    }

    /** Returns project pipelines. */
    @Tool(name = "gitlab_list_project_pipelines", description = "Returns a compact list of pipelines in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectPipelines(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Status: created, waiting_for_resource, preparing, pending, running, failed, success, canceled, skipped, manual, or scheduled.", required = false) PipelineStatus status,
            @ToolArg(description = "Branch or tag name.", required = false) String ref,
            @ToolArg(description = "Lower bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedAfter,
            @ToolArg(description = "Upper bound for updatedAt in ISO 8601 with a timezone.", required = false) String updatedBefore,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectPipelines(projectFullPath, status == null ? null : status.name(), ref,
                updatedAfter, updatedBefore, items, afterCursor);
    }

    /** Returns pipeline details. */
    @Tool(name = "gitlab_get_pipeline", description = "Returns pipeline details for a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getPipeline(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid) {
        return gitLabReadService.getPipeline(projectFullPath, pipelineIid);
    }

    /** Returns pipeline jobs. */
    @Tool(name = "gitlab_list_pipeline_jobs", description = "Returns a compact list of GitLab pipeline jobs.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listPipelineJobs(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid,
            @ToolArg(description = "Job status: created, waiting_for_resource, preparing, pending, running, failed, success, canceled, skipped, manual, or scheduled.", required = false) PipelineStatus status,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Cursor for the next GraphQL page.", required = false) String afterCursor) {
        return gitLabReadService.listPipelineJobs(projectFullPath, pipelineIid, status == null ? null : status.name(),
                items, afterCursor);
    }

    /** Returns pipeline job details. */
    @Tool(name = "gitlab_get_job", description = "Returns GitLab pipeline job details.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getJob(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid,
            @ToolArg(description = "Job name within the pipeline.") String jobName) {
        return gitLabReadService.getJob(projectFullPath, pipelineIid, jobName);
    }

    /** Returns the tail of a job log. */
    @Tool(name = "gitlab_get_job_log", description = "Returns the tail of a GitLab pipeline job log.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse getJobLog(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Project-scoped pipeline IID.") Integer pipelineIid,
            @ToolArg(description = "Job name within the pipeline.") String jobName,
            @ToolArg(description = "Number of log tail lines from 1 to 500; defaults to 200.", required = false) Integer lines) {
        return gitLabReadService.getJobLog(projectFullPath, pipelineIid, jobName, lines);
    }

    /** Returns repositories with activity during the specified period. */
    @Tool(name = "gitlab_list_active_projects", description = "Returns repositories with activity (push, merge request, pipeline) during the specified period.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listActiveProjects(
            @ToolArg(description = "Lower bound for lastActivityAt in ISO 8601 with a timezone. At least one bound is required.", required = false) String updatedAfter,
            @ToolArg(description = "Upper bound for lastActivityAt in ISO 8601 with a timezone. At least one bound is required.", required = false) String updatedBefore,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listActiveProjects(updatedAfter, updatedBefore, items, afterCursor);
    }

    /** Returns repository labels */
    @Tool (name = "gitlab_list_project_labels", description = "Returns a compact list of labels in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectLabels (
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectLabels(projectFullPath, items, afterCursor);
    }

    /** Returns project milestones */
    @Tool(name = "gitlab_list_project_milestones", description = "Returns a compact list of milestones in a GitLab project.", structuredContent = true, outputSchema = @Tool.OutputSchema(generator = FreeFormOutputSchemaGenerator.class))
    public ToolResponse listProjectMilestones(
            @ToolArg(description = "Full project path in the form group/subgroup/project.") String projectFullPath,
            @ToolArg(description = "Number of items from 1 to 20; defaults to 20.", required = false) Integer items,
            @ToolArg(description = "Number of the next REST page.", required = false) String afterCursor) {
        return gitLabReadService.listProjectMilestones(projectFullPath, items, afterCursor);
    }
}
