package io.mcpgitlab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import io.mcpgitlab.client.GitLabRestClientBase;
import java.util.*;

/**
 * Implements normalized read-only MCP contracts over GitLab GraphQL and REST v4.
 */
@ApplicationScoped
public class GitLabReadService extends GitLabServiceBase {

    private static final int CODE_SEARCH_ITEMS = 20;

    private static final int MAX_FILE_LINES = 500;

    /**
     * Returns projects accessible to the service token.
     *
     * @return normalized page of projects or an MCP error
     */
    public ToolResponse listProjects(String query, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            Map<String, Object> connection = graphQL("""
                    query Projects($first: Int!, $after: String, $search: String) {
                      projects(first: $first, after: $after, search: $search) {
                        nodes { id name fullPath description lastActivityAt webUrl }
                        pageInfo { endCursor hasNextPage }
                      }
                    }
                    """, vars("first", limit, "after", cursor(afterCursor), "search", optional(query)), "projects");
            return page(connection, this::projectList);
        });
    }

    /**
     * Returns project details.
     *
     * @return project or an MCP error
     */
    public ToolResponse getProject(String projectFullPath) {
        return execute(() -> {
            validateProject(projectFullPath);
            return graphQL("""
                            query Project($fullPath: ID!) {
                              project(fullPath: $fullPath) {
                                id name fullPath description lastActivityAt webUrl nameWithNamespace createdAt
                                visibility httpUrlToRepo sshUrlToRepo
                              }
                            }
                            """,
                    vars("fullPath", projectFullPath),
                    "project");
        });
    }

    /**
     * Returns project issues with supported GitLab GraphQL filters.
     *
     * @return normalized page of issues or an MCP error
     */
    public ToolResponse listProjectIssues(String projectFullPath, String query, String state,
                                          String authorUsername, String assigneeUsername, String label,
                                          String updatedAfter, String updatedBefore, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validateState(state, "opened", "closed", "all");
            validateDate(updatedAfter);
            validateDate(updatedBefore);
            Map<String, Object> project = graphQL("""
                    query ProjectIssues($fullPath: ID!, $first: Int!, $after: String, $search: String,
                                        $state: IssuableState, $author: String, $assignees: [String!],
                                        $labels: [String!], $updatedAfter: Time, $updatedBefore: Time) {
                      project(fullPath: $fullPath) {
                        issues(first: $first, after: $after, search: $search, state: $state,
                               authorUsername: $author, assigneeUsernames: $assignees, labelName: $labels,
                               updatedAfter: $updatedAfter, updatedBefore: $updatedBefore) {
                          nodes { iid title state updatedAt author { username name } assignees(first: 20) { nodes { username name } }
                                  labels(first: 20) { nodes { title } } milestone { title } webUrl }
                          pageInfo { endCursor hasNextPage }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "first", limit, "after", cursor(afterCursor),
                    "search", optional(query), "state", optional(state), "author", optional(authorUsername),
                    "assignees", one(assigneeUsername), "labels", one(label),
                    "updatedAfter", optional(updatedAfter), "updatedBefore", optional(updatedBefore)), "project");
            return page(object(project.get("issues")), this::issueList);
        });
    }

    /**
     * Returns project issue details.
     *
     * @return issue or an MCP error
     */
    public ToolResponse getIssue(String projectFullPath, Integer iid) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(iid);
            Map<String, Object> project = graphQL("""
                    query Issue($fullPath: ID!, $iid: String!) {
                      project(fullPath: $fullPath) {
                        issues(first: 1, iids: [$iid]) {
                          nodes { iid title state updatedAt author { username name } assignees(first: 20) { nodes { username name } }
                                  labels(first: 20) { nodes { title } } webUrl description createdAt closedAt
                                  milestone { title } weight dueDate reference }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "iid", iid.toString()), "project");
            return issueDetail(single(object(project.get("issues")).get("nodes"), "Issue not found"));
        });
    }

    /**
     * Performs server-side issue search using GitLab REST v4.
     *
     * @return normalized page of issues or an MCP error
     */
    public ToolResponse searchIssues(String query, String state, String authorUsername, String assigneeUsername,
                                     String labels, String updatedAfter, String updatedBefore, String createdAfter,
                                     String createdBefore, String milestone, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateState(state, "opened", "closed", "all");
            validateDate(updatedAfter);
            validateDate(updatedBefore);
            validateDate(createdAfter);
            validateDate(createdBefore);
            return restPage(restClient.searchIssues(privateToken, optional(query), optional(state), optional(authorUsername),
                    optional(assigneeUsername), optional(labels), optional(updatedAfter), optional(updatedBefore), optional(milestone),
                    optional(createdAfter), optional(createdBefore), limit, restPageNumber(afterCursor), "updated_at", "desc"), this::issueList);
        });
    }

    /**
     * Returns issue discussions with their notes.
     *
     * @return normalized page of discussions or an MCP error
     */
    public ToolResponse listIssueDiscussions(String projectFullPath, Integer iid, Integer items, String afterCursor) {
        return discussions(projectFullPath, iid, items, afterCursor, "issues(first: 1, iids: [$iid])", "issues");
    }

    /**
     * Returns project merge requests with supported GitLab GraphQL filters.
     *
     * @return normalized page of merge requests or an MCP error
     */
    public ToolResponse listProjectMergeRequests(String projectFullPath, String state, String authorUsername,
                                                 String assigneeUsername, String label, String updatedAfter,
                                                 String updatedBefore, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validateState(state, "opened", "closed", "merged", "all");
            validateDate(updatedAfter);
            validateDate(updatedBefore);
            Map<String, Object> project = graphQL("""
                    query ProjectMergeRequests($fullPath: ID!, $first: Int!, $after: String, $state: MergeRequestState,
                                                $author: String, $assignee: String, $labels: [String!],
                                                $updatedAfter: Time, $updatedBefore: Time) {
                      project(fullPath: $fullPath) {
                        mergeRequests(first: $first, after: $after, state: $state, authorUsername: $author,
                                      assigneeUsername: $assignee, labels: $labels, updatedAfter: $updatedAfter,
                                      updatedBefore: $updatedBefore) {
                          nodes { iid title state draft updatedAt author { username name } assignees(first: 20) { nodes { username name } }
                                  sourceBranch targetBranch mergeStatusEnum milestone { title } webUrl }
                          pageInfo { endCursor hasNextPage }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "first", limit, "after", cursor(afterCursor),
                    "state", optional(state), "author", optional(authorUsername), "assignee", optional(assigneeUsername),
                    "labels", one(label), "updatedAfter", optional(updatedAfter), "updatedBefore", optional(updatedBefore)), "project");
            return page(object(project.get("mergeRequests")), this::mergeRequestList);
        });
    }

    /**
     * Returns project merge request details.
     *
     * @return merge request or an MCP error
     */
    public ToolResponse getMergeRequest(String projectFullPath, Integer iid) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(iid);
            Map<String, Object> project = graphQL("""
                    query MergeRequest($fullPath: ID!, $iid: String!) {
                      project(fullPath: $fullPath) {
                        mergeRequests(first: 1, iids: [$iid]) {
                          nodes { iid title state draft updatedAt author { username name } assignees(first: 20) { nodes { username name } }
                                  sourceBranch targetBranch mergeStatusEnum milestone { title } webUrl description labels(first: 20) { nodes { title } }
                                  reviewers(first: 20) { nodes { username name } } approvalState { approvalRulesOverwritten }
                                  approvalsRequired approvalsLeft headPipeline { id status } mergedAt mergeCommitSha mergeError }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "iid", iid.toString()), "project");
            return mergeRequestDetail(single(object(project.get("mergeRequests")).get("nodes"), "Merge request not found"));
        });
    }

    /**
     * Performs server-side merge request search using GitLab REST v4.
     *
     * @return normalized page of merge requests or an MCP error
     */
    public ToolResponse searchMergeRequests(String query, String state, String authorUsername, String assigneeUsername,
                                            String labels, String updatedAfter, String updatedBefore, String createdAfter, String createdBefore, Integer items,
                                            String milestone, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateState(state, "opened", "closed", "merged", "all");
            validateDate(updatedAfter);
            validateDate(updatedBefore);
            validateDate(createdAfter);
            validateDate(createdBefore);
            return restPage(restClient.searchMergeRequests(privateToken, optional(query), optional(state), optional(authorUsername),
                    optional(assigneeUsername), optional(labels), optional(updatedAfter), optional(updatedBefore), optional(milestone),
                    optional(createdAfter), optional(createdBefore), limit,
                    restPageNumber(afterCursor), "updated_at", "desc"), this::mergeRequestList);
        });
    }

    /**
     * Searches project code snippets using the GitLab Search API.
     *
     * @return matching snippets with path, ref, line number, and URL, or an MCP error
     */
    public ToolResponse searchCode(String projectFullPath, String query, String ref, String path, String extension) {
        return execute(() -> {
            validateProject(projectFullPath);
            required(query, "query");
            Map<String, Object> project = restObject(restClient.getProject(privateToken, projectFullPath));
            String usedRef = optional(ref);
            if (usedRef == null) {
                usedRef = required((String) value(project, "defaultBranch"), "defaultBranch");
            }
            return codeSearchResult(restClient.searchCode(privateToken, projectFullPath, codeSearchQuery(query, path, extension), usedRef,
                    CODE_SEARCH_ITEMS), project, projectFullPath, usedRef);
        });
    }

    /**
     * Returns a bounded range of text file lines through GitLab GraphQL.
     *
     * @return file contents or metadata if text is unavailable
     */
    public ToolResponse getFileContent(String projectFullPath, String path, String ref, Integer startLine, Integer endLine) {
        return execute(() -> {
            validateProject(projectFullPath);
            required(path, "path");
            required(ref, "ref");
            validateLineRange(startLine, endLine);
            Map<String, Object> project = graphQL("""
                    query FileContent($fullPath: ID!, $paths: [String!]!, $ref: String!) {
                      project(fullPath: $fullPath) {
                        repository {
                          blobs(paths: $paths, ref: $ref, first: 1) {
                            nodes { path size rawTextBlob }
                          }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "paths", List.of(path), "ref", ref), "project");
            Map<String, Object> blob = single(object(object(project.get("repository")).get("blobs")).get("nodes"), "File not found");
            return fileContent(blob, ref, startLine, endLine);
        });
    }

    /**
     * Returns merge request discussions with their notes.
     *
     * @return normalized page of discussions or an MCP error
     */
    public ToolResponse listMergeRequestDiscussions(String projectFullPath, Integer iid, Integer items, String afterCursor) {
        return discussions(projectFullPath, iid, items, afterCursor, "mergeRequests(first: 1, iids: [$iid])", "mergeRequests");
    }

    /**
     * Returns project commits through GitLab REST v4.
     *
     * @return normalized page of commits or an MCP error
     */
    public ToolResponse listProjectCommits(String projectFullPath, String refName, String since, String until,
                                           Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validateDate(since);
            validateDate(until);
            return restPage(restClient.listCommits(privateToken, projectFullPath, optional(refName), optional(since),
                    optional(until), limit, restPageNumber(afterCursor)), this::commitList);
        });
    }

    /**
     * Returns a commit by SHA, branch, or tag through GitLab REST v4.
     *
     * @return commit details or an MCP error
     */
    public ToolResponse getCommit(String projectFullPath, String sha) {
        return execute(() -> {
            validateProject(projectFullPath);
            required(sha, "sha");
            return commitDetail(restObject(restClient.getCommit(privateToken, projectFullPath, sha)));
        });
    }

    /**
     * Returns a commit diff using GitLab REST v4.
     *
     * @return normalized page of diff files or an MCP error
     */
    public ToolResponse getCommitDiff(String projectFullPath, String sha, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            required(sha, "sha");
            return diffPage(restArray(restClient.getCommitDiff(privateToken, projectFullPath, sha)), limit,
                    restPageNumber(afterCursor), false);
        });
    }

    /**
     * Returns the current merge request diff in GitLab 14.
     *
     * @return normalized page of diff files or an MCP error
     */
    public ToolResponse getMergeRequestDiff(String projectFullPath, Integer iid, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validateIid(iid);
            Map<String, Object> changes = restObject(restClient.getMergeRequestChanges(privateToken, projectFullPath, iid));
            return diffPage(objects(changes.get("changes")), limit, restPageNumber(afterCursor),
                    Boolean.TRUE.equals(value(changes, "overflow")));
        });
    }

    /**
     * Returns project tags through GitLab REST v4.
     *
     * @return normalized page of tags or an MCP error
     */
    public ToolResponse listTags(String projectFullPath, Integer items, String afterCursor) {
        return execute(() -> {
            validateProject(projectFullPath);
            return restPage(restClient.listTags(privateToken, projectFullPath, pageSize(items), restPageNumber(afterCursor)), this::tagList);
        });
    }

    /**
     * Returns a project tag by name through GitLab REST v4.
     *
     * @return tag details or an MCP error
     */
    public ToolResponse getTag(String projectFullPath, String tagName) {
        return execute(() -> {
            validateProject(projectFullPath);
            required(tagName, "tagName");
            return tagDetail(restObject(restClient.getTag(privateToken, projectFullPath, tagName)));
        });
    }

    /**
     * Returns project labels through GitLab REST v4.
     *
     * @return normalized page of labels or an MCP error
     */
    public ToolResponse listProjectLabels(String projectFullPath, Integer items, String afterCursor) {
        return execute(() -> {
            validateProject(projectFullPath);
            return restPage(restClient.listLabels(privateToken, projectFullPath, pageSize(items), restPageNumber(afterCursor)), this::labelList);
        });
    }

    /**
     * Returns project milestones through GitLab REST v4.
     *
     * @return normalized page of milestones or an MCP error
     */
    public ToolResponse listProjectMilestones(String projectFullPath, Integer items, String afterCursor) {
        return execute(() -> {
            validateProject(projectFullPath);
            return restPage(restClient.listProjectMilestones(privateToken, projectFullPath, pageSize(items), restPageNumber(afterCursor)), this::milestoneList);
        });
    }

    /**
     * Returns a non-recursive list of repository directory files and folders through GitLab REST v4.
     *
     * @return normalized page of directory entries or an MCP error
     */
    public ToolResponse listDir(String projectFullPath, String path, String ref, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            return restPage(restClient.listRepositoryTree(privateToken, projectFullPath, optional(path), optional(ref),
                    limit, restPageNumber(afterCursor)), this::treeEntry);
        });
    }

    public ToolResponse searchUsers(String userName, Integer items, String afterCursor) {
        return execute(() -> {
            required(userName, "userName");
            return restPage(restClient.searchUsers(privateToken, userName, pageSize(items), restPageNumber(afterCursor)),
                    result -> fields(result, "id", "username", "name", "state", "webUrl"));
        });
    }

    /**
     * Returns projects with activity during the specified period (global REST filter).
     *
     * @return normalized page of projects or an MCP error
     */
    public ToolResponse listActiveProjects(String updatedAfter, String updatedBefore, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            if (optional(updatedAfter) == null && optional(updatedBefore) == null) {
                throw new GitLabException("VALIDATION_ERROR", "Specify updatedAfter or updatedBefore");
            }
            validateDate(updatedAfter);
            validateDate(updatedBefore);
            return restPage(restClient.listActiveProjects(privateToken, optional(updatedAfter), optional(updatedBefore),
                    limit, restPageNumber(afterCursor)), this::activeProject);
        });
    }

    /**
     * Returns project pipelines with supported GitLab GraphQL filters.
     *
     * @return normalized page of pipelines or an MCP error
     */
    public ToolResponse listProjectPipelines(String projectFullPath, String status, String ref,
                                             String updatedAfter, String updatedBefore, Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validatePipelineStatus(status);
            validateDate(updatedAfter);
            validateDate(updatedBefore);
            Map<String, Object> project = graphQL("""
                    query ProjectPipelines($fullPath: ID!, $first: Int!, $after: String, $status: PipelineStatusEnum,
                                        $ref: String, $updatedAfter: Time, $updatedBefore: Time) {
                      project(fullPath: $fullPath) {
                        pipelines(first: $first, after: $after, status: $status, ref: $ref,
                                  updatedAfter: $updatedAfter, updatedBefore: $updatedBefore) {
                          nodes { iid ref status sha(format: SHORT) createdAt updatedAt finishedAt duration
                                  user { username } path }
                          pageInfo { endCursor hasNextPage }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "first", limit, "after", cursor(afterCursor),
                    "status", statusEnum(status), "ref", optional(ref),
                    "updatedAfter", optional(updatedAfter), "updatedBefore", optional(updatedBefore)), "project");
            return page(object(project.get("pipelines")), this::pipelineList);
        });
    }

    /**
     * Returns project pipeline details.
     *
     * @return pipeline or an MCP error
     */
    public ToolResponse getPipeline(String projectFullPath, Integer pipelineIid) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            Map<String, Object> project = graphQL("""
                    query Pipeline($fullPath: ID!, $iid: ID) {
                      project(fullPath: $fullPath) {
                        pipeline(iid: $iid) {
                          iid ref status sha(format: SHORT) createdAt updatedAt finishedAt duration
                          user { username } path
                          startedAt coverage warnings
                          commit { sha shortId title authorName authoredDate webUrl }
                          stages(first: 100) { nodes { name status } }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "iid", pipelineIid.toString()), "project");
            Map<String, Object> pipeline = object(project.get("pipeline"));
            if (pipeline.isEmpty()) {
                throw new GitLabException("NOT_FOUND", "Pipeline not found");
            }
            Map<String, Object> result = pipelineList(pipeline);
            copy(result, pipeline, "startedAt", "coverage", "warnings");
            result.put("commit", fields(object(pipeline.get("commit")), "sha", "shortId", "title", "authorName", "authoredDate", "webUrl"));
            result.put("stages", stageList(pipeline.get("stages")));
            return result;
        });
    }

    /**
     * Returns pipeline jobs filtered by status.
     *
     * @return normalized page of jobs or an MCP error
     */
    public ToolResponse listPipelineJobs(String projectFullPath, Integer pipelineIid, String status,
                                         Integer items, String afterCursor) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            validatePipelineStatus(status);
            Map<String, Object> project = graphQL("""
                    query PipelineJobs($fullPath: ID!, $iid: ID, $first: Int!, $after: String, $statuses: [CiJobStatus!]) {
                      project(fullPath: $fullPath) {
                        pipeline(iid: $iid) {
                          jobs(first: $first, after: $after, statuses: $statuses) {
                            nodes { name status stage { name } duration startedAt finishedAt allowFailure id }
                            pageInfo { endCursor hasNextPage }
                          }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "iid", pipelineIid.toString(), "first", limit,
                    "after", cursor(afterCursor), "statuses", oneEnum(status)), "project");
            Map<String, Object> pipeline = requirePipeline(project, projectFullPath);
            return page(object(pipeline.get("jobs")), source -> jobList(source, projectFullPath));
        });
    }

    /**
     * Returns pipeline job details by name.
     *
     * @return job or an MCP error
     */
    public ToolResponse getJob(String projectFullPath, Integer pipelineIid, String jobName) {
        return execute(() -> {
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            required(jobName, "jobName");
            Map<String, Object> project = graphQL("""
                    query PipelineJob($fullPath: ID!, $iid: ID, $jobName: String) {
                      project(fullPath: $fullPath) {
                        pipeline(iid: $iid) {
                          job(name: $jobName) {
                            name status stage { name } duration startedAt finishedAt allowFailure id
                            createdAt queuedAt scheduledAt coverage tags manualJob triggered
                            downstreamPipeline { iid ref status }
                          }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "iid", pipelineIid.toString(), "jobName", jobName), "project");
            Map<String, Object> pipeline = requirePipeline(project, projectFullPath);
            Map<String, Object> job = object(pipeline.get("job"));
            if (job.isEmpty()) {
                throw new GitLabException("NOT_FOUND", "Job not found");
            }
            Map<String, Object> result = jobList(job, projectFullPath);
            copy(result, job, "createdAt", "queuedAt", "scheduledAt", "coverage", "manualJob", "triggered");
            result.put("tags", stringList(job.get("tags")));
            result.put("downstreamPipeline", downstreamCard(object(job.get("downstreamPipeline"))));
            return result;
        });
    }

    /**
     * Returns the tail of a job log through the REST trace endpoint.
     *
     * @return `{ text, truncated, totalLines }` or an MCP error
     */
    public ToolResponse getJobLog(String projectFullPath, Integer pipelineIid, String jobName, Integer lines) {
        return execute(() -> {
            int limit = logLines(lines);
            validateProject(projectFullPath);
            validateIid(pipelineIid);
            required(jobName, "jobName");
            Map<String, Object> project = graphQL("""
                    query PipelineJobForLog($fullPath: ID!, $iid: ID, $jobName: String) {
                      project(fullPath: $fullPath) {
                        pipeline(iid: $iid) {
                          job(name: $jobName) { id }
                        }
                      }
                    }
                    """, vars("fullPath", projectFullPath, "iid", pipelineIid.toString(), "jobName", jobName), "project");
            Map<String, Object> pipeline = requirePipeline(project, projectFullPath);
            Map<String, Object> job = object(pipeline.get("job"));
            if (job.isEmpty()) {
                throw new GitLabException("NOT_FOUND", "Job not found");
            }
            GitLabRestClientBase.RestResponse response = restClient.getJobTrace(privateToken, projectFullPath,
                    ciNumericId(job.get("id")));
            if (response.status() == 404) {
                throw new GitLabException("NOT_FOUND", "Job log not found");
            }
            return jobLog(response.body(), limit, response.status());
        });
    }

    private Map<String, Object> requirePipeline(Map<String, Object> project, String projectFullPath) {
        Map<String, Object> pipeline = object(project.get("pipeline"));
        if (pipeline.isEmpty()) {
            throw new GitLabException("NOT_FOUND", "Pipeline not found");
        }
        return pipeline;
    }

    private Map<String, Object> jobLog(String body, int limit, int status) {
        ensureRestSuccess(status);
        String text = body == null ? "" : body;
        List<String> all = text.isEmpty() ? List.of() : text.lines().toList();
        int total = all.size();
        int from = Math.max(0, total - limit);
        Map<String, Object> result = map();
        result.put("text", String.join("\n", all.subList(from, total)));
        result.put("truncated", total > limit);
        result.put("totalLines", total);
        return result;
    }

    private ToolResponse discussions(String projectFullPath, Integer iid, Integer items, String afterCursor,
                                     String connectionField, String resultField) {
        return execute(() -> {
            int limit = pageSize(items);
            validateProject(projectFullPath);
            validateIid(iid);
            Map<String, Object> project = graphQL("""
                    query Discussions($fullPath: ID!, $iid: String!, $first: Int!, $after: String) {
                      project(fullPath: $fullPath) {
                        %s {
                          nodes {
                            discussions(first: $first, after: $after) {
                              nodes { id notes { nodes { id body author { username name } createdAt updatedAt system resolvable resolved url } } }
                              pageInfo { endCursor hasNextPage }
                            }
                          }
                        }
                      }
                    }
                    """.formatted(connectionField), vars("fullPath", projectFullPath, "iid", iid.toString(),
                    "first", limit, "after", cursor(afterCursor)), "project");
            Map<String, Object> entity = single(object(project.get(resultField)).get("nodes"), "Object not found");
            return page(object(entity.get("discussions")), this::discussion);
        });
    }

    private Map<String, Object> codeSearchResult(GitLabRestClientBase.RestResponse response, Map<String, Object> project,
                                                 String projectFullPath, String ref) {
        ensureRestSuccess(response.status());
        try {
            List<Map<String, Object>> values = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            List<Object> items = new ArrayList<>();
            for (Map<String, Object> source : values) {
                String path = required((String) value(source, "path", "filename"), "path");
                Object lineValue = value(source, "startline");
                if (!(lineValue instanceof Number line)) {
                    throw new GitLabException("GITLAB_ERROR", "GitLab did not return a search line number");
                }
                String itemRef = value(source, "ref") == null ? ref : Objects.requireNonNull(value(source, "ref")).toString();
                Map<String, Object> item = map();
                item.put("projectFullPath", projectFullPath);
                item.put("path", path);
                item.put("ref", itemRef);
                item.put("line", line);
                item.put("fragment", value(source, "data"));
                item.put("webUrl", restClient.blobUrl(required((String) value(project, "webUrl"), "webUrl"), itemRef, path, line));
                items.add(item);
            }
            Map<String, Object> result = map();
            result.put("items", items);
            result.put("truncated", response.nextPage() != null && !response.nextPage().isBlank());
            return result;
        } catch (JsonProcessingException exception) {
            throw new GitLabException("GITLAB_ERROR", "GitLab returned invalid JSON");
        }
    }

    private Map<String, Object> fileContent(Map<String, Object> blob, String ref, Integer startLine, Integer endLine) {
        Map<String, Object> result = fields(blob, "path", "size");
        result.put("ref", ref);
        Object textValue = value(blob, "rawTextBlob");
        if (!(textValue instanceof String text)) {
            result.put("contentAvailable", false);
            result.put("text", null);
            result.put("truncated", false);
            return result;
        }
        List<String> lines = text.lines().toList();
        int first = startLine == null ? 1 : startLine;
        if (first > lines.size() && !lines.isEmpty()) {
            throw new GitLabException("VALIDATION_ERROR", "startLine is outside the file");
        }
        int requestedLast = endLine == null ? lines.size() : Math.min(endLine, lines.size());
        int last = Math.min(requestedLast, first + MAX_FILE_LINES - 1);
        List<String> selected = lines.isEmpty() ? List.of() : lines.subList(first - 1, last);
        result.put("contentAvailable", true);
        result.put("startLine", first);
        result.put("endLine", last);
        result.put("totalLines", lines.size());
        result.put("text", String.join("\n", selected));
        boolean truncated = last < requestedLast;
        result.put("truncated", truncated);
        if (truncated) {
            result.put("nextStartLine", last + 1);
        }
        return result;
    }

    private Object diffPage(List<Map<String, Object>> source, int items, Integer page, boolean sourceLimited) {
        // shortcut: GitLab 14 returns all MR changes at once; switch to server-side diff pagination after upgrading GitLab.
        int currentPage = page == null ? 1 : page;
        int start = (int) Math.min((long) (currentPage - 1) * items, source.size());
        int end = (int) Math.min((long) start + items, source.size());
        List<Object> resultItems = new ArrayList<>();
        for (int index = start; index < end; index++) {
            resultItems.add(diff(source.get(index)));
        }
        boolean hasNextPage = end < source.size();
        Map<String, Object> result = pageResult(resultItems, hasNextPage ? Integer.toString(currentPage + 1) : null, hasNextPage);
        result.put("truncated", sourceLimited || hasNextPage);
        result.put("sourceLimited", sourceLimited);
        return result;
    }

    private Map<String, Object> diff(Map<String, Object> source) {
        return fields(source, "oldPath", "newPath", "newFile", "renamedFile", "deletedFile", "diff");
    }

    private String codeSearchQuery(String query, String path, String extension) {
        StringBuilder result = new StringBuilder(query);
        if (optional(path) != null) {
            result.append(" path:").append(path);
        }
        if (optional(extension) != null) {
            result.append(" extension:").append(extension);
        }
        return result.toString();
    }

    private Map<String, Object> projectList(Map<String, Object> source) {
        return fields(source, "id", "name", "fullPath", "description", "lastActivityAt", "webUrl");
    }

    private Map<String, Object> projectDetail(Map<String, Object> source) {
        return fields(source, "id", "name", "fullPath", "description", "lastActivityAt", "webUrl", "nameWithNamespace",
                "createdAt", "visibility", "httpUrlToRepo", "sshUrlToRepo");
    }

    private Map<String, Object> issueList(Map<String, Object> source) {
        Map<String, Object> result = fields(source, "iid", "title", "state", "updatedAt", "webUrl");
        result.put("author", person(object(source.get("author"))));
        result.put("assignees", people(source.get("assignees")));
        result.put("labels", titles(source.get("labels")));
        result.put("milestone", fields(object(source.get("milestone")), "title"));
        return result;
    }

    private Map<String, Object> issueDetail(Map<String, Object> source) {
        Map<String, Object> result = issueList(source);
        copy(result, source, "description", "createdAt", "closedAt", "weight", "dueDate", "reference");
        return result;
    }

    private Map<String, Object> mergeRequestList(Map<String, Object> source) {
        Map<String, Object> result = fields(source, "iid", "title", "state", "draft", "updatedAt", "sourceBranch", "targetBranch", "webUrl");
        result.put("author", person(object(source.get("author"))));
        result.put("assignees", people(source.get("assignees")));
        result.put("mergeStatus", value(source, "mergeStatusEnum", "mergeStatus"));
        result.put("milestone", fields(object(source.get("milestone")), "title"));
        return result;
    }

    private Map<String, Object> mergeRequestDetail(Map<String, Object> source) {
        Map<String, Object> result = mergeRequestList(source);
        copy(result, source, "description", "approvalsRequired", "approvalsLeft", "mergedAt", "mergeCommitSha", "mergeError");
        result.put("labels", titles(source.get("labels")));
        result.put("reviewers", people(source.get("reviewers")));
        result.put("approvalState", fields(object(source.get("approvalState")), "approvalRulesOverwritten"));
        result.put("headPipeline", fields(object(source.get("headPipeline")), "id", "status"));
        return result;
    }

    private Map<String, Object> discussion(Map<String, Object> source) {
        Map<String, Object> result = fields(source, "id");
        List<Object> notes = new ArrayList<>();
        for (Map<String, Object> note : objects(source.get("notes"))) {
            Map<String, Object> item = fields(note, "id", "body", "createdAt", "updatedAt", "system", "resolvable", "resolved");
            item.put("webUrl", value(note, "webUrl", "url"));
            item.put("author", person(object(note.get("author"))));
            notes.add(item);
        }
        result.put("notes", notes);
        return result;
    }

    private Map<String, Object> commitList(Map<String, Object> source) {
        Map<String, Object> result = fields(source, "shortId", "title", "message", "authorName", "authorEmail", "authoredDate", "webUrl");
        result.put("sha", value(source, "sha", "id"));
        return result;
    }

    private Map<String, Object> commitDetail(Map<String, Object> source) {
        Map<String, Object> result = commitList(source);
        result.put("committedDate", value(source, "committedDate"));
        result.put("parentIds", value(source, "parentIds"));
        return result;
    }

    private Map<String, Object> tagList(Map<String, Object> source) {
        return fields(source, "name", "target", "message", "protected");
    }

    private Map<String, Object> tagDetail(Map<String, Object> source) {
        Map<String, Object> result = tagList(source);
        Map<String, Object> commit = object(source.get("commit"));
        Map<String, Object> commitResult = fields(commit, "shortId", "title", "authoredDate", "authorName", "webUrl");
        commitResult.put("sha", value(commit, "sha", "id"));
        result.put("commit", commitResult);
        result.put("release", fields(object(source.get("release")), "name", "tagName", "description", "releasedAt"));
        return result;
    }

    private Map<String, Object> labelList(Map<String, Object> source) {
        return fields(source, "id", "name", "color", "description", "priority");
    }

    private Map<String, Object> milestoneList(Map<String, Object> source) {
        return fields(source, "id", "title", "state", "dueDate");
    }

    private Map<String, Object> activeProject(Map<String, Object> source) {
        Map<String, Object> result = map();
        result.put("id", value(source, "id"));
        result.put("name", value(source, "name"));
        result.put("fullPath", value(source, "path_with_namespace"));
        result.put("description", value(source, "description"));
        result.put("lastActivityAt", value(source, "last_activity_at"));
        result.put("webUrl", value(source, "web_url"));
        return result;
    }

    private Map<String, Object> treeEntry(Map<String, Object> source) {
        return fields(source, "id", "name", "type", "path");
    }

    private Map<String, Object> pipelineList(Map<String, Object> source) {
        Map<String, Object> result = map();
        Object iid = value(source, "iid");
        result.put("iid", iid instanceof String text && !text.isBlank() ? Integer.valueOf(text) : null);
        result.put("ref", value(source, "ref"));
        result.put("status", lower(value(source, "status")));
        result.put("sha", value(source, "sha"));
        result.put("createdAt", value(source, "createdAt"));
        result.put("updatedAt", value(source, "updatedAt"));
        result.put("finishedAt", value(source, "finishedAt"));
        result.put("duration", value(source, "duration"));
        result.put("user", person(object(source.get("user"))));
        result.put("webUrl", pipelineWebUrl(value(source, "path")));
        return result;
    }

    private List<Object> stageList(Object connectionValue) {
        List<Object> result = new ArrayList<>();
        for (Map<String, Object> stage : objects(connectionValue)) {
            Map<String, Object> item = fields(stage, "name");
            item.put("status", lower(value(stage, "status")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> jobList(Map<String, Object> source, String projectFullPath) {
        Map<String, Object> result = map();
        result.put("name", value(source, "name"));
        result.put("status", lower(value(source, "status")));
        result.put("stage", fields(object(source.get("stage")), "name"));
        result.put("duration", value(source, "duration"));
        result.put("startedAt", value(source, "startedAt"));
        result.put("finishedAt", value(source, "finishedAt"));
        result.put("allowFailure", value(source, "allowFailure"));
        result.put("webUrl", jobWebUrl(value(source, "id"), projectFullPath));
        return result;
    }

    private Map<String, Object> downstreamCard(Map<String, Object> source) {
        if (source.isEmpty()) {
            return null;
        }
        Map<String, Object> result = map();
        Object iid = value(source, "iid");
        result.put("iid", iid instanceof String text && !text.isBlank() ? Integer.valueOf(text) : null);
        result.put("ref", value(source, "ref"));
        result.put("status", lower(value(source, "status")));
        return result;
    }

    private String pipelineWebUrl(Object pathValue) {
        if (!(pathValue instanceof String path) || path.isBlank()) {
            return null;
        }
        return baseUri + (path.startsWith("/") ? "" : "/") + path;
    }

    private String jobWebUrl(Object gid, String projectFullPath) {
        return baseUri + "/" + projectFullPath + "/-/jobs/" + ciNumericId(gid);
    }

    private int ciNumericId(Object gid) {
        if (!(gid instanceof String id) || !id.contains("/")) {
            throw new GitLabException("GITLAB_ERROR", "GitLab did not return a numeric job ID");
        }
        String tail = id.substring(id.lastIndexOf('/') + 1);
        try {
            return Integer.valueOf(tail);
        } catch (NumberFormatException exception) {
            throw new GitLabException("GITLAB_ERROR", "GitLab did not return a numeric job ID");
        }
    }
}
