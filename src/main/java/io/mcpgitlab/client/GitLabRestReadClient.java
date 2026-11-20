package io.mcpgitlab.client;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Focused REST v4 adapter for READ capabilities missing from GitLab 14 GraphQL.
 *
 * <p>Shared HTTP transport, the {@link GitLabRestClientBase.RestResponse} response type,
 * and the {@link GitLabRestClientBase.GitLabRestException} transport error
 * are inherited from {@link GitLabRestClientBase}.
 */
@ApplicationScoped
public class GitLabRestReadClient extends GitLabRestClientBase {

    /**
     * Searches issues across all projects accessible to the service token.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse searchIssues(String privateToken, String query, String state, String authorUsername,
                                     String assigneeUsername, String labels, String updatedAfter, String updatedBefore,
                                     String milestone, String createdAfter, String createdBefore, int items, Integer page, String orderBy, String sort) {
        return get(privateToken, "/api/v4/issues", "scope", "all", "search", query, "state", state, "author_username", authorUsername,
                "assignee_username", assigneeUsername, "labels", labels, "updated_after", updatedAfter,
                "updated_before", updatedBefore, "milestone", milestone, "created_after", createdAfter, "created_before", createdBefore,
                "per_page", items, "page", page, "order_by", orderBy, "sort", sort);
    }

    /**
     * Searches merge requests across all projects accessible to the service token.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse searchMergeRequests(String privateToken, String query, String state, String authorUsername,
                                            String assigneeUsername, String labels, String updatedAfter, String updatedBefore,
                                            String milestone, String createdAfter, String createdBefore, int items, Integer page, String orderBy, String sort) {
        return get(privateToken, "/api/v4/merge_requests", "scope", "all", "search", query, "state", state, "author_username", authorUsername,
                "assignee_username", assigneeUsername, "labels", labels, "updated_after", updatedAfter,
                "updated_before", updatedBefore, "milestone", milestone, "created_after", createdAfter, "created_before", createdBefore,
                "per_page", items, "page", page, "order_by", orderBy, "sort", sort);
    }

    /**
     * Searches code snippets in a project using the GitLab Search API.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse searchCode(String privateToken, String projectFullPath, String query, String ref, int items) {
        return get(privateToken, projectPath(projectFullPath) + "/search", "scope", "blobs", "search", query, "ref", ref,
                "per_page", items);
    }

    /**
     * Returns project metadata needed to build a link to a matching file.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getProject(String privateToken, String projectFullPath) {
        return get(privateToken, projectPath(projectFullPath));
    }

    /**
     * Builds a GitLab link to a file line at the specified ref.
     *
     * @return file link with a line anchor
     */
    public String blobUrl(String projectWebUrl, String ref, String path, Number line) {
        StringBuilder encodedPath = new StringBuilder();
        for (String segment : path.split("/")) {
            if (!encodedPath.isEmpty()) {
                encodedPath.append('/');
            }
            encodedPath.append(encode(segment));
        }
        return projectWebUrl + "/-/blob/" + encode(ref) + "/" + encodedPath + "#L" + line;
    }

    /**
     * Returns a page of project commits.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse listCommits(String privateToken, String projectFullPath, String refName, String since, String until,
                                    int items, Integer page) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/commits", "ref_name", refName,
                "since", since, "until", until, "per_page", items, "page", page);
    }

    /**
     * Returns a commit by SHA, branch, or tag.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getCommit(String privateToken, String projectFullPath, String sha) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/commits/" + encode(sha));
    }

    /**
     * Returns a commit diff.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getCommitDiff(String privateToken, String projectFullPath, String sha) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/commits/" + encode(sha) + "/diff");
    }

    /**
     * Returns current merge request changes in GitLab 14.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getMergeRequestChanges(String privateToken, String projectFullPath, int iid) {
        return get(privateToken, projectPath(projectFullPath) + "/merge_requests/" + iid + "/changes");
    }

    /**
     * Returns a page of project tags.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse listTags(String privateToken, String projectFullPath, int items, Integer page) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/tags", "per_page", items, "page", page);
    }

    /**
     * Returns a page of project labels.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse listLabels(String privateToken, String projectFullPath, int items, Integer page) {
        return get(privateToken, projectPath(projectFullPath) + "/labels", "per_page", items, "page", page);
    }

    /**
     * Returns a tag by name.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getTag(String privateToken, String projectFullPath, String tagName) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/tags/" + encode(tagName));
    }

    /**
     * Returns a page of repository directory entries; recursion must be requested explicitly.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse listRepositoryTree(String privateToken, String projectFullPath, String path, String ref,
                                           boolean recursive, int items, Integer page) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/tree", "path", path, "ref", ref,
                "recursive", recursive, "per_page", items, "page", page);
    }

    /**
     * Returns a page of repository branches with the server-side search filter.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse listBranches(String privateToken, String projectFullPath, String search, int items, Integer page) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/branches", "search", search,
                "per_page", items, "page", page);
    }

    /**
     * Returns a repository branch by name (the whole name is URL-encoded).
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse getBranch(String privateToken, String projectFullPath, String branchName) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/branches/" + encode(branchName));
    }

    /**
     * Compares two repository refs (branch, tag, or SHA).
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse compare(String privateToken, String projectFullPath, String from, String to) {
        return get(privateToken, projectPath(projectFullPath) + "/repository/compare", "from", from, "to", to);
    }

    /**
     * Returns the GitLab version.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse version(String privateToken) {
        return get(privateToken, "/api/v4/version");
    }

    /**
     * Returns a global project list with server-side activity period filtering.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse listActiveProjects(String privateToken, String lastActivityAfter,
                                           String lastActivityBefore, int items, Integer page) {
        return get(privateToken, "/api/v4/projects", "last_activity_after", lastActivityAfter,
                "last_activity_before", lastActivityBefore, "order_by", "last_activity_at", "sort", "desc",
                "per_page", items, "page", page);
    }

    /**
     * Returns a job log (trace) through GitLab REST v4.
     *
     * @return GitLab HTTP response: job log as text
     */
    public RestResponse getJobTrace(String privateToken, String projectFullPath, int jobId) {
        return get(privateToken, projectPath(projectFullPath) + "/jobs/" + jobId + "/trace");
    }

    /**
     * Searches users by name or username.
     *
     * @return GitLab HTTP response without logging unfiltered data
     */
    public RestResponse searchUsers(String privateToken, String userName, int items, Integer page) {
        return get(privateToken, "/api/v4/search", "scope", "users", "search", userName, "per_page", items, "page", page);
    }

    /**
     * Returns a page of project milestones.
     *
     * @return GitLab HTTP response: milestone list
     */
    public RestResponse listProjectMilestones(String privateToken, String projectFullPath, int items, Integer page) {
        return get(privateToken, projectPath(projectFullPath) + "/milestones", "per_page", items, "page", page);
    }

    /**
     * Returns a user by exact username.
     *
     * @return GitLab HTTP response: a single-user list
     */
    public RestResponse getUserByUsername(String privateToken, String username) {
        return get(privateToken, "/api/v4/users", "username", username);
    }

}
