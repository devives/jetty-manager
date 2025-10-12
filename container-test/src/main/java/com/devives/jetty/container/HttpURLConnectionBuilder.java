package com.devives.jetty.container;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpURLConnectionBuilder {

    // See javax.ws.rs.core.HttpHeaders

    // Body type in request
    public static final String CONTENT_TYPE = "Content-Type: application/json";

    // Accepted response by client
    public static final String ACCEPT = "Accept";

    // See javax.ws.rs.core.MediaType

    // Media Type 'application/json'
    public final static String APPLICATION_JSON = "application/json";

    private URL url;
    private String method;
    private Map<String, String> headers = new HashMap<String, String>();
    private String body;

    public HttpURLConnectionBuilder() {
    }

    // ----------------------- Logic Methods -----------------------

    public HttpURLConnectionBuilder get(URL url) {
        return method(url, "GET");
    }

    public HttpURLConnectionBuilder post(URL url) {
        return method(url, "POST");
    }

    public HttpURLConnectionBuilder put(URL url) {
        return method(url, "PUT");
    }

    public HttpURLConnectionBuilder delete(URL url) {
        return method(url, "DELETE");
    }

    public HttpURLConnectionBuilder method(URL url, String method) {
        this.url = url;
        this.method = method;
        return this;
    }

    public HttpURLConnectionBuilder header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public HttpURLConnectionBuilder body(String body) {
        this.body = body;
        return this;
    }

    public HttpURLConnectionResponse send() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) this.url.openConnection();

        // Set Request Method
        conn.setRequestMethod(this.method);

        // Set Request Headers
        for (String name : this.headers.keySet()) {
            conn.setRequestProperty(name, this.headers.get(name));
        }

        // Trace Log Request
        System.out.println(conn.getRequestMethod() + " " + conn.getURL());
        for (String key : conn.getRequestProperties().keySet()) {
            System.out.println(
                    "  >> " + key + ": " + conn.getRequestProperty(key));
        }

        // Set Request Body
        if (this.body != null) {
            conn.setDoOutput(true);
            try (OutputStreamWriter output = new OutputStreamWriter(
                    new BufferedOutputStream(conn.getOutputStream()),
                    StandardCharsets.UTF_8)) {

                output.write(this.body);
            }
        }

        // Send Request and Get Response Code
        int responseCode = conn.getResponseCode();

        // Get Response Body
        InputStream responseStream = null;
        if (responseCode >= 200 && responseCode <= 299) {

            responseStream = conn.getInputStream();
        } else {

            responseStream = conn.getErrorStream();
        }
        String responseBody = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                responseStream, StandardCharsets.UTF_8))) {

            StringBuilder builder = new StringBuilder();
            for (String line = null; (line = in.readLine()) != null;) {
                builder.append(line);
            }
            responseBody = builder.toString();
        }

        // Get Response Headers
        Map<String, List<String>> responseHeaders = conn.getHeaderFields();

        // Trace Log Response
        System.out.println("<< " + conn.getHeaderField(null));
        for (String key : conn.getHeaderFields().keySet()) {
            if (key != null) {
                System.out.println(
                        "  << " + key + ": " + conn.getHeaderField(key));
            }
        }

        return new HttpURLConnectionResponse(responseCode, responseBody,
                responseHeaders);
    }

    // ----------------------- Helper Methods -----------------------

    // ----------------------- Get and Set Methods -----------------------

}