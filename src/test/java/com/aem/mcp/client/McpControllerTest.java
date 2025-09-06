package com.aem.mcp.client;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class McpControllerTest {

    @Test
    public void queryCombinesServices() {
        GptService gptService = Mockito.mock(GptService.class);
        AemQueryService aemQueryService = Mockito.mock(AemQueryService.class);

        Mockito.when(gptService.generateJcrQuery("test question"))
                .thenReturn("SELECT * FROM [cq:Page]");
        Mockito.when(aemQueryService.runQuery("SELECT * FROM [cq:Page]"))
                .thenReturn("[\"/content/page1\"]");

        McpController controller = new McpController(gptService, aemQueryService);

        ResponseEntity<String> response = controller.query(Map.of("question", "test question"));

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"query\""));
        assertTrue(response.getBody().contains("\"data\""));
        assertTrue(response.getBody().contains("SELECT * FROM [cq:Page]"));
    }
}
