package com.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.Base64;

@Service
public class GptService {
    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    public String generateJcrQuery(String userQuestion) {
        try {
            String prompt = String.format(
                    "{\n" +
                            "  \"model\": \"gpt-4\",\n" +
                            "  \"messages\": [\n" +
                            "    {\"role\": \"system\", \"content\": \"You are an AEM JCR-SQL2 expert. Translate the user's question into a precise JCR-SQL2 query. Always use [cq:Page] as the main type with alias 's', and filter using s.[jcr:content/jcr:createdBy] or other jcr:content-level fields. Only return the SQL2 string, no explanation.\"},\n" +
                            "    {\"role\": \"user\", \"content\": \"%s\"}\n" +
                            "  ]\n" +
                            "}\n",
                    userQuestion);

            HttpURLConnection conn = (HttpURLConnection) new URL(OPENAI_ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + "OPENAI_AK");
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(prompt.getBytes());
            }

            Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String responseBody = scanner.hasNext() ? scanner.next() : "";

            JsonNode json = new ObjectMapper().readTree(responseBody);
            return json.get("choices").get(0).get("message").get("content").asText();

        } catch (Exception e) {
            return "Error calling OpenAI: " + e.getMessage();
        }
    }
}