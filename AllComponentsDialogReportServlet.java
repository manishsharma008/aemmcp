package com.invest.saudi.core.servlets;


import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import javax.jcr.query.Query;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Iterator;

/**
 * AEM Servlet to generate a master JSON report of dialog fields for all components
 * found under a given search path.
 *
 * This servlet binds directly to a specific path, providing a reliable
 * API endpoint under /bin.
 *
 * How to access:
 * http://localhost:4502/bin/report?searchPath=/apps/mysite/components
 */
@Component(service = { Servlet.class }, scope = ServiceScope.PROTOTYPE)
@SlingServletPaths(
        value = "/bin/allreport"
)
public class AllComponentsDialogReportServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException {
        // Set content type to JSON for the final output.
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Get the search path from the request parameter, with a default value.
        String searchPath = req.getParameter("searchPath");
        if (searchPath == null || searchPath.isEmpty()) {
            searchPath = "/apps/trp-compose/components";
        }

        JSONObject finalResponse = new JSONObject();
        JSONArray componentsArray = new JSONArray();
        ResourceResolver resolver = req.getResourceResolver();

        // JCR-SQL2 query to find all components under the search path.
        final String query = "SELECT * FROM [cq:Component] AS s WHERE ISDESCENDANTNODE(s, '" + searchPath + "')";
        Iterator<Resource> components = resolver.findResources(query, Query.JCR_SQL2);

        while (components.hasNext()) {
            Resource component = components.next();
            String componentPath = component.getPath();
            String componentName = component.getName();

            // Get the dialog JSON for the current component.
            JSONObject dialogJson = getDialogForComponent(componentPath, req);

            // Only add components that have a valid dialog report (not an error object).
            if (dialogJson != null && !dialogJson.has("error")) {
                JSONObject componentData = new JSONObject();
                componentData.put("componentName", componentName);
                componentData.put("componentPath", componentPath);
                componentData.put("componentConfig", dialogJson);
                componentsArray.put(componentData);
            }
        }

        finalResponse.put("components", componentsArray);
        resp.getWriter().write(finalResponse.toString(4)); // Pretty print with an indent of 4
    }

    /**
     * Fetches and parses the dialog for a single component.
     * @param componentPath The path of the component.
     * @param req The SlingHttpServletRequest to construct the fetch URL.
     * @return A JSONObject representing the parsed dialog, or null if not found or an error occurs.
     */
    private JSONObject getDialogForComponent(String componentPath, SlingHttpServletRequest req) {
        JSONObject componentReport = new JSONObject();
        String dialogJsonUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort()
                + "/mnt/override/" + componentPath.replaceFirst("^/", "") + "/_cq_dialog.-1.json";

        try {
            URL url = new URL(dialogJsonUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Use hardcoded admin credentials. In a real-world scenario, a service user is recommended.
            String username = "admin";
            String password = "admin";
            String encodedAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8"));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                JSONObject dialogJson = new JSONObject(new JSONTokener(is));

                JSONObject tabs = dialogJson.optJSONObject("content")
                        .optJSONObject("items")
                        .optJSONObject("tabs")
                        .optJSONObject("items");

                if (tabs != null) {
                    for (String tabKey : tabs.keySet()) {
                        if (tabs.get(tabKey) instanceof JSONObject) {
                            JSONArray fieldsInTab = new JSONArray();
                            findFieldsInNode(tabs.getJSONObject(tabKey), fieldsInTab);
                            if (!fieldsInTab.isEmpty()) {
                                componentReport.put(tabKey, fieldsInTab);
                            }
                        }
                    }
                } else {
                    // Handle dialogs without a standard tab structure
                    JSONArray fieldsList = new JSONArray();
                    findFieldsInNode(dialogJson, fieldsList);
                    if (!fieldsList.isEmpty()) {
                        componentReport.put("fields", fieldsList);
                    } else {
                        componentReport.put("error", "Dialog found, but it has no tabs or recognizable fields.");
                    }
                }
            } else {
                // This handles the case where the dialog doesn't exist (404) or other errors.
                return null;
            }
        } catch (Exception e) {
            // Return null on exception to indicate failure
            return null;
        }
        return componentReport;
    }

    /**
     * Recursively finds all fields within a given JSON node and adds them to a JSON array.
     * @param node The current JSONObject to process.
     * @param fieldArray The JSONArray to which field information will be added.
     */
    private void findFieldsInNode(JSONObject node, JSONArray fieldArray) {
        if (node.has("fieldLabel") && !node.optString("fieldLabel").isEmpty()) {
            JSONObject fieldDetails = new JSONObject();
            String name = node.optString("name", "").replace("./", "");
            String type = node.optString("sling:resourceType", "");

            fieldDetails.put("name", name);
            fieldDetails.put("type", type);
            addExampleValues(type, node, fieldDetails);
            fieldArray.put(fieldDetails);
        }

        for (String key : node.keySet()) {
            Object value = node.get(key);
            if (value instanceof JSONObject) {
                findFieldsInNode((JSONObject) value, fieldArray);
            } else if (value instanceof JSONArray) {
                for (Object item : (JSONArray) value) {
                    if (item instanceof JSONObject) {
                        findFieldsInNode((JSONObject) item, fieldArray);
                    }
                }
            }
        }
    }

    /**
     * Adds a "values" key with example data to a field's JSON object based on its type.
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
                        if (val != null && !val.isEmpty()) {
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
            values.put("true");
            values.put("false");
            valuesFound = true;
        } else if (type.equals("granite/ui/components/coral/foundation/form/textfield") || type.equals("cq/gui/components/authoring/dialog/richtext")) {
            values.put("laura ipsum");
            valuesFound = true;
        } else if (type.equals("granite/ui/components/coral/foundation/form/numberfield")) {
            values.put(1234567);
            valuesFound = true;
        }

        if (valuesFound) {
            fieldDetails.put("values", values);
        }
    }
}
