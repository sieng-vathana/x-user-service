package com.x.user.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.user.config.TelegramAuthProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
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
    private static final String LOGO_PATH = "branding/tinhluk-logo-telegram.png";
    private volatile String botUsername;
    private volatile String logoFileId;

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

    public void sendHtmlMessage(long chatId, String html) {
        call("sendMessage", Map.of("chat_id", chatId, "text", html, "parse_mode", "HTML", "disable_web_page_preview", true));
    }

    /**
     * Sends the TinhLuk logo with an HTML caption. The logo is uploaded once and then reused by its Telegram file id.
     * Falls back to a plain HTML message if the photo cannot be sent, so codes still arrive.
     */
    public void sendBrandedMessage(long chatId, String htmlCaption) {
        try {
            String fileId = logoFileId;
            JsonNode result = fileId != null
                    ? call("sendPhoto", Map.of("chat_id", chatId, "photo", fileId, "caption", htmlCaption, "parse_mode", "HTML"))
                    : uploadLogo(chatId, htmlCaption);
            JsonNode sizes = result.path("photo");
            if (fileId == null && sizes.isArray() && !sizes.isEmpty()) {
                logoFileId = sizes.get(sizes.size() - 1).path("file_id").asText(null);
            }
        } catch (TelegramAuthException exception) {
            logoFileId = null;
            sendHtmlMessage(chatId, htmlCaption);
        }
    }

    private JsonNode uploadLogo(long chatId, String htmlCaption) {
        byte[] logo;
        try (InputStream input = new ClassPathResource(LOGO_PATH).getInputStream()) {
            logo = input.readAllBytes();
        } catch (IOException exception) {
            throw new TelegramAuthException("The TinhLuk logo is missing");
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chat_id", String.valueOf(chatId));
        form.add("caption", htmlCaption);
        form.add("parse_mode", "HTML");
        form.add("photo", new ByteArrayResource(logo) {
            @Override
            public String getFilename() { return "tinhluk.png"; }
        });
        return call("sendPhoto", form, MediaType.MULTIPART_FORM_DATA);
    }

    public JsonNode getUpdates(long offset, int timeoutSeconds) {
        return call("getUpdates", Map.of("offset", offset, "timeout", timeoutSeconds, "allowed_updates", List.of("message")));
    }

    private JsonNode call(String method, Map<String, ?> body) {
        return call(method, body, MediaType.APPLICATION_JSON);
    }

    private JsonNode call(String method, Object body, MediaType contentType) {
        if (!isConfigured()) throw new TelegramAuthException("The Telegram authentication bot is not configured");
        JsonNode response;
        try {
            response = client.post()
                    .uri(URI.create(properties.getAuthApiBaseUrl() + "/bot" + properties.getAuthBotToken() + "/" + method))
                    .contentType(contentType)
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
