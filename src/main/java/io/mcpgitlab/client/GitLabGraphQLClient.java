package io.mcpgitlab.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import io.mcpgitlab.dto.GraphQLRequest;

import java.util.Map;

@RegisterRestClient(configKey = "gitlab-client")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitLabGraphQLClient {

    /**
     * Executes a read-only GraphQL query against GitLab.
     *
     * @param privateToken GitLab service token
     * @param request GraphQL query and its variables
     * @return unmodified GitLab GraphQL response
     */
    @POST
    @Path("/api/graphql")
    Map<String, Object> execute(
            @HeaderParam("PRIVATE-TOKEN") String privateToken,
            GraphQLRequest request
    );
}
