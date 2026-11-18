# Security policy

## Reporting vulnerabilities

Do not disclose vulnerabilities, tokens, private project data, or exploit
details in public issues or pull requests.

Use this GitHub repository's **Security → Advisories → Report a vulnerability**
feature to submit a private report. Include the affected version, impact,
reproduction steps with synthetic data, and any proposed mitigation.

**Maintainer release prerequisite:** enable
[GitHub private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/working-with-repository-security-advisories/configuring-private-vulnerability-reporting-for-a-repository)
when creating the public repository, then verify that the report button is
available. This setting cannot be enabled by adding this file. If the button
is absent, a private reporting channel has not yet been established here;
do not post sensitive details publicly.

## Support scope

This is an early-stage project. Security fixes target the latest release;
older releases have no backport guarantee. No response-time SLA is promised.

## Deployment boundary

- The MCP endpoint has no configured client authentication. All callers use
  the same GitLab token, including for writes.
- Keep it on a trusted local interface or behind an authenticating TLS reverse
  proxy. The supplied Compose file publishes the port on localhost only.
- Limit the token's project access and permissions, rotate it regularly, and
  revoke it immediately if exposed.
- Runtime environment variables are visible to operators with Docker access.
  The token is not a Docker build argument and is not embedded in the image.
- Do not disable TLS certificate or hostname verification. Install your
  organization's CA when accessing GitLab with a private certificate chain.
- Treat GitLab content and tool output as untrusted input to an AI agent.
  Require approval before writes; issue text and code can contain prompt injection.
