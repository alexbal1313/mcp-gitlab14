package io.mcpgitlab.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Normalizers of the read extensions: branches, compare, project members, server info.
 */
class ReadExtensionsTest {

    private final GitLabReadService service = new GitLabReadService();

    @Test
    void branchListMapsRestFields() {
        Map<String, Object> commit = Map.of("id", "abc123", "short_id", "abc", "title", "init",
                "committed_date", "2026-11-19T10:00:00Z", "author_name", "Dev");
        Map<String, Object> branch = Map.of("name", "feature/x", "default", false, "protected", true, "merged", false,
                "web_url", "https://gitlab.example.com/p/-/tree/feature/x", "commit", commit, "developers_can_push", true,
                "developers_can_merge", false);

        Map<String, Object> item = service.branchList(branch);
        assertEquals("feature/x", item.get("name"));
        assertEquals(true, item.get("protected"));
        assertEquals("https://gitlab.example.com/p/-/tree/feature/x", item.get("webUrl"));
        assertEquals(Map.of("shortId", "abc", "title", "init", "committedDate", "2026-11-19T10:00:00Z"), item.get("commit"));
        assertFalse(item.containsKey("developersCanPush"));

        Map<String, Object> detail = service.branchDetail(branch);
        assertEquals(true, detail.get("developersCanPush"));
        assertEquals(false, detail.get("developersCanMerge"));
        @SuppressWarnings("unchecked")
        Map<String, Object> detailCommit = (Map<String, Object>) detail.get("commit");
        assertEquals("abc123", detailCommit.get("sha"));
        assertEquals("Dev", detailCommit.get("authorName"));
    }

    @Test
    void compareTruncatesCommitsNewestFirst() {
        List<Object> commits = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            commits.add(Map.of("id", "sha" + index, "short_id", "s" + index, "title", "c" + index));
        }
        Map<String, Object> source = new HashMap<>();
        source.put("commits", commits);
        source.put("diffs", List.of(Map.of("old_path", "a", "new_path", "a", "new_file", false,
                "deleted_file", false, "renamed_file", false, "diff", "@@ secret @@")));
        source.put("compare_timeout", false);
        source.put("compare_same_ref", false);

        Map<String, Object> result = service.compareResult(source, 2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("commits");
        assertEquals(2, items.size());
        assertEquals("sha5", items.get(0).get("sha"));
        assertEquals("sha4", items.get(1).get("sha"));
        assertEquals(true, result.get("commitsTruncated"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diffs = (List<Map<String, Object>>) result.get("diffs");
        assertEquals("a", diffs.getFirst().get("newPath"));
        assertFalse(diffs.getFirst().containsKey("diff"));
        assertEquals(false, result.get("diffsTruncated"));
    }

    @Test
    void compareTruncatesDiffsAndMapsFlags() {
        List<Object> diffs = new ArrayList<>();
        for (int index = 0; index < GitLabReadService.MAX_COMPARE_DIFFS + 1; index++) {
            diffs.add(Map.of("new_path", "f" + index));
        }
        Map<String, Object> source = Map.of("commits", List.of(), "diffs", diffs,
                "compare_timeout", true, "compare_same_ref", true);

        Map<String, Object> result = service.compareResult(source, 20);
        assertEquals(GitLabReadService.MAX_COMPARE_DIFFS, ((List<?>) result.get("diffs")).size());
        assertEquals(true, result.get("diffsTruncated"));
        assertEquals(false, result.get("commitsTruncated"));
        assertEquals(true, result.get("compareTimeout"));
        assertEquals(true, result.get("sameRef"));
    }

    @Test
    void memberMapsUserAndAccessLevel() {
        Map<String, Object> node = Map.of("accessLevel", Map.of("stringValue", "MAINTAINER"),
                "user", Map.of("username", "dev", "name", "Developer"));
        Map<String, Object> member = service.member(node);
        assertEquals("dev", member.get("username"));
        assertEquals("Developer", member.get("name"));
        assertEquals("MAINTAINER", member.get("accessLevel"));

        Map<String, Object> empty = service.member(Map.of());
        assertNull(empty.get("username"));
        assertNull(empty.get("accessLevel"));
    }

    @Test
    void serverInfoHasNoToken() {
        service.baseUri = "https://gitlab.example.com";
        service.privateToken = "secret-token";
        Map<String, Object> result = service.serverInfoResult(Map.of("version", "14.10.3-ee", "revision", "d118e6c477f"));
        assertEquals(Map.of("version", "14.10.3-ee", "revision", "d118e6c477f", "baseUri", "https://gitlab.example.com"), result);
        assertTrue(result.values().stream().noneMatch("secret-token"::equals));
    }
}
