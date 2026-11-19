# MCP GitLab 14.x+

## Contents

- [What it is](#what-it-is)
- [Why GraphQL first?](#why-graphql-first)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Configuration and HTTPS](#configuration)
- [AI agent configuration](#ai-agent-configuration)
- [Tools](#tools)
- [Tool arguments and response contracts](#tool-arguments-and-response-contracts)
- [GitLab compatibility](#gitlab-compatibility)
- [Build & test](#build--test)
- [Docker](#docker)
- [License](#license)
- [Contributing and security](#contributing-and-security)

## What it is

An MCP server for GitLab, built with Quarkus. It exposes **49 tools
(34 read + 15 write)** over **Streamable HTTP** at `/mcp`. Write tools are
published only when `GITLAB_WRITE_ENABLED=true`.
GraphQL is used where supported by the verified GitLab 14 schema;
REST v4 covers the remaining operations.

## Why GraphQL first?

**GraphQL is the preferred GitLab API in this server.** Queries select only
the fields the tool needs and fetch related data, such as issue authors,
assignees, labels, and milestones, together. This reduces over-fetching and
the need for separate requests for related entities.

**REST v4 is used where the required functionality is unavailable in the
verified GitLab GraphQL schema.** Examples include cross-project issue and
merge request searches, code search, branch, commit, and tag operations, and
most writes. Job logs combine GraphQL job lookup with REST trace retrieval.

This is an explicit per-operation API choice, not an automatic fallback:
a failed GraphQL request is reported as an error, not silently retried
through REST. The tool tables below show the API used by each operation.

## Requirements

- JDK 25.
- Maven: use `./mvnw` (Maven 3.9.16 is downloaded on the first run).
- A reachable GitLab instance.
- A GitLab token with API read access to the projects you need (`read_api`
  for read-only use). Write tools require the `api` scope and permission to
  modify those projects. Create the token in your GitLab access token settings;
  scopes do not grant access to projects the token's identity cannot access.
- Port `8004` available.

## Quick start

Clone or download this repository and open its root directory.
Set your GitLab URL, then start the server:

```bash
export GITLAB_BASE_URI=https://gitlab.example.com
TOKEN_GITLAB=<token> ./mvnw quarkus:dev
```

Replace the URL and `<token>` with your instance URL and token.
The default URL is a placeholder, not a hosted service.

## Configuration

| Environment variable | Purpose | Default |
|---|---|---|
| `TOKEN_GITLAB` | Shared GitLab service token | Required |
| `GITLAB_BASE_URI` | GitLab instance base URL | `https://gitlab.example.com` |
| `GITLAB_WRITE_ENABLED` | Publish write tools (issues, merge requests, CI/CD) | `false` |
| `QUARKUS_HTTP_PORT` | HTTP port | `8004` |
| `QUARKUS_HTTP_HOST` | HTTP bind address | `0.0.0.0` |

The token is configured on the server, never passed as a tool argument.
All callers use that token's permissions. The server does not configure
MCP client authentication. Write tools are disabled by default: with
`GITLAB_WRITE_ENABLED=false` they are absent from `tools/list` and calls to them
are rejected. Enable them only when agents need to modify GitLab. Do not expose
the server to untrusted networks; restrict access or use an authenticating reverse proxy.
For local-only access, set `QUARKUS_HTTP_HOST=127.0.0.1`.

### HTTPS and private certificate authorities

Both GitLab clients verify TLS certificates. Publicly trusted HTTPS endpoints
need no extra settings. For a corporate or self-signed certificate chain,
obtain the trusted CA certificate from your administrator and verify its
fingerprint through a trusted channel. Do not disable certificate or hostname
verification.

Create a local trust store containing the JDK's public roots and your CA
(first-time setup; do not overwrite an existing custom store):

```bash
mkdir -p .certs
keytool -importkeystore \
  -srckeystore "$JAVA_HOME/lib/security/cacerts" -srcstorepass changeit \
  -destkeystore .certs/gitlab-truststore.p12 -deststoretype PKCS12 \
  -deststorepass changeit -noprompt
keytool -importcert -alias gitlab-company-ca -file /path/to/company-ca.pem \
  -keystore .certs/gitlab-truststore.p12 -storetype PKCS12 \
  -storepass changeit -noprompt
```

For a packaged JVM application:

```bash
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$PWD/.certs/gitlab-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"
java -jar target/quarkus-app/quarkus-run.jar
```

Set `TOKEN_GITLAB` and `GITLAB_BASE_URI` as usual. Both clients honor the JVM
trust store; the test suite checks this without disabling TLS verification.
The password above protects a store of public CA certificates, not private keys.

For Docker, create an optional `compose.ca.yaml` override:

```yaml
services:
  mcp-gitlab:
    volumes:
      - ./.certs/gitlab-truststore.p12:/deployments/tls/gitlab-truststore.p12:ro
    environment:
      JAVA_TOOL_OPTIONS: >-
        -Djavax.net.ssl.trustStore=/deployments/tls/gitlab-truststore.p12
        -Djavax.net.ssl.trustStorePassword=changeit
        -Djavax.net.ssl.trustStoreType=PKCS12
```

Then use both Compose files, including for subsequent updates and shutdown:

```bash
docker compose -f compose.yaml -f compose.ca.yaml up -d
docker compose -f compose.yaml -f compose.ca.yaml down
```

The container runs as UID 185; make sure it can read the mounted certificate
store. `.certs/` is excluded from Git and the Docker build context.

## AI agent configuration

Start the server before connecting your agent. The two URLs serve different purposes:

- `GITLAB_BASE_URI`: the GitLab instance the server connects to.
- `http://localhost:8004/mcp`: the MCP endpoint your agent connects to.

The GitLab token belongs on the server, not in agent configuration or prompts.
One server process connects to one GitLab instance using one shared token.
Agent-side tool approval is recommended, especially for write tools.

### Claude Code

Register the server for the current project:

```bash
claude mcp add --transport http gitlab --scope project http://localhost:8004/mcp
```

Alternatively, add this to `.mcp.json` in your agent project's root:

```json
{
  "mcpServers": {
    "gitlab": {
      "type": "http",
      "url": "http://localhost:8004/mcp"
    }
  }
}
```

Use `claude mcp list` or `/mcp` inside Claude Code to check the connection.

### Cursor

Add to `.cursor/mcp.json` in your agent project, or `~/.cursor/mcp.json` globally:

```json
{
  "mcpServers": {
    "gitlab": {
      "url": "http://localhost:8004/mcp"
    }
  }
}
```

### VS Code / GitHub Copilot

For an editor-managed workspace configuration, add `.vscode/mcp.json`:

```json
{
  "servers": {
    "gitlab": {
      "type": "http",
      "url": "http://localhost:8004/mcp"
    }
  }
}
```

You can also use **MCP: Add Server** in the Command Palette and select HTTP.

These examples use **Streamable HTTP**, not stdio or the legacy SSE endpoint.
They follow the clients' configuration formats; interactive client UI behavior
has not been tested here. See the official
[Claude Code](https://code.claude.com/docs/en/mcp),
[Cursor](https://cursor.com/docs/context/mcp), and
[VS Code](https://code.visualstudio.com/docs/agents/reference/mcp-configuration)
documentation for version-specific details.

For an agent running in a container or on another machine, `localhost`
refers to that environment, not necessarily the MCP server's host.
Use a reachable, authenticated endpoint; the supplied Compose configuration
intentionally publishes the service on the host loopback interface only.

### Example agent tasks

- "List open issues in team/project assigned to alice."
- "Review merge request !3 in team/project, including its diff and discussions."
- "Show the last 100 log lines for job unit in pipeline IID 12 of team/project."
- "Compare main with feature/x in team/project and summarize the changed files."
- "Create an issue in team/project after showing me the proposed title and body."

Use tools only against projects the shared server token is authorized to access.

## Tools

### Read tools (34)

| Group | Tool | Purpose | GitLab API | Key arguments |
|---|---|---|---|---|
| Projects | `gitlab_list_projects` | List accessible projects | GraphQL | `query`, `items`, `afterCursor` |
| Projects | `gitlab_get_project` | Get project details | GraphQL | `projectFullPath` |
| Projects | `gitlab_list_active_projects` | List projects active during a period | REST | `updatedAfter` and/or `updatedBefore` |
| Issues | `gitlab_list_project_issues` | List and filter project issues | GraphQL | `projectFullPath`, `state`, `query` |
| Issues | `gitlab_get_issue` | Get issue details | GraphQL | `projectFullPath`, `iid` |
| Issues | `gitlab_search_issues` | Search issues across accessible projects | REST | `query`, `state`, `labels`, date filters |
| Issues | `gitlab_list_issue_discussions` | List issue discussions and notes | GraphQL | `projectFullPath`, `iid` |
| Merge requests | `gitlab_list_project_merge_requests` | List and filter project merge requests | GraphQL | `projectFullPath`, `state` |
| Merge requests | `gitlab_get_merge_request` | Get merge request details | GraphQL | `projectFullPath`, `iid` |
| Merge requests | `gitlab_get_merge_request_diff` | Get the current merge request diff | REST | `projectFullPath`, `iid`, `items`, `afterCursor` |
| Merge requests | `gitlab_search_merge_requests` | Search across accessible projects | REST | `query`, `state`, `labels`, date filters |
| Merge requests | `gitlab_list_merge_request_discussions` | List discussions and notes | GraphQL | `projectFullPath`, `iid` |
| Code search | `gitlab_search_code` | Search code snippets in one project | REST | `projectFullPath`, `query`, `ref`, `path`, `extension` |
| File content | `gitlab_get_file_content` | Read a bounded text file line range at a revision | GraphQL | `projectFullPath`, `path`, `ref`, `startLine`, `endLine` |
| Commits | `gitlab_list_project_commits` | List project commits | REST | `projectFullPath`, `refName`, `since`, `until` |
| Commits | `gitlab_get_commit` | Get a commit by SHA, branch, or tag | REST | `projectFullPath`, `sha` |
| Commits | `gitlab_get_commit_diff` | Get a commit diff | REST | `projectFullPath`, `sha`, `items`, `afterCursor` |
| Tags | `gitlab_list_tags` | List project tags | REST | `projectFullPath`, `items`, `afterCursor` |
| Tags | `gitlab_get_tag` | Get a tag by name | REST | `projectFullPath`, `tagName` |
| Merge requests | `gitlab_get_merge_request_commits` | List merge request commits | GraphQL | `projectFullPath`, `iid`, `items`, `afterCursor` |
| Branches | `gitlab_list_branches` | List repository branches | REST | `projectFullPath`, `search`, `items`, `afterCursor` |
| Branches | `gitlab_get_branch` | Get branch details | REST | `projectFullPath`, `branchName` |
| Commits | `gitlab_compare_refs` | Compare two refs: commits and changed file paths | REST | `projectFullPath`, `from`, `to`, `items` |
| Repository tree | `gitlab_list_dir` | List files and folders, optionally recursive | REST | `projectFullPath`, `path`, `ref`, `recursive` |
| Users | `gitlab_search_users` | Search users by name or username using Latin letters | REST | `userName`, `items`, `afterCursor` |
| Pipelines | `gitlab_list_project_pipelines` | List and filter project pipelines | GraphQL | `projectFullPath`, `status`, `ref`, date filters |
| Pipelines | `gitlab_get_pipeline` | Get pipeline details | GraphQL | `projectFullPath`, `pipelineIid` |
| Jobs | `gitlab_list_pipeline_jobs` | List pipeline jobs | GraphQL | `projectFullPath`, `pipelineIid`, `status` |
| Jobs | `gitlab_get_job` | Get a pipeline job by name | GraphQL | `projectFullPath`, `pipelineIid`, `jobName` |
| Jobs | `gitlab_get_job_log` | Get the tail of a job log | GraphQL + REST | `projectFullPath`, `pipelineIid`, `jobName`, `lines` |
| Labels | `gitlab_list_project_labels` | List project labels | REST | `projectFullPath`, `items`, `afterCursor` |
| Milestones | `gitlab_list_project_milestones` | List project milestones | REST | `projectFullPath`, `items`, `afterCursor` |
| Members | `gitlab_list_project_members` | List direct and inherited project members with access levels | GraphQL | `projectFullPath`, `search`, `items`, `afterCursor` |
| Server | `gitlab_server_info` | Get the GitLab version; verifies connectivity and the token | REST | — |

### Write tools (15)

Published only when `GITLAB_WRITE_ENABLED=true`.

| Group | Tool | Purpose | GitLab API | Key arguments |
|---|---|---|---|---|
| Issues | `gitlab_create_issue` | Create an issue with optional labels and milestone | REST | `projectFullPath`, `title`, `description`, `labels`, `milestone` |
| Issues | `gitlab_create_issue_note` | Add an issue comment | REST | `projectFullPath`, `iid`, `body` |
| Labels | `gitlab_modify_issue_labels` | Add or remove issue labels | REST | `projectFullPath`, `iid`, `action`, `labels` |
| Assignees | `gitlab_assign_issue` | Replace all assignees; an empty array removes all | REST | `projectFullPath`, `iid`, `assignees` |
| Milestones | `gitlab_assign_issue_milestone` | Assign or remove an issue milestone | REST | `projectFullPath`, `iid`, `milestone` |
| Merge requests | `gitlab_create_merge_request` | Create a merge request | REST | `projectFullPath`, `sourceBranch`, `targetBranch`, `title`, `assignees`, `reviewers` |
| Merge requests | `gitlab_update_merge_request` | Partially update title, description, state, target branch, assignees, reviewers | REST | `projectFullPath`, `iid`, `title`, `stateEvent`, `targetBranch` |
| Merge requests | `gitlab_merge_merge_request` | Merge a merge request, optionally guarded by the expected HEAD SHA | REST | `projectFullPath`, `iid`, `sha`, `removeSourceBranch` |
| Merge requests | `gitlab_approve_merge_request` | Approve as the token owner | REST | `projectFullPath`, `iid` |
| Merge requests | `gitlab_unapprove_merge_request` | Revoke the token owner's approval | REST | `projectFullPath`, `iid` |
| Merge requests | `gitlab_create_merge_request_note` | Add a merge request comment | REST | `projectFullPath`, `iid`, `body` |
| Pipelines | `gitlab_create_pipeline` | Start a pipeline for a ref with optional variables | REST | `projectFullPath`, `ref`, `variables` |
| Pipelines | `gitlab_retry_pipeline` | Retry failed/canceled pipeline jobs | GraphQL + REST | `projectFullPath`, `pipelineIid` |
| Pipelines | `gitlab_cancel_pipeline` | Cancel active pipeline jobs | GraphQL + REST | `projectFullPath`, `pipelineIid` |
| Jobs | `gitlab_retry_job` | Retry a pipeline job by name | GraphQL + REST | `projectFullPath`, `pipelineIid`, `jobName` |

## Tool arguments and response contracts

The tables show key arguments, not the complete JSON schemas. MCP `tools/list`
returns the authoritative input schema, including required fields, types, enums,
and optional filters, for each tool.

- `projectFullPath` is a full namespace path, such as `team/subgroup/project`.
- Issue and merge request `iid` values are project-scoped numbers, not global IDs.
  `pipelineIid` is also project-scoped; jobs are selected by `jobName` within it.
- Date filters use ISO 8601 timestamps with a timezone, such as
  `2026-01-02T03:04:05Z`.
- `items` is 1–20, default 20. Paginated tools return `items` and
  `pageInfo: {endCursor, hasNextPage}`. Pass the returned `endCursor` unchanged
  as `afterCursor` until `hasNextPage` is false.
- GraphQL cursors are opaque strings. REST-backed list and diff tools use
  positive page numbers encoded as strings. Cursors are not interchangeable.
- Code search returns up to 20 snippets and a `truncated` flag, not a cursor.
- File content uses 1-based inclusive line ranges, capped at 500 lines per call.
  Truncated results include `nextStartLine`; binary/unavailable text returns
  `contentAvailable: false`.
- Job log tails default to 200 lines and accept 1–500; results include
  `text`, `totalLines`, and `truncated`.
- `gitlab_assign_issue` replaces all assignees; an empty array removes all.
  An empty/omitted milestone removes the assignment in `gitlab_assign_issue_milestone`.
- `gitlab_list_dir` is non-recursive unless `recursive: true` is passed.
- `gitlab_compare_refs` returns commits newest first (up to `items`) and up to
  200 changed file paths without diff content; `commitsTruncated`,
  `diffsTruncated`, `compareTimeout`, and `sameRef` flag partial results.
- `gitlab_update_merge_request` sends only supplied fields; a blank `title` or
  `targetBranch` is a `VALIDATION_ERROR`, while an empty `description` clears it.
  `assignees`/`reviewers` replace the whole list; an empty array removes everyone.
- `gitlab_merge_merge_request` merges only if the source branch HEAD still equals
  `sha` when it is supplied; a blank `sha` is rejected.
- Pipeline variables are `{key, value}` pairs of type `env_var`. Pipelines and
  jobs are addressed by project-scoped `pipelineIid` and `jobName`; GitLab
  creates a new job attempt with the same name on retry.
- GitLab 405/406 responses for objects in an unsuitable state (for example, a
  merge request that cannot be merged or a pipeline that cannot be canceled)
  are reported as `VALIDATION_ERROR`.
- Write tools are disabled by default (`GITLAB_WRITE_ENABLED=false`). There are
  no tools for deleting issues, branches, or merge requests.

### Example tool calls

After MCP initialization, a `tools/call` JSON-RPC request looks like this:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "gitlab_list_project_issues",
    "arguments": {
      "projectFullPath": "team/project",
      "state": "opened",
      "assigneeUsername": "alice",
      "items": 10
    }
  }
}
```

An issue creation call uses these parameters (ask for approval before writing):

```json
{
  "name": "gitlab_create_issue",
  "arguments": {
    "projectFullPath": "team/project",
    "title": "Investigate failing build",
    "description": "The unit job fails on the main branch.",
    "labels": ["bug"]
  }
}
```

Successful calls return `result.isError: false`, a machine-readable
`result.structuredContent` object, and the same JSON serialized in a text
content block for older clients. A paginated payload has this shape:

```json
{
  "items": [],
  "pageInfo": {
    "endCursor": null,
    "hasNextPage": false
  }
}
```

Application errors return `result.isError: true` and a structured payload such as:

```json
{
  "code": "NOT_FOUND",
  "message": "Object not found"
}
```

Expected error codes are `VALIDATION_ERROR`, `NOT_FOUND`, `ACCESS_DENIED`, and
`GITLAB_ERROR`. Unexpected runtime failures currently use `exception`.
MCP/framework validation errors may instead contain text-only tool errors;
unknown tool names return a JSON-RPC error. Do not assume every failure has
`structuredContent`, or that HTTP 200 means a successful tool execution.

## GitLab compatibility

Tested on **GitLab 14**, with GraphQL operations verified against its schema.
Compatibility with other GitLab versions has not been established.
The automated suite uses synthetic HTTP fixtures and is not proof of live
compatibility with every GitLab installation.

Current limits: one GitLab instance and shared token per process; no per-agent
GitLab identities. Milestone assignment currently resolves titles from the
first 100 project milestones. GitLab 14 merge request diffs are fetched as a
whole and paginated locally; upstream diff limits can still truncate results.

## Build & test

```bash
./mvnw package
```

This compiles the application, runs unit and HTTP integration tests, and builds
the JVM package. Tests cover all 49 tools through the real MCP endpoint;
only the external GitLab API is replaced. No real GitLab token is required.
Both GitLab clients are also tested against trusted and untrusted HTTPS.

For the full Maven verification lifecycle:

```bash
./mvnw clean verify
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for test and contribution guidance.
To run it:

```bash
GITLAB_BASE_URI=https://gitlab.example.com TOKEN_GITLAB=<token> \
  java -jar target/quarkus-app/quarkus-run.jar
```

## Docker

### Requirements

Install JDK 25 and Docker with the Compose plugin. Use the Maven Wrapper
included in the repository. Run the following commands from the project root.

Compose builds the image using `src/main/docker/Dockerfile.jvm`; it does not
compile Java sources, so the JVM package must be built first.

### First deployment

1. Create a local configuration file. If `.env` already exists, edit it
   instead of overwriting it:

   ```bash
   cp .env.example .env
   chmod 600 .env
   ```

2. Edit `.env`, replacing both placeholders with your GitLab token and URL:

   ```dotenv
   TOKEN_GITLAB=replace-with-your-gitlab-token
   GITLAB_BASE_URI=https://gitlab.example.com
   GITLAB_WRITE_ENABLED=false
   ```

   The example token is sufficient for image builds only; replace it with a
   real token before using the server. Set `GITLAB_WRITE_ENABLED=true` to publish
   write tools; they require the permissions described in [Requirements](#requirements).

3. Build the JVM package and the image, then start the server:

   ```bash
   ./mvnw package
   docker compose build
   docker compose up -d
   ```

4. Check the container and startup logs:

   ```bash
   docker compose ps
   docker compose logs --tail=100
   ```

   Connect your MCP client to `http://localhost:8004/mcp` using Streamable HTTP.
   The published port is bound to localhost only. For remote access, use an
   authenticated reverse proxy; do not expose this unauthenticated server
   directly to the internet.

Compose automatically reads `.env`. Exported shell variables take precedence
over values in that file. A non-empty `TOKEN_GITLAB` is required even for
`docker compose build`, because Compose validates the whole configuration.

The local `.env` is excluded from Git and the Docker build context.
Only `.env.example` is intended for publication. The token and GitLab URL
are passed to the running container, not embedded in the image.

### Update and stop

After updating the source code, rebuild and recreate the service:

```bash
./mvnw package
docker compose up -d --build
```

After changing only `.env`, run `docker compose up -d` to apply the new values.

To follow logs or stop and remove the container and Compose network:

```bash
docker compose logs -f
docker compose down
```

### Optional Docker smoke-test

After building the image, run a real-container protocol check against a local
scripted GitLab API (no real GitLab credentials or writes):

```bash
./mvnw test -Dtest=DockerSmokeTest -Ddocker.smoke=true
```

The test enables write tools, initializes MCP, checks all 49 advertised tools, performs a read and
an issue creation through the packaged application, then removes its container.
Compose uses the stable image name `mcp-gitlab14:local`, regardless of the clone directory.
The test requires Docker and this already built image;
use `-Ddocker.image=your-image` for another image name. It is not a live GitLab
compatibility test and is skipped by the default Maven test run.

## License

[MIT](LICENSE) — Copyright (c) 2026 Alexander Balashov.

The Apache Maven Wrapper retains its Apache-2.0 license.
See [third-party notices](THIRD_PARTY_NOTICES.md).

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and
[CHANGELOG.md](CHANGELOG.md). Report vulnerabilities privately, not in public issues.
