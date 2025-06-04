package com.aem.mcp.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpController {
    private final GptService gptService;
    private final AemQueryService aemQueryService;

    public McpController(GptService gptService, AemQueryService aemQueryService) {
        this.gptService = gptService;
        this.aemQueryService = aemQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<String> query(@RequestBody Map<String, String> input) {
        String question = input.get("question");
        String jcrQuery = gptService.generateJcrQuery(question);
        String results = aemQueryService.runQuery(jcrQuery);
        jcrQuery = jcrQuery.replaceAll("\"","'");
        results = " {\n" +
                "  \"query\": \" "+jcrQuery+"\",\n" +
                "  \"data\":" +results + "}";
        return ResponseEntity.ok(results);
    }
}
