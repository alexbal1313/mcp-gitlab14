package io.mcpgitlab;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class GitLabTestResource implements QuarkusTestResourceLifecycleManager {
    private GitLabStub stub;

    @Override
    public Map<String, String> start() {
        try {
            stub = new GitLabStub();
            return Map.of("gitlab.base.uri", stub.url(), "gitlab.token", "test-token",
                    "quarkus.http.test-port", "0",
                    "quarkus.log.category.\"io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler\".level", "WARN");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void inject(TestInjector injector) {
        injector.injectIntoFields(stub, new TestInjector.MatchesType(GitLabStub.class));
    }

    @Override
    public void stop() {
        if (stub != null) {
            stub.close();
        }
    }
}
