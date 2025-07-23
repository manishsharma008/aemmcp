package com.invest.saudi.core.servlets;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * AEM Servlet to automatically generate a test page by repeating a template node
 * for each possible configuration of a given component.
 *
 * This version uses a GET request for simplicity in development environments.
 *
 * WARNING: This servlet performs write operations via a GET request and uses the
 * session of the logged-in user. This is NOT a best practice for production.
 *
 * How to use:
 * Open the URL in your browser after logging into AEM as an admin.
 * http://localhost:4502/bin/create-test-page?componentPath=[path-to-component]&templatePagePath=[path-to-template-page]&destinationPath=[parent-path-for-new-page]&newPageName=[name-of-new-page]&nodeToRepeat=[name-of-node-to-repeat]&placeholderResourceType=[resource-type-of-placeholders]
 *
 * Example:
 * http://localhost:4502/bin/create-test-page?componentPath=/apps/trp-compose/components/button-container/v1/button-container&templatePagePath=/content/trp-ref/global/en/home/testpagetemplate&destinationPath=/content/trp-ref/global/en/home&newPageName=button-test-variations&nodeToRepeat=grid_layout_containe&placeholderResourceType=trp-ref/components/foundation/button-container
 */
@Component(service = { Servlet.class }, scope = ServiceScope.PROTOTYPE)
@SlingServletPaths(value = "/bin/create-test-page")
public class ComponentTestPageGeneratorServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 3L; // Version 3
    private static final int MAX_COMBINATIONS_LIMIT = 500; // Safety limit

    @Override
    protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        JSONObject responseJson = new JSONObject();
        ResourceResolver resolver = req.getResourceResolver();

        // --- 1. Get and Validate Parameters ---
        String componentPath = req.getParameter("componentPath");
        String templatePagePath = req.getParameter("templatePagePath");
        String destinationPath = req.getParameter("destinationPath");
        String newPageName = req.getParameter("newPageName");
        String nodeToRepeatName = req.getParameter("nodeToRepeat");
        String placeholderResourceType = req.getParameter("placeholderResourceType");

        if (isAnyBlank(componentPath, templatePagePath, destinationPath, newPageName, nodeToRepeatName, placeholderResourceType)) {
            resp.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            responseJson.put("error", "Missing required parameters. Please provide componentPath, templatePagePath, destinationPath, newPageName, nodeToRepeat, and placeholderResourceType.");
            resp.getWriter().write(responseJson.toString(2));
            return;
        }

        try {
            // --- 2. Generate All Component Configurations ---
            List<JSONObject> combinations = generateConfigurations(req, componentPath);
            if (combinations.isEmpty()) {
                responseJson.put("message", "No configurable fields found for the component, so no page was created.");
                resp.getWriter().write(responseJson.toString(2));
                return;
            }
            if (combinations.size() > MAX_COMBINATIONS_LIMIT) {
                responseJson.put("warning", "Component has " + combinations.size() + " combinations, which exceeds the limit of " + MAX_COMBINATIONS_LIMIT + ". Page creation aborted.");
                resp.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(responseJson.toString(2));
                return;
            }

            // --- 3. Create the New Page by Copying the Template ---
            PageManager pageManager = resolver.adaptTo(PageManager.class);
            String newPagePath = destinationPath + "/" + newPageName;
            if (pageManager.getPage(newPagePath) != null) {
                pageManager.delete(pageManager.getPage(newPagePath), false);
            }
            Page newPage = pageManager.copy(pageManager.getPage(templatePagePath), newPagePath, null, true, true);

            // --- 4. Prepare for Template Node Repetition ---
            String pageContentPath = newPage.getContentResource().getPath();
            Resource pageContentResource = resolver.getResource(pageContentPath);
            Resource containerResource = pageContentResource.getChild("root/container");

            if (containerResource == null) {
                throw new IOException("Could not find 'root/container' in the new page at: " + pageContentPath);
            }

            // Get the original template node to copy from the template page
            Resource originalNodeToRepeat = resolver.getResource(templatePagePath + "/jcr:content/root/container/" + nodeToRepeatName);
            if(originalNodeToRepeat == null) {
                throw new IOException("Could not find the node to repeat '" + nodeToRepeatName + "' in the template page.");
            }

            // Delete the template node from the newly created page
            Resource existingNodeInNewPage = containerResource.getChild(nodeToRepeatName);
            if(existingNodeInNewPage != null) {
                resolver.delete(existingNodeInNewPage);
            }

            // --- 5. Create a Variation for Each Combination ---
            for (int i = 0; i < combinations.size(); i++) {
                JSONObject combination = combinations.get(i);
                String variantContainerName = "variant_container_" + i;
                String destinationCopyPath = containerResource.getPath() + "/" + variantContainerName;

                // FIX: Use JCR Workspace API to prevent "Destination resource does not exist" error
                // This is more reliable for copying into uncommitted destination parent nodes.
                Session session = resolver.adaptTo(Session.class);
                if (session == null) {
                    throw new RepositoryException("Could not adapt ResourceResolver to JCR Session.");
                }
                session.getWorkspace().copy(originalNodeToRepeat.getPath(), destinationCopyPath);

                // Re-fetch the resource after the JCR copy to ensure we have the correct resource object
                Resource newVariantContainer = resolver.getResource(destinationCopyPath);

                if(newVariantContainer == null) continue; // Should not happen

                // Update the title of the container to describe the variant
                ModifiableValueMap properties = newVariantContainer.adaptTo(ModifiableValueMap.class);
                if (properties != null) {
                    properties.put("jcr:title", "Variant " + (i + 1));
                    properties.put("subtitle", getCombinationDescription(combination));
                }

                // Recursively find and replace placeholders within the new container
                findAndReplacePlaceholders(newVariantContainer, componentPath, placeholderResourceType, combination);
            }

            resolver.commit();

            // --- 6. Success Response ---
            responseJson.put("success", true);
            responseJson.put("message", "Successfully created test page with " + combinations.size() + " component variations.");
            responseJson.put("pagePath", newPage.getPath() + ".html");

        } catch (Exception e) {
            resp.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseJson.put("error", "An error occurred during page generation.");
            responseJson.put("exception_type", e.getClass().getName());
            responseJson.put("exception_message", e.getMessage());
            if (resolver.hasChanges()) {
                resolver.revert();
            }
        }

        resp.getWriter().write(responseJson.toString(2));
    }

    /**
     * Recursively traverses a resource tree, finds resources of a specific type,
     * and updates them with a new resource type and properties from a combination.
     */
    private void findAndReplacePlaceholders(Resource startResource, String newResourceType, String placeholderType, JSONObject combination) {
        for (Resource child : startResource.getChildren()) {
            String resourceType = child.getValueMap().get("sling:resourceType", String.class);

            if (placeholderType.equals(resourceType)) {
                ModifiableValueMap properties = child.adaptTo(ModifiableValueMap.class);
                if (properties != null) {
                    // Update the resource type to the new component
                    properties.put("sling:resourceType", newResourceType);
                    // Apply all properties from the combination
                    for (String key : combination.keySet()) {
                        properties.put(key, combination.get(key));
                    }
                }
            }
            // Recurse into children
            if (child.hasChildren()) {
                findAndReplacePlaceholders(child, newResourceType, placeholderType, combination);
            }
        }
    }

    /**
     * Generates all configuration combinations for a component.
     */
    private List<JSONObject> generateConfigurations(SlingHttpServletRequest req, String componentPath) throws IOException {
        String dialogJsonUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort()
                + "/mnt/override/" + componentPath.replaceFirst("^/", "") + "/_cq_dialog.-1.json";

        URL url = new URL(dialogJsonUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        String username = "admin";
        String password = "admin";
        String encodedAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            InputStream is = conn.getInputStream();
            JSONObject dialogJson = new JSONObject(new JSONTokener(is));
            List<JSONObject> allFields = extractFields(dialogJson);
            return generateCombinations(allFields);
        } else {
            throw new IOException("Failed to fetch dialog JSON. Status: " + conn.getResponseCode());
        }
    }

    private List<JSONObject> extractFields(JSONObject dialogJson) {
        List<JSONObject> fields = new ArrayList<>();
        JSONObject tabs = dialogJson.optJSONObject("content").optJSONObject("items").optJSONObject("tabs").optJSONObject("items");
        if (tabs != null) {
            for (String tabKey : tabs.keySet()) {
                if (tabs.get(tabKey) instanceof JSONObject) findFieldsInNode(tabs.getJSONObject(tabKey), fields);
            }
        } else {
            findFieldsInNode(dialogJson, fields);
        }
        return fields;
    }

    private void findFieldsInNode(JSONObject node, List<JSONObject> fieldList) {
        if (node.has("name") && node.has("sling:resourceType")) {
            JSONObject fieldDetails = new JSONObject();
            String name = node.optString("name", "").replace("./", "");
            if (!name.isEmpty()) {
                fieldDetails.put("name", name);
                addExampleValues(node.optString("sling:resourceType"), node, fieldDetails);
                if (fieldDetails.has("values")) fieldList.add(fieldDetails);
            }
        }
        for (String key : node.keySet()) {
            Object value = node.get(key);
            if (value instanceof JSONObject) findFieldsInNode((JSONObject) value, fieldList);
            else if (value instanceof JSONArray) {
                for (Object item : (JSONArray) value) {
                    if (item instanceof JSONObject) findFieldsInNode((JSONObject) item, fieldList);
                }
            }
        }
    }

    private void addExampleValues(String type, JSONObject node, JSONObject fieldDetails) {
        JSONArray values = new JSONArray();
        if (type.contains("select")) {
            JSONObject items = node.optJSONObject("items");
            if (items != null) {
                for (String key : items.keySet()) {
                    JSONObject opt = items.optJSONObject(key);
                    if (opt != null) {
                        String val = opt.optString("value");
                        if (!val.isEmpty()) values.put(val);
                    }
                }
            }
        } else if (type.contains("checkbox")) {
            values.put(true);
            values.put(false);
        }
        if (!values.isEmpty()) fieldDetails.put("values", values);
    }

    private List<JSONObject> generateCombinations(List<JSONObject> fields) {
        List<JSONObject> combinations = new ArrayList<>();
        if (fields != null && !fields.isEmpty()) {
            generateCombinationsRecursive(fields, 0, new JSONObject(), combinations);
        }
        return combinations;
    }

    private void generateCombinationsRecursive(List<JSONObject> fields, int index, JSONObject current, List<JSONObject> all) {
        if (all.size() >= MAX_COMBINATIONS_LIMIT) return;
        if (index == fields.size()) {
            all.add(new JSONObject(current.toString()));
            return;
        }
        JSONObject field = fields.get(index);
        String name = field.getString("name");
        JSONArray values = field.getJSONArray("values");
        for (int i = 0; i < values.length(); i++) {
            current.put(name, values.get(i));
            generateCombinationsRecursive(fields, index + 1, current, all);
        }
    }

    private String getCombinationDescription(JSONObject combination) {
        StringBuilder sb = new StringBuilder();
        for (String key : combination.keySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(key).append(" = ").append(combination.get(key).toString());
        }
        return sb.toString();
    }

    private boolean isAnyBlank(String... strings) {
        for (String s : strings) {
            if (s == null || s.trim().isEmpty()) return true;
        }
        return false;
    }
}