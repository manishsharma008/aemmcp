package com.invest.saudi.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AEM Servlet to generate all possible configuration combinations for a component's dialog.
 *
 * This servlet inspects a component's dialog, extracts all fields and their possible values,
 * and then calculates the Cartesian product to generate every unique configuration.
 *
 * How to access:
 * http://localhost:4502/bin/component-configs?componentPath=/apps/mysite/components/mycomponent
 */
@Component(service = { Servlet.class }, scope = ServiceScope.PROTOTYPE)
@SlingServletPaths(
        value = "/bin/component-configs"
)
public class ComponentConfigurationServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_COMBINATIONS_LIMIT = 10000; // Safety limit

    @Override
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String componentPath = req.getParameter("componentPath");
        JSONObject finalJson = new JSONObject();

        if (componentPath == null || componentPath.isEmpty()) {
            resp.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            finalJson.put("error", "Request parameter 'componentPath' is missing.");
            resp.getWriter().write(finalJson.toString(2));
            return;
        }

        String dialogJsonUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort()
                + "/mnt/override/" + componentPath.replaceFirst("^/", "") + "/_cq_dialog.-1.json";

        try {
            URL url = new URL(dialogJsonUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // IMPORTANT: In a real-world scenario, use a service user or token-based auth.
            String username = "admin";
            String password = "admin";
            String encodedAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8"));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                JSONObject dialogJson = new JSONObject(new JSONTokener(is));

                // Extract all fields and their possible values into a flat list
                List<JSONObject> allFields = extractFields(dialogJson);

                if (allFields.isEmpty()) {
                    finalJson.put("message", "No configurable fields with values were found in the dialog.");
                } else {
                    // Calculate all possible combinations
                    List<JSONObject> combinations = generateCombinations(allFields);

                    finalJson.put("component", componentPath);
                    finalJson.put("totalPossibleConfigurations", combinations.size());

                    if (combinations.size() > MAX_COMBINATIONS_LIMIT) {
                        finalJson.put("warning", "The number of combinations exceeds the safety limit of " + MAX_COMBINATIONS_LIMIT + ". Only a subset is being returned.");
                        finalJson.put("configurations", new JSONArray(combinations.subList(0, MAX_COMBINATIONS_LIMIT)));
                    } else {
                        finalJson.put("configurations", new JSONArray(combinations));
                    }
                }

            } else {
                resp.setStatus(conn.getResponseCode());
                finalJson.put("error", "Failed to fetch dialog JSON from AEM.");
                finalJson.put("status", conn.getResponseCode());
                finalJson.put("url", dialogJsonUrl);
            }

        } catch (Exception e) {
            resp.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            finalJson.put("error", "An exception occurred while processing the request.");
            finalJson.put("exception_type", e.getClass().getName());
            finalJson.put("exception_message", e.getMessage());
        }

        resp.getWriter().write(finalJson.toString(2));
    }

    /**
     * Extracts all fields from the dialog structure into a flat list.
     *
     * @param dialogJson The entire dialog JSON.
     * @return A list of JSONObjects, each representing a field with its name and possible values.
     */
    private List<JSONObject> extractFields(JSONObject dialogJson) {
        List<JSONObject> fields = new ArrayList<>();
        // Standard path for dialogs with tabs
        JSONObject tabs = dialogJson.optJSONObject("content")
                .optJSONObject("items")
                .optJSONObject("tabs")
                .optJSONObject("items");

        if (tabs != null) {
            for (String tabKey : tabs.keySet()) {
                Object tabValue = tabs.get(tabKey);
                if (tabValue instanceof JSONObject) {
                    findFieldsInNode((JSONObject) tabValue, fields);
                }
            }
        } else {
            // Fallback for dialogs without a tab structure
            findFieldsInNode(dialogJson, fields);
        }
        return fields;
    }

    /**
     * Recursively finds all fields within a given JSON node and adds them to a list.
     *
     * @param node The current JSONObject to process.
     * @param fieldList The list to which field information will be added.
     */
    private void findFieldsInNode(JSONObject node, List<JSONObject> fieldList) {
        // A field is identified by having a 'name' property.
        if (node.has("name") && node.has("sling:resourceType")) {
            JSONObject fieldDetails = new JSONObject();
            String name = node.optString("name", "").replace("./", "");
            String type = node.optString("sling:resourceType", "");

            if (!name.isEmpty()) {
                fieldDetails.put("name", name);
                // Add example values based on the field type
                addExampleValues(type, node, fieldDetails);

                // Only add fields that have possible values to combine
                if (fieldDetails.has("values")) {
                    fieldList.add(fieldDetails);
                }
            }
        }

        // Recurse through all child nodes
        for (String key : node.keySet()) {
            Object value = node.get(key);
            if (value instanceof JSONObject) {
                findFieldsInNode((JSONObject) value, fieldList);
            } else if (value instanceof JSONArray) {
                for (Object item : (JSONArray) value) {
                    if (item instanceof JSONObject) {
                        findFieldsInNode((JSONObject) item, fieldList);
                    }
                }
            }
        }
    }

    /**
     * Adds a "values" key with example data to a field's JSON object based on its type.
     *
     * @param type The sling:resourceType of the field.
     * @param node The JSONObject of the field definition.
     * @param fieldDetails The JSONObject where the example values will be added.
     */
    private void addExampleValues(String type, JSONObject node, JSONObject fieldDetails) {
        JSONArray values = new JSONArray();
        boolean valuesFound = false;

        if (type.contains("select")) {
            JSONObject items = node.optJSONObject("items");
            if (items != null) {
                for (String key : items.keySet()) {
                    if (items.opt(key) instanceof JSONObject) {
                        JSONObject opt = items.optJSONObject(key);
                        String val = opt.optString("value");
                        // Ignore empty 'Select...' options
                        if (!val.isEmpty()) {
                            values.put(val);
                        }
                    }
                }
                valuesFound = !values.isEmpty();
            }
        } else if (type.contains("pathfield")) {
            values.put("/content/dam/example.png");
            valuesFound = true;
        } else if (type.contains("checkbox")) {
            // A checkbox represents a boolean choice for its 'name' property
            values.put("true");
            values.put("false");
            valuesFound = true;
        } else if (type.equals("granite/ui/components/coral/foundation/form/textfield") || type.equals("cq/gui/components/authoring/dialog/richtext")) {
            values.put("Sample Text");
            valuesFound = true;
        } else if (type.equals("granite/ui/components/coral/foundation/form/numberfield")) {
            values.put(123);
            valuesFound = true;
        }

        if (valuesFound) {
            fieldDetails.put("values", values);
        }
    }

    /**
     * Generates the Cartesian product of all possible field values.
     *
     * @param fields A list of fields, each with a 'name' and a 'values' array.
     * @return A list of JSONObjects, where each object is a unique configuration.
     */
    private List<JSONObject> generateCombinations(List<JSONObject> fields) {
        List<JSONObject> combinations = new ArrayList<>();
        if (fields == null || fields.isEmpty()) {
            return combinations;
        }

        // Start the recursive generation
        generateCombinationsRecursive(fields, 0, new JSONObject(), combinations);
        return combinations;
    }

    /**
     * The recursive helper function to build combinations.
     *
     * @param fields The list of all fields to process.
     * @param index The index of the current field being processed.
     * @param currentCombination The combination being built in the current recursion path.
     * @param allCombinations The final list where complete combinations are stored.
     */
    private void generateCombinationsRecursive(List<JSONObject> fields, int index, JSONObject currentCombination, List<JSONObject> allCombinations) {
        // Safety break to prevent heap space errors on extremely complex components
        if (allCombinations.size() >= MAX_COMBINATIONS_LIMIT) {
            return;
        }

        // Base case: If we have processed all fields, this is a complete combination
        if (index == fields.size()) {
            allCombinations.add(new JSONObject(currentCombination.toString())); // Add a copy
            return;
        }

        JSONObject field = fields.get(index);
        String name = field.getString("name");
        JSONArray values = field.getJSONArray("values");

        // Recursive step: Iterate through values of the current field
        for (int i = 0; i < values.length(); i++) {
            Object value = values.get(i);
            currentCombination.put(name, value);
            generateCombinationsRecursive(fields, index + 1, currentCombination, allCombinations);
        }
    }
}
