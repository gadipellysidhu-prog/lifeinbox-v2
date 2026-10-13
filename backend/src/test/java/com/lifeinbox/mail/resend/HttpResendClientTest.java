package com.lifeinbox.mail.resend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpResendClientTest {

    @Mock
    private HttpClient httpClient;

    private ObjectMapper objectMapper;
    private HttpResendClient resendClient;

    private static final String API_URL = "https://api.resend.com/emails";
    private static final String API_KEY = "re_test_mock_key_123456";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        resendClient = new HttpResendClient(httpClient, objectMapper);
    }

    @Test
    @DisplayName("sendEmail sends POST request with Authorization Bearer header and parses 200 response")
    @SuppressWarnings("unchecked")
    void testSendEmailSuccess() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\":\"email_resend_101\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ResendEmailRequest request = new ResendEmailRequest(
                "LifeInbox <onboarding@resend.dev>",
                List.of("recipient@example.com"),
                "Subject Line",
                "<p>HTML</p>",
                "Plain Text"
        );

        ResendEmailResponse response = resendClient.sendEmail(API_URL, API_KEY, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("email_resend_101");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest captured = requestCaptor.getValue();
        assertThat(captured.uri().toString()).isEqualTo(API_URL);
        assertThat(captured.method()).isEqualTo("POST");
        assertThat(captured.headers().firstValue("Authorization")).contains("Bearer " + API_KEY);
        assertThat(captured.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    @DisplayName("sendEmail throws ResendApiException on HTTP 422 with parsed error message")
    @SuppressWarnings("unchecked")
    void testSendEmail422Error() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(422);
        when(mockResponse.body()).thenReturn("{\"statusCode\":422,\"message\":\"The from address does not match your verified domain.\",\"name\":\"validation_error\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ResendEmailRequest request = new ResendEmailRequest(
                "Invalid <invalid@unverified.org>",
                List.of("test@example.com"),
                "Subject",
                "HTML",
                null
        );

        assertThatThrownBy(() -> resendClient.sendEmail(API_URL, API_KEY, request))
                .isInstanceOf(ResendApiException.class)
                .hasMessageContaining("The from address does not match your verified domain.")
                .satisfies(ex -> assertThat(((ResendApiException) ex).getStatusCode()).isEqualTo(422));
    }

    @Test
    @DisplayName("sendEmail handles non-JSON error response gracefully")
    @SuppressWarnings("unchecked")
    void testSendEmailNonJsonError() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(502);
        when(mockResponse.body()).thenReturn("Bad Gateway from upstream edge");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ResendEmailRequest request = new ResendEmailRequest(
                "sender@example.com",
                List.of("test@example.com"),
                "Subject",
                "HTML",
                null
        );

        assertThatThrownBy(() -> resendClient.sendEmail(API_URL, API_KEY, request))
                .isInstanceOf(ResendApiException.class)
                .hasMessageContaining("Bad Gateway from upstream edge")
                .satisfies(ex -> assertThat(((ResendApiException) ex).getStatusCode()).isEqualTo(502));
    }
}
