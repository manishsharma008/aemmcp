<%@ page import="org.json.*, java.net.*, java.io.*, java.util.*, java.util.Base64" %>
<%@ page contentType="application/json;charset=UTF-8" language="java" %>

<%!
    /**
     * Recursively finds fields within a JSON node and populates a list of maps.
     * Each map represents a field with its properties and values.
     *
     * @param node The current JSON node (JSONObject or JSONArray) to process.
     * @param fieldList The list to which field maps will be added.
     */
    public static void findFieldsInNode(Object node, List<Map<String, Object>> fieldList) {
        if (node instanceof JSONObject) {
            JSONObject json = (JSONObject) node;
            String fieldLabel = json.optString("fieldLabel");

            // A field is identified by the presence of a 'fieldLabel'.
            if (fieldLabel != null && !fieldLabel.isEmpty()) {
                Map<String, Object> fieldMap = new LinkedHashMap<>(); // Use LinkedHashMap to preserve key order.
                String name = json.optString("name");
                String type = json.optString("sling:resourceType");

                if (name.startsWith("./")) {
                    name = name.substring(2);
                }

                fieldMap.put("name", name);
                fieldMap.put("type", type);
                
                List<Object> values = new ArrayList<>();
                boolean valuesFound = false;

                if (type != null) {
                    if (type.contains("select")) {
                        JSONObject items = json.optJSONObject("items");
                        if (items != null) {
                            for (Iterator<String> keys = items.keys(); keys.hasNext(); ) {
                                String key = keys.next();
                                if (items.opt(key) instanceof JSONObject) {
                                    JSONObject opt = items.optJSONObject(key);
                                    if (opt.has("value")) {
                                        String val = opt.optString("value");
                                        if (val != null && !val.isEmpty()) {
                                            values.add(val);
                                        }
                                    }
                                }
                            }
                            valuesFound = !values.isEmpty();
                        }
                    } else if (type.contains("pathfield")) {
                        values.add("/content/dam/text.png");
                        valuesFound = true;
                    } else if (type.contains("checkbox")) {
                        values.add("true");
                        values.add("false");
                        valuesFound = true;
                    } else if (type.equals("granite/ui/components/coral/foundation/form/textfield") || type.equals("cq/gui/components/authoring/dialog/richtext")) {
                        values.add("laura ipsum");
                        valuesFound = true;
                    } else if (type.equals("granite/ui/components/coral/foundation/form/numberfield")) {
                        values.add(1234567);
                        valuesFound = true;
                    }
                }

                if (valuesFound) {
                    fieldMap.put("values", values);
                }

                fieldList.add(fieldMap);
            }

            // Recurse through all child nodes.
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                try {
                    findFieldsInNode(json.get(key), fieldList);
                } catch (Exception ignored) {}
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                findFieldsInNode(array.get(i), fieldList);
            }
        }
    }
%>
<%
    Map<String, Object> finalJsonMap = new LinkedHashMap<>();
    String componentPath = request.getParameter("componentPath");

    if (componentPath == null || componentPath.isEmpty()) {
        response.setStatus(400); // Bad Request
        finalJsonMap.put("error", "Please provide a valid ?componentPath=/apps/...");
    } else {
        if (componentPath.startsWith("/")) {
            componentPath = componentPath.substring(1);
        }
        String dialogJsonUrl = "http://localhost:4502/mnt/override/" + componentPath + "/_cq_dialog.-1.json";

        try {
            URL url = new URL(dialogJsonUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String username = "admin";
            String password = "admin";
            String encodedAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8"));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                JSONObject dialogJson = new JSONObject(new JSONTokener(is));

                try {
                    // Standard path for dialogs with tabs.
                    JSONObject tabs = dialogJson.getJSONObject("content").getJSONObject("items").getJSONObject("tabs").getJSONObject("items");
                    for (Iterator<String> tabKeys = tabs.keys(); tabKeys.hasNext(); ) {
                        String tabNodeName = tabKeys.next();
                        Object value = tabs.get(tabNodeName);
                        if (value instanceof JSONObject) {
                            List<Map<String, Object>> fieldsInTab = new ArrayList<>();
                            findFieldsInNode((JSONObject) value, fieldsInTab);
                            if (!fieldsInTab.isEmpty()) {
                                finalJsonMap.put(tabNodeName, fieldsInTab);
                            }
                        }
                    }
                } catch (JSONException e) {
                    // Fallback for dialogs without tabs.
                    List<Map<String, Object>> fieldsList = new ArrayList<>();
                    JSONObject contentItems = dialogJson.optJSONObject("content");
                    if(contentItems != null) {
                        findFieldsInNode(contentItems, fieldsList);
                    }
                    finalJsonMap.put("fields", fieldsList);
                }
            } else {
                response.setStatus(conn.getResponseCode());
                finalJsonMap.put("error", "Failed to fetch dialog JSON from AEM.");
                finalJsonMap.put("status", conn.getResponseCode());
                finalJsonMap.put("url", dialogJsonUrl);
            }
        } catch (Exception e) {
            response.setStatus(500); // Internal Server Error
            finalJsonMap.put("error", "An exception occurred while processing the request.");
            finalJsonMap.put("exception_type", e.getClass().getName());
            finalJsonMap.put("exception_message", e.getMessage());
        }
    }

    // Convert the final map to a JSONObject and print it.
    out.print(new JSONObject(finalJsonMap).toString(2));
    out.flush();
%>
