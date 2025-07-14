package com.aem.mcp.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GptServiceOldTest {

    @Test
    public void returnsStaticQuery() {
        GptServiceOld service = new GptServiceOld();
        String query = service.generateJcrQuery("any question");
        assertNotNull(query);
        assertFalse(query.isEmpty());
        assertTrue(query.contains("SELECT * FROM [cq:Page]"));
    }
}
