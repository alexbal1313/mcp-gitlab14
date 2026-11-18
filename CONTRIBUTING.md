# Contributing

Bug reports and focused pull requests are welcome. For vulnerabilities,
follow [SECURITY.md](SECURITY.md), not a public issue.

## Development

Use JDK 25 (including `keytool`) and the Maven Wrapper:

```bash
./mvnw clean verify
```

The default test suite needs no real GitLab token or network access to a GitLab
instance. Maven dependencies may need to be downloaded on the first build.
Tests start the real Quarkus application and send MCP requests over HTTP.
Only the external GitLab API is replaced by a local scripted HTTP server.
TLS tests generate temporary certificates and check both GitLab clients.

Contract fixtures are in `src/test/resources/contracts/`.
Use synthetic data and fake tokens only. Never commit your local `.env`,
credentials, internal GitLab data, or private project code.

## Changes

- Keep changes small and preserve tool names, input fields, and output contracts.
- Add regression tests through public interfaces for behavior changes.
- Keep source comments, tool descriptions, and public documentation in English.
- Document user-visible changes in [CHANGELOG.md](CHANGELOG.md).
- Explain the problem, solution, and commands you ran in the pull request.
- Only contribute code you have the right to submit under the project license.

Run write-operation checks only in a disposable test project you control;
delete or close created issues and remove other test artifacts afterwards.
Do not point the automated contract suite at production GitLab.

## Optional container check

After `./mvnw package` and `docker compose --env-file .env.example build`, run:

```bash
./mvnw test -Dtest=DockerSmokeTest -Ddocker.smoke=true
```

Unset shell GitLab variables if you want Compose to use only the example file.
The check uses the built image, a random host port, and a local fake GitLab;
its container is removed in a `finally` block. The default image name is
`mcp-gitlab14:local`, independent of the clone directory; override it with
`-Ddocker.image=your-image`.
