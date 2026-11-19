# Changelog

## 0.2.0

### Breaking

- Write tools are published only when `GITLAB_WRITE_ENABLED=true`
  (`gitlab.write.enabled`, default `false`). Without the flag, the existing
  issue write tools are hidden as well. Set `GITLAB_WRITE_ENABLED=true` in
  `.env` to keep the previous behavior.

### Added

- `GITLAB_WRITE_ENABLED` flag: when `false`, write tools are absent from
  `tools/list` and calls to them are rejected (34 read / 49 total tools).
- Read tools:
  - `gitlab_list_branches`, `gitlab_get_branch`;
  - `gitlab_compare_refs`: commits and changed file paths between two refs;
  - `gitlab_list_project_members`: direct and inherited project members;
  - `gitlab_get_merge_request_commits`;
  - `gitlab_server_info`: GitLab version, connectivity and token check;
  - `gitlab_list_dir`: new `recursive` argument.
- Merge request write tools: `gitlab_create_merge_request`,
  `gitlab_update_merge_request`, `gitlab_merge_merge_request` (with a `sha`
  guard against races), `gitlab_approve_merge_request`,
  `gitlab_unapprove_merge_request`, `gitlab_create_merge_request_note`.
- CI/CD write tools: `gitlab_create_pipeline` (`env_var` variables),
  `gitlab_retry_pipeline`, `gitlab_cancel_pipeline`, `gitlab_retry_job`
  (job selected by `pipelineIid` + `jobName`).

### Changed

- GitLab 405/406 responses from write tools are reported as `VALIDATION_ERROR`.
- `gitlab_merge_merge_request` rejects a blank `sha` instead of merging
  without the HEAD check; `gitlab_update_merge_request` rejects a blank
  `title`/`targetBranch` (an empty `description` still clears it).

## 0.1.0

Initial public release.

- MCP tools for GitLab issues, merge requests, repositories, and CI/CD over Streamable HTTP.
- GraphQL-first GitLab access, complemented by REST v4 where needed.
- Docker Compose deployment.
