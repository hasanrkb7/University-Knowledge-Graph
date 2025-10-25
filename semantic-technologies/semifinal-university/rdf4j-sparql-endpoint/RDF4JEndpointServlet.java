package com.university.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.model.Statement;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class RDF4JEndpointServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        String serverURL = request.getParameter("serverURL");
        String repositoryID = request.getParameter("repositoryID");
        String sparqlQuery = request.getParameter("sparqlQuery");
        
        Repository repository = null;
        RepositoryConnection connection = null;
        
        try {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>SPARQL Query Results</title>");
            out.println("<link rel='stylesheet' type='text/css' href='styles.css'>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class='container'>");
            out.println("<h1>SPARQL Query Results</h1>");
            
            // DEBUG: Show what we received
            out.println("<div style='background:#f0f0f0; padding:10px; margin:10px 0; border:1px solid #ccc;'>");
            out.println("<h3>DEBUG INFO:</h3>");
            out.println("<p><strong>Query Length:</strong> " + (sparqlQuery != null ? sparqlQuery.length() : "null") + "</p>");
            out.println("<p><strong>Query (first 100 chars):</strong> " + (sparqlQuery != null ? escapeHtml(sparqlQuery.substring(0, Math.min(100, sparqlQuery.length()))) : "null") + "</p>");
            
            if (sparqlQuery != null) {
                String normalized = sparqlQuery.replaceAll("#[^\n]*", "").replaceAll("\\s+", " ").trim().toLowerCase();
                out.println("<p><strong>Normalized Query:</strong> " + escapeHtml(normalized.substring(0, Math.min(100, normalized.length()))) + "</p>");
                out.println("<p><strong>Contains 'select':</strong> " + normalized.contains("select") + "</p>");
                out.println("<p><strong>Contains 'construct':</strong> " + normalized.contains("construct") + "</p>");
                out.println("<p><strong>Contains 'ask':</strong> " + normalized.contains("ask") + "</p>");
            }
            out.println("</div>");
            
            if (sparqlQuery == null || sparqlQuery.trim().isEmpty()) {
                out.println("<div class='error'>");
                out.println("<p>No query provided!</p>");
                out.println("</div>");
            } else {
                repository = new HTTPRepository(serverURL, repositoryID);
                repository.initialize();
                
                connection = repository.getConnection();
                
                // Remove comments and normalize whitespace for query type detection
                String normalizedQuery = sparqlQuery.replaceAll("#[^\n]*", "").replaceAll("\\s+", " ").trim().toLowerCase();
                
                if (normalizedQuery.contains("select")) {
                    executeSELECTQuery(connection, sparqlQuery, out);
                } else if (normalizedQuery.contains("construct") || normalizedQuery.contains("describe")) {
                    executeGRAPHQuery(connection, sparqlQuery, out);
                } else if (normalizedQuery.contains("ask")) {
                    executeASKQuery(connection, sparqlQuery, out);
                } else {
                    out.println("<div class='error'>");
                    out.println("<p>Unknown query type. Please use SELECT, CONSTRUCT, DESCRIBE, or ASK.</p>");
                    out.println("</div>");
                }
            }
            
            out.println("<div class='back-button'>");
            out.println("<a href='index.html'>← Back to Query Form</a>");
            out.println("</div>");
            
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
            
        } catch (Exception e) {
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>Error</title>");
            out.println("<link rel='stylesheet' type='text/css' href='styles.css'>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class='container'>");
            out.println("<div class='error'>");
            out.println("<h2>Error</h2>");
            out.println("<p>" + escapeHtml(e.getMessage()) + "</p>");
            out.println("</div>");
            out.println("<div class='back-button'>");
            out.println("<a href='index.html'>← Back to Query Form</a>");
            out.println("</div>");
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (repository != null) {
                try {
                    repository.shutDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (out != null) {
                out.close();
            }
        }
    }
    
    private void executeSELECTQuery(RepositoryConnection connection, String sparqlQuery, PrintWriter out) {
        TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery);
        TupleQueryResult result = null;
        
        try {
            result = query.evaluate();
            List<String> bindingNames = result.getBindingNames();
            
            out.println("<div class='results'>");
            out.println("<h2>SELECT Query Results</h2>");
            out.println("<table>");
            out.println("<thead><tr>");
            
            for (String bindingName : bindingNames) {
                out.println("<th>" + escapeHtml(bindingName) + "</th>");
            }
            out.println("</tr></thead>");
            out.println("<tbody>");
            
            int count = 0;
            while (result.hasNext()) {
                out.println("<tr>");
                BindingSet bindingSet = result.next();
                for (String bindingName : bindingNames) {
                    Value value = bindingSet.getValue(bindingName);
                    out.println("<td>" + (value != null ? escapeHtml(value.stringValue()) : "") + "</td>");
                }
                out.println("</tr>");
                count++;
            }
            out.println("</tbody>");
            out.println("</table>");
            out.println("<p class='count'>Total Results: " + count + "</p>");
            out.println("</div>");
        } finally {
            if (result != null) {
                result.close();
            }
        }
    }
    
    private void executeGRAPHQuery(RepositoryConnection connection, String sparqlQuery, PrintWriter out) {
        GraphQuery query = connection.prepareGraphQuery(QueryLanguage.SPARQL, sparqlQuery);
        GraphQueryResult result = null;
        
        try {
            result = query.evaluate();
            out.println("<div class='results'>");
            out.println("<h2>CONSTRUCT/DESCRIBE Query Results</h2>");
            out.println("<table>");
            out.println("<thead><tr>");
            out.println("<th>Subject</th><th>Predicate</th><th>Object</th>");
            out.println("</tr></thead>");
            out.println("<tbody>");
            
            int count = 0;
            while (result.hasNext()) {
                Statement statement = result.next();
                out.println("<tr>");
                out.println("<td>" + escapeHtml(statement.getSubject().stringValue()) + "</td>");
                out.println("<td>" + escapeHtml(statement.getPredicate().stringValue()) + "</td>");
                out.println("<td>" + escapeHtml(statement.getObject().stringValue()) + "</td>");
                out.println("</tr>");
                count++;
            }
            out.println("</tbody>");
            out.println("</table>");
            out.println("<p class='count'>Total Triples: " + count + "</p>");
            out.println("</div>");
        } finally {
            if (result != null) {
                result.close();
            }
        }
    }
    
    private void executeASKQuery(RepositoryConnection connection, String sparqlQuery, PrintWriter out) {
        BooleanQuery query = connection.prepareBooleanQuery(QueryLanguage.SPARQL, sparqlQuery);
        boolean result = query.evaluate();
        
        out.println("<div class='results'>");
        out.println("<h2>ASK Query Result</h2>");
        out.println("<div class='ask-result " + (result ? "true" : "false") + "'>");
        out.println("<p><strong>Result: " + result + "</strong></p>");
        out.println("</div>");
        out.println("</div>");
    }
    
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
}