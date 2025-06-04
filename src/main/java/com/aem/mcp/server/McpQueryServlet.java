package com.aem.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.servlet.Servlet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component(service = Servlet.class,
        property = {
                "sling.servlet.methods=POST",
                "sling.servlet.paths=/bin/mcp/query"
        })
public class McpQueryServlet extends SlingAllMethodsServlet {

    @Reference
    private Repository repository;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json");

        String queryString = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8).trim();
        if (!queryString.toLowerCase().startsWith("select")) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"Only JCR-SQL2 SELECT queries are allowed.\"}");
            return;
        }

        try  {Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            QueryManager qm = session.getWorkspace().getQueryManager();
            Query query = qm.createQuery(queryString, Query.JCR_SQL2);
            QueryResult result = query.execute();

            NodeIterator nodes = result.getNodes();
            List<String> paths = new ArrayList<>();
            while (nodes.hasNext()) {
                paths.add(nodes.nextNode().getPath());
            }

            response.getWriter().write(new ObjectMapper().writeValueAsString(paths));

        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
