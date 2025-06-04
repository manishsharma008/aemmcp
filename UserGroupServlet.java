package com.mcp.server.api;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/findGroups",
                "sling.servlet.methods=GET"
        }
)
public class UserGroupServlet extends SlingSafeMethodsServlet {

    private static final Logger logger = LoggerFactory.getLogger(UserGroupServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        ResourceResolver resolver = request.getResourceResolver();
        String userId = request.getParameter("userId");
        logger.info("Searching for groups of user ID: {}", userId);

        if (userId == null || userId.isEmpty()) {
            response.getWriter().write("Please provide a userId parameter.");
            return;
        }

        try {
            // Step 1: Get QueryBuilder and JCR Session
            QueryBuilder queryBuilder = resolver.adaptTo(QueryBuilder.class);
            Session session = resolver.adaptTo(Session.class);

            if (queryBuilder == null || session == null) {
                response.getWriter().write("QueryBuilder or Session unavailable.");
                return;
            }

            // Step 2: Find the user's UUID
            Map<String, String> userQueryMap = new HashMap<>();
            userQueryMap.put("type", "rep:User");
            userQueryMap.put("property", "rep:principalName");
            userQueryMap.put("property.value", userId);

            Query userQuery = queryBuilder.createQuery(PredicateGroup.create(userQueryMap), session);
            SearchResult userResult = userQuery.getResult();

            String memberUUID = null;
            Iterator<Resource> userResources = userResult.getResources();
            if (userResources.hasNext()) {
                Resource userResource = userResources.next();
                memberUUID = userResource.getValueMap().get("jcr:uuid", String.class);
            }

            if (memberUUID == null) {
                response.getWriter().write("No user found with userId: " + userId);
                return;
            }

            // Step 3: Find groups containing the UUID
            Map<String, String> groupQueryMap = new HashMap<>();
            groupQueryMap.put("type", "rep:Group");
            groupQueryMap.put("property", "rep:members");
            groupQueryMap.put("property.value", memberUUID);

            Query groupQuery = queryBuilder.createQuery(PredicateGroup.create(groupQueryMap), session);
            SearchResult groupResult = groupQuery.getResult();

            // Step 4: Collect group names
            StringBuilder groupNames = new StringBuilder();
            Iterator<Resource> groupResources = groupResult.getResources();
            while (groupResources.hasNext()) {
                Resource groupResource = groupResources.next();
                ValueMap properties = groupResource.getValueMap();
                String groupName = properties.get("rep:principalName", String.class);
                if (groupName != null) {
                    groupNames.append(groupName).append("\n");
                }
            }

            response.getWriter().write(groupNames.toString());

        } catch (Exception e) {
            logger.error("Error executing query", e);
            response.getWriter().write("Error: " + e.getMessage());
        }
    }
}
