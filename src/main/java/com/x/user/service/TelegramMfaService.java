package com.x.user.service;

import com.x.user.dto.TelegramMfaChallengeResponse;
import com.x.user.dto.TelegramMfaLinkResponse;
import com.x.user.dto.TelegramMfaStatusResponse;
import com.x.user.model.UserTelegramMfa;
import com.x.user.model.UserTelegramMfaChallenge;
import com.x.user.repository.UserTelegramMfaChallengeRepository;
import com.x.user.repository.UserRepository;
import com.x.user.repository.UserTelegramMfaRepository;
import com.x.user.telegram.TelegramAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TelegramMfaService {
    private static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final UserTelegramMfaRepository accountRepository;
    private final UserTelegramMfaChallengeRepository challengeRepository;
    private final TelegramAuthClient telegram;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder codeEncoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public TelegramMfaStatusResponse status(long userId) {
        UserTelegramMfa account = accountRepository.findByUserId(userId).orElse(null);
        return new TelegramMfaStatusResponse(
                telegram.isConfigured(),
                safeBotUsername(),
                account != null && account.getChatId() != null,
                account != null && account.isEnabled(),
                account != null && account.getLinkCodeHash() != null
                        && account.getLinkExpiresAt() != null && account.getLinkExpiresAt().isAfter(Instant.now()));
    }

    @Transactional
    public TelegramMfaLinkResponse createLink(long userId) {
        requireBot();
        UserTelegramMfa account = accountRepository.findByUserId(userId).orElseGet(() -> {
            UserTelegramMfa created = new UserTelegramMfa();
            created.setUserId(userId);
            return created;
        });
        if (account.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disable Telegram 2FA before changing its linked account");
        }
        String code = randomToken(24);
        Instant expiresAt = Instant.now().plus(LINK_TTL);
        account.setLinkCodeHash(sha256(code));
        account.setLinkExpiresAt(expiresAt);
        accountRepository.save(account);
        String username = safeBotUsername();
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Telegram authentication bot is not reachable");
        }
        return new TelegramMfaLinkResponse("https://t.me/" + username + "?start=" + code, expiresAt);
    }

    @Transactional
    public boolean connect(String rawCode, long chatId, String chatType) {
        if (!"private".equalsIgnoreCase(chatType) || rawCode == null || rawCode.isBlank()) return false;
        UserTelegramMfa account = accountRepository.findByLinkCodeHash(sha256(rawCode.trim())).orElse(null);
        if (account == null || account.getLinkExpiresAt() == null || account.getLinkExpiresAt().isBefore(Instant.now())) return false;
        if (accountRepository.existsByChatIdAndUserIdNot(chatId, account.getUserId())) return false;
        account.setChatId(chatId);
        account.setLinkedAt(Instant.now());
        account.setLinkCodeHash(null);
        account.setLinkExpiresAt(null);
        accountRepository.save(account);
        return true;
    }

    @Transactional
    public TelegramMfaStatusResponse setEnabled(long userId, boolean enabled) {
        UserTelegramMfa account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Connect Telegram before enabling 2FA"));
        if (enabled && account.getChatId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connect Telegram before enabling 2FA");
        }
        if (enabled) requireBot();
        account.setEnabled(enabled);
        accountRepository.save(account);
        if (!enabled) challengeRepository.deleteAllByUserId(userId);
        return status(userId);
    }

    @Transactional
    public TelegramMfaStatusResponse disconnect(long userId) {
        accountRepository.findByUserId(userId).ifPresent(accountRepository::delete);
        challengeRepository.deleteAllByUserId(userId);
        return status(userId);
    }

    @Transactional
    public TelegramMfaChallengeResponse startLogin(long userId, String username) {
        UserTelegramMfa account = accountRepository.findByUserId(userId)
                .filter(UserTelegramMfa::isEnabled)
                .filter(item -> item.getChatId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Telegram 2FA is not enabled"));
        requireBot();
        Instant now = Instant.now();
        if (challengeRepository.existsByUserIdAndCreatedAtAfter(userId, now.minus(SEND_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another Telegram code");
        }
        String token = randomToken(32);
        String code = String.format("%08d", random.nextInt(100_000_000));
        UserTelegramMfaChallenge challenge = new UserTelegramMfaChallenge();
        challenge.setUserId(userId);
        challenge.setTokenHash(sha256(token));
        challenge.setCodeHash(codeEncoder.encode(code));
        challenge.setExpiresAt(now.plus(CHALLENGE_TTL));
        challengeRepository.save(challenge);
        try {
            String name = userRepository.findById(userId)
                    .map(user -> user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername())
                    .orElse(username);
            telegram.sendBrandedMessage(account.getChatId(),
                    MfaMessages.telegramCode(MfaMessages.Purpose.TELEGRAM_LOGIN, name, code, challenge.getExpiresAt()));
        } catch (RuntimeException exception) {
            challengeRepository.delete(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the Telegram sign-in code");
        }
        return new TelegramMfaChallengeResponse(token, challenge.getExpiresAt());
    }

    @Transactional
    public boolean verify(long userId, String rawToken, String code) {
        if (rawToken == null || rawToken.isBlank() || code == null || !code.matches("\\d{8}")) return false;
        UserTelegramMfaChallenge challenge = challengeRepository.findByTokenHash(sha256(rawToken)).orElse(null);
        if (challenge == null || !challenge.getUserId().equals(userId)) return false;
        if (challenge.getExpiresAt().isBefore(Instant.now()) || challenge.getAttempts() >= MAX_ATTEMPTS) {
            challengeRepository.delete(challenge);
            return false;
        }
        if (codeEncoder.matches(code, challenge.getCodeHash())) {
            challengeRepository.delete(challenge);
            return true;
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() >= MAX_ATTEMPTS) challengeRepository.delete(challenge);
        else challengeRepository.save(challenge);
        return false;
    }

    @Transactional
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanExpiredChallenges() {
        challengeRepository.deleteByExpiresAtBefore(Instant.now());
        accountRepository.findAll().stream()
                .filter(account -> account.getLinkExpiresAt() != null && account.getLinkExpiresAt().isBefore(Instant.now()))
                .forEach(account -> {
                    account.setLinkExpiresAt(null);
                    account.setLinkCodeHash(null);
                    accountRepository.save(account);
                });
    }

    private void requireBot() {
        if (!telegram.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram authentication bot is not configured");
        }
    }

    private String safeBotUsername() {
        try { return telegram.botUsername(); }
        catch (RuntimeException exception) { return null; }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
