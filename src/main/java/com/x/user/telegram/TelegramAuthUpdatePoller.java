package com.x.user.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.user.config.TelegramAuthProperties;
import com.x.user.service.MfaMessages;
import com.x.user.service.TelegramMfaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Polls only the dedicated authentication bot and only links private user chats. */
@Component
@RequiredArgsConstructor
public class TelegramAuthUpdatePoller {
    private static final Logger log = LoggerFactory.getLogger(TelegramAuthUpdatePoller.class);
    private final TelegramAuthProperties properties;
    private final TelegramAuthClient telegram;
    private final TelegramMfaService mfaService;
    private long offset;
    private Instant retryAfter = Instant.EPOCH;

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void poll() {
        if (!properties.isConfigured() || !properties.isAuthPollingEnabled() || Instant.now().isBefore(retryAfter)) return;
        JsonNode updates;
        try {
            updates = telegram.getUpdates(offset, 25);
        } catch (RuntimeException exception) {
            log.warn("Telegram authentication updates unavailable; retrying in 30 seconds");
            retryAfter = Instant.now().plusSeconds(30);
            return;
        }
        for (JsonNode update : updates) {
            offset = Math.max(offset, update.path("update_id").asLong() + 1);
            try { handle(update.path("message")); }
            catch (RuntimeException exception) { log.warn("Telegram authentication update could not be handled"); }
        }
    }

    void handle(JsonNode message) {
        JsonNode chat = message.path("chat");
        long chatId = chat.path("id").asLong();
        String text = message.path("text").asText("").trim();
        if (!"private".equalsIgnoreCase(chat.path("type").asText()) || !text.startsWith("/start")) return;
        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            telegram.sendBrandedMessage(chatId, MfaMessages.telegramWelcome());
            return;
        }
        boolean connected = mfaService.connect(parts[1].trim(), chatId, "private");
        if (connected) telegram.sendBrandedMessage(chatId, MfaMessages.telegramLinked());
        else telegram.sendHtmlMessage(chatId, MfaMessages.telegramLinkInvalid());
    }
}
