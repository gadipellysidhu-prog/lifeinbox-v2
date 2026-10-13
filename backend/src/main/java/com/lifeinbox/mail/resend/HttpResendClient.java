package com.lifeinbox.mail.resend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpResendClient implements ResendClient {

    private static final Logger log = LoggerFactory.getLogger(HttpResendClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpResendClient(ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                objectMapper
        );
    }

    public HttpResendClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResendEmailResponse sendEmail(String apiUrl, String apiKey, ResendEmailRequest request) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "LifeInbox-Backend/2.0")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        log.debug("Sending email request to Resend API at {}", apiUrl);
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode >= 200 && statusCode < 300) {
            ResendEmailResponse emailResponse = objectMapper.readValue(responseBody, ResendEmailResponse.class);
            log.info("Resend API accepted email: id={}", emailResponse.id());
            return emailResponse;
        }

        String errorMessage = parseErrorMessage(responseBody, statusCode);
        log.warn("Resend API rejected email: HTTP {} - {}", statusCode, errorMessage);
        throw new ResendApiException("Resend API error (HTTP " + statusCode + "): " + errorMessage, statusCode);
    }

    private String parseErrorMessage(String responseBody, int statusCode) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Empty response from Resend (HTTP " + statusCode + ")";
        }
        try {
            ResendErrorResponse error = objectMapper.readValue(responseBody, ResendErrorResponse.class);
            if (error != null && error.message() != null && !error.message().isBlank()) {
                return error.message();
            }
        } catch (Exception ignored) {
            // fallback to raw body if not standard JSON
        }
        return responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody;
    }
}
