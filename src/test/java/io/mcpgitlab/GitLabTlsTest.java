package io.mcpgitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.mcpgitlab.client.GitLabGraphQLClient;
import io.mcpgitlab.client.GitLabRestClientBase;
import io.mcpgitlab.client.GitLabRestReadClient;
import io.mcpgitlab.dto.GraphQLRequest;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GitLabTlsTest {
    @TempDir
    static Path directory;
    private static SSLContext trusted;
    private static SSLContext untrusted;
    private static Path trustStore;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void certificate() throws Exception {
        Path store = directory.resolve("gitlab.p12");
        trustStore = store;
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "gitlab-test",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", store.toString(), "-storetype", "PKCS12",
                "-storepass", "test-only", "-keypass", "test-only", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("keytool timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(directory.resolve("keytool.log")));
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(store)) {
            keys.load(input, "test-only".toCharArray());
        }
        var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, "test-only".toCharArray());
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keys);
        trusted = SSLContext.getInstance("TLS");
        trusted.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        var empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);
        trustManagers.init(empty);
        untrusted = SSLContext.getInstance("TLS");
        untrusted.init(null, trustManagers.getTrustManagers(), null);
    }

    private GitLabStub server() throws Exception {
        var https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(trusted));
        return new GitLabStub(https);
    }

    private static class RestClient extends GitLabRestReadClient {
        RestClient(String uri) {
            baseUri = uri;
        }
    }

    @Test
    void restAcceptsTrustedCertificate() throws Exception {
        SSLContext original = SSLContext.getDefault();
        try (var server = server()) {
            SSLContext.setDefault(trusted);
            server.script(JSON.readTree("""
                    [{"method":"GET","path":"/api/v4/projects/team%2Fproject","response":{"id":7}}]
                    """));
            assertEquals(200, new RestClient(server.url()).getProject("test-token", "team/project").status());
            server.verify(1);
        } finally {
            SSLContext.setDefault(original);
        }
    }

    @Test
    void restRejectsUntrustedCertificate() throws Exception {
        SSLContext original = SSLContext.getDefault();
        try (var server = server()) {
            SSLContext.setDefault(untrusted);
            server.script(JSON.createArrayNode());
            assertThrows(GitLabRestClientBase.GitLabRestException.class,
                    () -> new RestClient(server.url()).getProject("test-token", "team/project"));
            server.verify(0);
        } finally {
            SSLContext.setDefault(original);
        }
    }

    @Test
    void graphQLAcceptsTrustedCertificate() throws Exception {
        try (var server = server()) {
            server.script(JSON.readTree("""
                    [{"method":"POST","path":"/api/graphql","operation":"Project",
                      "response":{"data":{"project":{"id":7}}}}]
                    """));
            var client = graphQL(server.url(), trusted);
            try {
                assertEquals(Map.of("project", Map.of("id", 7)),
                        client.execute("test-token", new GraphQLRequest("query Project { project { id } }")).get("data"));
                server.verify(1);
            } finally {
                ((AutoCloseable) client).close();
            }
        }
    }

    @Test
    void graphQLRejectsUntrustedCertificate() throws Exception {
        try (var server = server()) {
            server.script(JSON.createArrayNode());
            var client = graphQL(server.url(), untrusted);
            try {
                assertThrows(ProcessingException.class,
                        () -> client.execute("test-token", new GraphQLRequest("query Project { project { id } }")));
                server.verify(0);
            } finally {
                ((AutoCloseable) client).close();
            }
        }
    }

    @Test
    void bothClientsUseConfiguredJvmTrustStore() throws Exception {
        SSLContext original = SSLContext.getDefault();
        var properties = new java.util.HashMap<String, String>();
        for (String key : java.util.List.of("javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreType")) {
            properties.put(key, System.getProperty(key));
        }
        try (var server = server()) {
            System.setProperty("javax.net.ssl.trustStore", trustStore.toString());
            System.setProperty("javax.net.ssl.trustStorePassword", "test-only");
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
            SSLContext refreshed = SSLContext.getInstance("TLS");
            refreshed.init(null, null, null);
            SSLContext.setDefault(refreshed);
            server.script(JSON.readTree("""
                    [{"method":"GET","path":"/api/v4/projects/team%2Fproject","response":{"id":7}},
                     {"method":"POST","path":"/api/graphql","operation":"Project",
                      "response":{"data":{"project":{"id":7}}}}]
                    """));
            assertEquals(200, new RestClient(server.url()).getProject("test-token", "team/project").status());
            var client = graphQL(server.url(), null);
            try {
                assertEquals(Map.of("project", Map.of("id", 7)),
                        client.execute("test-token", new GraphQLRequest("query Project { project { id } }")).get("data"));
            } finally {
                ((AutoCloseable) client).close();
            }
            server.verify(2);
        } finally {
            properties.forEach((key, value) -> {
                if (value == null) System.clearProperty(key); else System.setProperty(key, value);
            });
            SSLContext.setDefault(original);
        }
    }

    private GitLabGraphQLClient graphQL(String uri, SSLContext context) {
        var builder = RestClientBuilder.newBuilder().baseUri(URI.create(uri))
                .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS);
        if (context != null) builder.sslContext(context);
        return builder.build(GitLabGraphQLClient.class);
    }
}
