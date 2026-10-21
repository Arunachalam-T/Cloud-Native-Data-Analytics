package com.dataanalytics.ui.service;

import com.dataanalytics.ui.MainApp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

/**
 * HTTP client for communicating with the Spring Boot backend REST API.
 */
public class ApiClient {

    private static final ApiClient INSTANCE = new ApiClient();
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authHeader;

    private ApiClient() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Basic auth: admin:<API_KEY>
        String credentials = "admin:" + MainApp.API_KEY;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    public static ApiClient getInstance() {
        return INSTANCE;
    }

    public <T> T get(String path, Class<T> responseType) throws IOException {
        HttpGet request = new HttpGet(MainApp.BASE_URL + path);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");

        try (var response = httpClient.execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() >= 400) {
                throw new IOException("API error " + response.getStatusLine().getStatusCode() + ": " + body);
            }
            return objectMapper.readValue(body, responseType);
        }
    }

    public String getRaw(String path) throws IOException {
        HttpGet request = new HttpGet(MainApp.BASE_URL + path);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        try (var response = httpClient.execute(request)) {
            return EntityUtils.toString(response.getEntity());
        }
    }

    public String uploadFile(File file, String name, String description) throws IOException {
        HttpPost request = new HttpPost(MainApp.BASE_URL + "/api/v1/datasets/upload");
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);

        var entity = MultipartEntityBuilder.create()
            .addBinaryBody("file", file, ContentType.APPLICATION_OCTET_STREAM, file.getName())
            .addTextBody("name", name != null ? name : file.getName())
            .addTextBody("description", description != null ? description : "")
            .build();
        request.setEntity(entity);

        try (var response = httpClient.execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() >= 400) {
                throw new IOException("Upload failed: " + body);
            }
            return body;
        }
    }

    public void delete(String path) throws IOException {
        HttpDelete request = new HttpDelete(MainApp.BASE_URL + path);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        try (var response = httpClient.execute(request)) {
            if (response.getStatusLine().getStatusCode() >= 400) {
                throw new IOException("Delete failed: " + response.getStatusLine().getStatusCode());
            }
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
