package com.aem.mcp.server;

import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

@Service
public class AemQueryService {

    private static final String AEM_ENDPOINT = "http://localhost:4502/bin/mcp/query";
    private static final String AEM_USER = "admin";
    private static final String AEM_PASSWORD = "admin";

    public String runQuery(String jcrSql2Query) {
        try {
            URL url = new URL(AEM_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Basic " + encodeAuth(AEM_USER, AEM_PASSWORD));
            conn.setRequestProperty("Content-Type", "text/plain");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jcrSql2Query.getBytes());
            }

            Scanner scanner = new Scanner(conn.getInputStream()).useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";

            conn.disconnect();
            return response;

        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String encodeAuth(String user, String pass) {
        String auth = user + ":" + pass;
        return java.util.Base64.getEncoder().encodeToString(auth.getBytes());
    }
}
