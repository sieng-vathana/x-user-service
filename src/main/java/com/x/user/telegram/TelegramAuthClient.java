package com.x.user.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.user.config.TelegramAuthProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Telegram client dedicated to account verification. Its token is separate from merchant alert bots. */
@Component
public class TelegramAuthClient {
    private final TelegramAuthProperties properties;
    private final RestClient client;
    private volatile String botUsername;

    public TelegramAuthClient(RestClient.Builder builder, TelegramAuthProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        this.client = builder.clone().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() { return properties.isConfigured(); }
    public boolean isPollingEnabled() { return properties.isAuthPollingEnabled(); }

    public String botUsername() {
        if (botUsername == null && isConfigured()) {
            botUsername = call("getMe", Map.of()).path("username").asText(null);
        }
        return botUsername;
    }

    public void sendMessage(long chatId, String text) {
        call("sendMessage", Map.of("chat_id", chatId, "text", text, "disable_web_page_preview", true));
    }

    public JsonNode getUpdates(long offset, int timeoutSeconds) {
        return call("getUpdates", Map.of("offset", offset, "timeout", timeoutSeconds, "allowed_updates", List.of("message")));
    }

    private JsonNode call(String method, Map<String, ?> body) {
        if (!isConfigured()) throw new TelegramAuthException("The Telegram authentication bot is not configured");
        JsonNode response;
        try {
            response = client.post()
                    .uri(URI.create(properties.getAuthApiBaseUrl() + "/bot" + properties.getAuthBotToken() + "/" + method))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> true, (request, errorResponse) -> { })
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new TelegramAuthException("Could not contact Telegram");
        }
        if (response == null || !response.path("ok").asBoolean(false)) {
            throw new TelegramAuthException("Telegram rejected the authentication bot request");
        }
        return response.path("result");
    }

    public static class TelegramAuthException extends RuntimeException {
        public TelegramAuthException(String message) { super(message); }
    }
}
