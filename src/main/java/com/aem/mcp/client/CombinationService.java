package com.aem.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Service that expands JSON form definitions with select options into all valid combinations.
 */
@Service
public class CombinationService {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses the supplied JSON string and returns every valid combination of select options.
     * Static values are preserved and blank options are skipped.
     */
    public List<Map<String, String>> generateCombinations(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        Map<String, List<String>> fieldOptions = new LinkedHashMap<>();
        collectOptions(root, fieldOptions);
        List<Map.Entry<String, List<String>>> entries = new ArrayList<>(fieldOptions.entrySet());
        List<Map<String, String>> result = new ArrayList<>();
        build(entries, 0, new LinkedHashMap<>(), result);
        return result;
    }

    private void collectOptions(JsonNode node, Map<String, List<String>> map) {
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                List<String> vals = new ArrayList<>();
                value.forEach(v -> {
                    String s = v.asText();
                    if (s != null && !s.trim().isEmpty()) {
                        vals.add(s);
                    }
                });
                map.put(entry.getKey(), vals);
            } else if (value.isValueNode()) {
                map.put(entry.getKey(), Collections.singletonList(value.asText()));
            } else if (value.isObject()) {
                collectOptions(value, map);
            }
        });
    }

    private void build(List<Map.Entry<String, List<String>>> entries, int index,
                       Map<String, String> current, List<Map<String, String>> result) {
        if (index == entries.size()) {
            result.add(new LinkedHashMap<>(current));
            return;
        }
        Map.Entry<String, List<String>> entry = entries.get(index);
        for (String val : entry.getValue()) {
            current.put(entry.getKey(), val);
            build(entries, index + 1, current, result);
        }
    }
}
