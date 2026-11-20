package io.mcpgitlab.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REST v4 returns the commit SHA in the {@code id} field; normalized models must populate {@code sha}.
 */
class CommitShaTest {

    private final GitLabReadService service = new GitLabReadService();

    private static final Map<String, Object> REST_COMMIT = Map.of(
            "id", "0123456789abcdef0123456789abcdef01234567", "short_id", "01234567", "title", "init",
            "message", "init\n", "author_name", "Dev", "author_email", "dev@example.com",
            "authored_date", "2026-11-19T10:00:00Z", "committed_date", "2026-11-19T10:00:00Z",
            "parent_ids", List.of("fedcba"), "web_url", "https://gitlab.example.com/p/-/commit/0123");

    @Test
    void listItemTakesShaFromId() {
        Map<String, Object> item = service.commitList(REST_COMMIT);
        assertEquals("0123456789abcdef0123456789abcdef01234567", item.get("sha"));
        assertEquals("01234567", item.get("shortId"));
    }

    @Test
    void detailTakesShaFromId() {
        Map<String, Object> detail = service.commitDetail(REST_COMMIT);
        assertEquals("0123456789abcdef0123456789abcdef01234567", detail.get("sha"));
        assertEquals("2026-11-19T10:00:00Z", detail.get("committedDate"));
        assertEquals(List.of("fedcba"), detail.get("parentIds"));
    }

    @Test
    void tagCommitTakesShaFromId() {
        Map<String, Object> tag = Map.of("name", "v0.1", "target", "0123", "message", "", "protected", false,
                "commit", REST_COMMIT);
        @SuppressWarnings("unchecked")
        Map<String, Object> commit = (Map<String, Object>) service.tagDetail(tag).get("commit");
        assertEquals("0123456789abcdef0123456789abcdef01234567", commit.get("sha"));
        assertEquals("01234567", commit.get("shortId"));
    }
}
