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
import java.util.Base64;

/**
 * AEM Servlet to generate a JSON report of a component's dialog fields.
 *
 * This servlet binds directly to a specific path, providing a reliable
 * API endpoint under /bin.
 *
 * How to access:
 * http://localhost:4502/bin/report?componentPath=/apps/mysite/components/mycomponent
 */
@Component(service = { Servlet.class }, scope = ServiceScope.PROTOTYPE)
@SlingServletPaths(
        value = "/bin/report"
)
public class DialogFieldReportServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException {
        // Set the response content type to JSON
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Get the component path from the request parameter
        String componentPath = req.getParameter("componentPath");
        JSONObject finalJson = new JSONObject();

        if (componentPath == null || componentPath.isEmpty()) {
            resp.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            finalJson.put("error", "Request parameter 'componentPath' is missing.");
            resp.getWriter().write(finalJson.toString(2));
            return;
        }

        // Construct the URL to fetch the dialog definition using the resource merger
        String dialogJsonUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort()
                + "/mnt/override/" + componentPath.replaceFirst("^/", "") + "/_cq_dialog.-1.json";

        try {
            URL url = new URL(dialogJsonUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Add Basic Authentication header (replace with your credentials or a service user)
            String username = "admin";
            String password = "admin";
            String encodedAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8"));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                JSONObject dialogJson = new JSONObject(new JSONTokener(is));

                // Standard path for dialogs with tabs
                JSONObject tabs = dialogJson.optJSONObject("content")
                        .optJSONObject("items")
                        .optJSONObject("tabs")
                        .optJSONObject("items");

                if (tabs != null) {
                    for (String tabKey : tabs.keySet()) {
                        Object tabValue = tabs.get(tabKey);
                        if (tabValue instanceof JSONObject) {
                            JSONArray fieldsInTab = new JSONArray();
                            findFieldsInNode((JSONObject) tabValue, fieldsInTab);
                            if (!fieldsInTab.isEmpty()) {
                                finalJson.put(tabKey, fieldsInTab);
                            }
                        }
                    }
                } else {
                    finalJson.put("error", "Could not find a standard tab structure in the dialog JSON.");
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

        // Write the final JSON object to the response
        resp.getWriter().write(finalJson.toString(2));
    }

    /**
     * Recursively finds all fields within a given JSON node and adds them to a JSON array.
     *
     * @param node The current JSONObject to process.
     * @param fieldArray The JSONArray to which field information will be added.
     */
    private void findFieldsInNode(JSONObject node, JSONArray fieldArray) {
        // A field is identified by the presence of a 'fieldLabel' property.
        if (node.has("fieldLabel") && !node.optString("fieldLabel").isEmpty()) {
            JSONObject fieldDetails = new JSONObject();
            String name = node.optString("name", "").replace("./", "");
            String type = node.optString("sling:resourceType", "");

            fieldDetails.put("name", name);
            fieldDetails.put("type", type);

            // Add example values based on the field type
            addExampleValues(type, node, fieldDetails);

            fieldArray.put(fieldDetails);
        }

        // Recurse through all child nodes
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
