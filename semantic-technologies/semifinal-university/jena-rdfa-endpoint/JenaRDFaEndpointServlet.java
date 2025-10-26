package com.university.servlet;

import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;
import org.apache.jena.reasoner.rulesys.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@WebServlet(name = "JenaRDFaEndpointServlet", urlPatterns = {"/JenaRDFaEndpointServlet"})
public class JenaRDFaEndpointServlet extends HttpServlet {

    // SSL bypass for HTTPS connections
    static {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            System.setProperty("jsse.enableSNIExtension", "false");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            // Get form parameters
            String rdfaUrl = request.getParameter("rdfaUrl");
            String n3Rules = request.getParameter("n3Rules");
            String sparqlQuery = request.getParameter("sparqlQuery");

            // Validate inputs
            if (rdfaUrl == null || rdfaUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("RDFa document URL is required");
            }
            if (sparqlQuery == null || sparqlQuery.trim().isEmpty()) {
                throw new IllegalArgumentException("SPARQL query is required");
            }

            // Log request details
            System.out.println("=== Processing Request ===");
            System.out.println("URL: " + rdfaUrl);
            System.out.println("Rules provided: " + (n3Rules != null && !n3Rules.trim().isEmpty()));
            System.out.println("Query length: " + sparqlQuery.length());

            // Create base model
            Model baseModel = ModelFactory.createDefaultModel();

            // Load RDF data based on URL type
            if (rdfaUrl.toLowerCase().startsWith("file:")) {
                // LOCAL FILE LOADING
                loadLocalFileSmartly(baseModel, rdfaUrl);
            } else if (rdfaUrl.toLowerCase().startsWith("http://")) {
                // HTTP URL
                loadHttpUrl(baseModel, rdfaUrl);
            } else if (rdfaUrl.toLowerCase().startsWith("https://")) {
                // HTTPS URL
                loadHttpsUrl(baseModel, rdfaUrl);
            } else {
                throw new IllegalArgumentException("URL must start with http://, https://, or file://");
            }

            System.out.println("Base model loaded: " + baseModel.size() + " triples");

            // Apply reasoning if N3 rules provided
            Model inferredModel;
            if (n3Rules != null && !n3Rules.trim().isEmpty()) {
                System.out.println("Applying N3 rules...");
                String rules = n3Rules.trim();
                Reasoner reasoner = new GenericRuleReasoner(Rule.parseRules(rules));
                InfModel infModel = ModelFactory.createInfModel(reasoner, baseModel);
                inferredModel = infModel;
                System.out.println("Inferred model size: " + inferredModel.size() + " triples");
            } else {
                inferredModel = baseModel;
                System.out.println("No reasoning applied");
            }

            // Execute SPARQL query
            System.out.println("Executing SPARQL query...");
            Query query = QueryFactory.create(sparqlQuery);
            QueryExecution qexec = QueryExecutionFactory.create(query, inferredModel);
            ResultSet results = qexec.execSelect();

            // Generate HTML output
            generateHtmlOutput(out, rdfaUrl, inferredModel.size(), results, sparqlQuery, n3Rules);

            qexec.close();

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            generateErrorPage(out, e);
        } finally {
            out.close();
        }
    }

    // Load local file with smart format detection
    private void loadLocalFileSmartly(Model model, String fileUrl) throws Exception {
        System.out.println("Loading LOCAL file: " + fileUrl);
        
        // Convert file:/// URL to proper path
        String filePath = fileUrl;
        
        if (filePath.startsWith("file:///")) {
            filePath = filePath.substring(8);
        } else if (filePath.startsWith("file://")) {
            filePath = filePath.substring(7);
        }
        
        filePath = filePath.replace("/", "\\");
        
        if (filePath.length() > 1 && filePath.charAt(1) != ':') {
            filePath = filePath.charAt(0) + ":" + filePath.substring(1);
        }
        
        System.out.println("Resolved file path: " + filePath);
        
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        
        System.out.println("File exists: " + file.getAbsolutePath());
        System.out.println("File size: " + file.length() + " bytes");
        
        // Check file content to detect actual format
        String detectedFormat = detectFileFormat(file);
        System.out.println("Detected format: " + detectedFormat);
        
        // Try formats in order based on detection
        Lang[] formatsToTry;
        if ("RDF/XML".equals(detectedFormat)) {
            formatsToTry = new Lang[]{Lang.RDFXML, Lang.TURTLE, Lang.N3};
        } else if ("TURTLE".equals(detectedFormat)) {
            formatsToTry = new Lang[]{Lang.TURTLE, Lang.N3, Lang.RDFXML};
        } else {
            // Try all
            formatsToTry = new Lang[]{Lang.RDFXML, Lang.TURTLE, Lang.N3, Lang.JSONLD};
        }
        
        boolean loaded = false;
        Exception lastException = null;
        
        for (Lang lang : formatsToTry) {
            try {
                System.out.println("Trying format: " + lang.getName());
                try (InputStream in = new FileInputStream(file)) {
                    RDFDataMgr.read(model, in, lang);
                }
                System.out.println("SUCCESS with " + lang.getName() + " - loaded " + model.size() + " triples");
                loaded = true;
                break;
            } catch (Exception e) {
                System.out.println("Failed with " + lang.getName() + ": " + e.getMessage());
                lastException = e;
                model.removeAll();
            }
        }
        
        if (!loaded) {
            throw new Exception("Could not parse file. Last error: " + 
                              (lastException != null ? lastException.getMessage() : "Unknown"));
        }
    }

    // Simple file format detection by reading content
    private String detectFileFormat(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sample = new StringBuilder();
            String line;
            int linesRead = 0;
            
            while ((line = reader.readLine()) != null && linesRead < 15) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    sample.append(trimmed.toLowerCase()).append(" ");
                    linesRead++;
                }
            }
            
            String content = sample.toString();
            
            // Check for XML/RDF
            if (content.contains("<?xml") || content.contains("<rdf:rdf") || 
                content.contains("xmlns:rdf") || content.contains("<rdf:") ||
                content.contains("<owl:")) {
                System.out.println("→ File contains XML/RDF markers");
                return "RDF/XML";
            }
            
            // Check for Turtle
            if (content.contains("@prefix") || content.contains("@base")) {
                System.out.println("→ File contains Turtle markers");
                return "TURTLE";
            }
            
            System.out.println("→ Format unclear, will try all");
            return "UNKNOWN";
            
        } catch (IOException e) {
            System.out.println("→ Could not read file for detection: " + e.getMessage());
            return "UNKNOWN";
        }
    }

    // Load HTTP URL
    private void loadHttpUrl(Model model, String urlString) throws Exception {
        System.out.println("Loading HTTP URL: " + urlString);
        Lang guessedLang = guessFormatFromUrl(urlString);
        System.out.println("Guessed format from URL: " + guessedLang.getName());
        
        try {
            RDFDataMgr.read(model, urlString, guessedLang);
        } catch (Exception e) {
            System.err.println("RDFDataMgr failed, trying direct connection: " + e.getMessage());
            loadWithHttpClient(model, urlString, guessedLang);
        }
    }

    // Load HTTPS URL
    private void loadHttpsUrl(Model model, String urlString) throws Exception {
        System.out.println("Loading HTTPS URL: " + urlString);
        Lang guessedLang = guessFormatFromUrl(urlString);
        System.out.println("Guessed format from URL: " + guessedLang.getName());
        
        try {
            loadWithHttpClient(model, urlString, guessedLang);
        } catch (Exception e) {
            System.err.println("HTTP client failed, trying RDFDataMgr: " + e.getMessage());
            RDFDataMgr.read(model, urlString, guessedLang);
        }
    }

    // Load using HttpURLConnection
    private void loadWithHttpClient(Model model, String urlString, Lang lang) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestProperty("User-Agent", "Apache-Jena/4.10.0");
        conn.setRequestProperty("Accept", "text/turtle, application/rdf+xml, application/ld+json, text/n3");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        int responseCode = conn.getResponseCode();
        System.out.println("HTTP Response Code: " + responseCode);
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream in = conn.getInputStream()) {
                RDFDataMgr.read(model, in, lang);
            }
        } else {
            throw new IOException("HTTP error code: " + responseCode);
        }
    }

    // Guess format from URL extension
    private Lang guessFormatFromUrl(String url) {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.endsWith(".ttl")) return Lang.TURTLE;
        if (lowerUrl.endsWith(".n3")) return Lang.N3;
        if (lowerUrl.endsWith(".rdf") || lowerUrl.endsWith(".owl") || lowerUrl.endsWith(".xml")) return Lang.RDFXML;
        if (lowerUrl.endsWith(".jsonld") || lowerUrl.endsWith(".json")) return Lang.JSONLD;
        if (lowerUrl.endsWith(".nt")) return Lang.NTRIPLES;
        return Lang.TURTLE;
    }

    // Generate HTML output
    private void generateHtmlOutput(PrintWriter out, String sourceUrl, long tripleCount,
                                   ResultSet results, String query, String rules) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'>");
        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        out.println("<title>SPARQL Query Results - JenaQR</title>");
        out.println("<link rel='stylesheet' href='styles.css'>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class='container'>");
        out.println("<h1>🎯 Query Results</h1>");
        
        out.println("<div class='info-box'>");
        out.println("<h3>📊 Data Source Information</h3>");
        out.println("<p><strong>Source URL:</strong> <code>" + escapeHtml(sourceUrl) + "</code></p>");
        out.println("<p><strong>Triples Loaded:</strong> " + tripleCount + "</p>");
        if (rules != null && !rules.trim().isEmpty()) {
            out.println("<p><strong>Reasoning:</strong> ✓ N3 Rules Applied</p>");
        } else {
            out.println("<p><strong>Reasoning:</strong> ✗ No Rules Applied</p>");
        }
        out.println("</div>");

        out.println("<div class='query-box'>");
        out.println("<h3>🔍 SPARQL Query</h3>");
        out.println("<pre><code>" + escapeHtml(query) + "</code></pre>");
        out.println("</div>");

        out.println("<div class='results-section'>");
        out.println("<h3>📋 Query Results</h3>");
        
        if (!results.hasNext()) {
            out.println("<p class='no-results'>No results found for this query.</p>");
        } else {
            out.println("<div class='table-container'>");
            out.println("<table>");
            
            java.util.List<String> vars = results.getResultVars();
            out.println("<thead><tr>");
            for (String var : vars) {
                out.println("<th>" + escapeHtml(var) + "</th>");
            }
            out.println("</tr></thead>");
            
            out.println("<tbody>");
            int rowCount = 0;
            while (results.hasNext()) {
                QuerySolution solution = results.next();
                out.println("<tr>");
                for (String var : vars) {
                    RDFNode node = solution.get(var);
                    String value = (node != null) ? node.toString() : "";
                    out.println("<td>" + escapeHtml(value) + "</td>");
                }
                out.println("</tr>");
                rowCount++;
            }
            out.println("</tbody>");
            out.println("</table>");
            out.println("</div>");
            out.println("<p class='result-count'>Total results: <strong>" + rowCount + "</strong></p>");
        }
        out.println("</div>");

        out.println("<div class='back-button'>");
        out.println("<a href='index.html' class='btn'>← New Query</a>");
        out.println("</div>");
        
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }

    // Generate error page
    private void generateErrorPage(PrintWriter out, Exception e) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang='en'>");
        out.println("<head>");
        out.println("<meta charset='UTF-8'>");
        out.println("<title>Error - JenaQR</title>");
        out.println("<link rel='stylesheet' href='styles.css'>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div class='container'>");
        out.println("<h1>❌ Error</h1>");
        out.println("<div class='error'>");
        out.println("<p><strong>An error occurred:</strong></p>");
        out.println("<p>" + escapeHtml(e.getMessage()) + "</p>");
        
        out.println("<details>");
        out.println("<summary>Stack Trace (for debugging)</summary>");
        out.println("<pre>");
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        out.println(escapeHtml(sw.toString()));
        out.println("</pre>");
        out.println("</details>");
        
        out.println("</div>");
        out.println("<div class='back-button'>");
        out.println("<a href='index.html' class='btn'>← Try Again</a>");
        out.println("</div>");
        out.println("</div>");
        out.println("</body>");
        out.println("</html>");
    }

    // Escape HTML
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}