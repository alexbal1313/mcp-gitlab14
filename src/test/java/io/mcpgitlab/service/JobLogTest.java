package io.mcpgitlab.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests job log tail truncation (synthetic log: the instance has no pipeline with a non-empty log).
 */
class JobLogTest {

    private final GitLabReadService service = new GitLabReadService();

    @SuppressWarnings("unchecked")
    private Map<String, Object> jobLog(String body, int limit, int status) throws Exception {
        Method method = GitLabReadService.class.getDeclaredMethod("jobLog", String.class, int.class, int.class);
        method.setAccessible(true);
        try {
            return (Map<String, Object>) method.invoke(service, body, limit, status);
        } catch (InvocationTargetException exception) {
            throw (RuntimeException) exception.getTargetException();
        }
    }

    @Test
    void tailOfLongLog() throws Exception {
        Map<String, Object> result = jobLog("line1\nline2\nline3\nline4\nline5", 2, 200);
        assertEquals("line4\nline5", result.get("text"));
        assertEquals(5, result.get("totalLines"));
        assertEquals(true, result.get("truncated"));
    }

    @Test
    void wholeShortLog() throws Exception {
        Map<String, Object> result = jobLog("line1\nline2\nline3", 200, 200);
        assertEquals("line1\nline2\nline3", result.get("text"));
        assertEquals(3, result.get("totalLines"));
        assertEquals(false, result.get("truncated"));
    }

    @Test
    void emptyLog() throws Exception {
        Map<String, Object> result = jobLog("", 200, 200);
        assertEquals("", result.get("text"));
        assertEquals(0, result.get("totalLines"));
        assertFalse((Boolean) result.get("truncated"));
    }

    @Test
    void accessDeniedStatus() throws Exception {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> jobLog("ignored", 10, 403));
        Field code = exception.getClass().getDeclaredField("code");
        code.setAccessible(true);
        assertEquals("ACCESS_DENIED", code.get(exception));
    }
}
