package com.x.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "x.telegram")
public class TelegramAuthProperties {
    private String authBotToken;
    private String authApiBaseUrl = "https://api.telegram.org";
    private boolean authPollingEnabled = true;

    public boolean isConfigured() { return StringUtils.hasText(authBotToken); }
    public String getAuthBotToken() { return authBotToken; }
    public void setAuthBotToken(String authBotToken) { this.authBotToken = authBotToken; }
    public String getAuthApiBaseUrl() { return authApiBaseUrl; }
    public void setAuthApiBaseUrl(String authApiBaseUrl) { this.authApiBaseUrl = authApiBaseUrl; }
    public boolean isAuthPollingEnabled() { return authPollingEnabled; }
    public void setAuthPollingEnabled(boolean authPollingEnabled) { this.authPollingEnabled = authPollingEnabled; }
}
