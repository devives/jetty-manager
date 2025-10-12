package com.devives.jetty.container;


import java.util.List;
import java.util.Map;

public class HttpURLConnectionResponse {

    private final int statusCode;
    private final String body;
    private final Map<String, List<String>> headers;

    public HttpURLConnectionResponse(int statusCode, String body,
                                     Map<String, List<String>> headers) {

        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
    }

    // ----------------------- Logic Methods -----------------------

    // ----------------------- Helper Methods -----------------------

    // ----------------------- Get and Set Methods -----------------------

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

}