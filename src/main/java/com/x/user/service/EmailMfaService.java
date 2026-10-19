package com.x.user.service;

import com.x.user.dto.EmailMfaChallengeResponse;
import com.x.user.dto.EmailMfaStatusResponse;
import com.x.user.model.User;
import com.x.user.model.UserEmailMfa;
import com.x.user.model.UserEmailMfaChallenge;
import com.x.user.repository.UserEmailMfaChallengeRepository;
import com.x.user.repository.UserEmailMfaRepository;
import com.x.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailMfaService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 5;
    private static final String SETUP = "SETUP";
    private static final String LOGIN = "LOGIN";

    private final UserRepository userRepository;
    private final UserEmailMfaRepository accountRepository;
    private final UserEmailMfaChallengeRepository challengeRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder codeEncoder = new BCryptPasswordEncoder();

    @Value("${spring.mail.username:}")
    private String senderUsername;
    @Value("${spring.mail.host:}")
    private String mailHost;
    @Value("${spring.mail.password:}")
    private String mailPassword;
    @Value("${security.mfa.email.from:}")
    private String senderAddress;

    @Transactional(readOnly = true)
    public EmailMfaStatusResponse status(long userId) {
        User user = getUser(userId);
        String currentEmail = normalize(user.getEmail());
        UserEmailMfa account = accountRepository.findByUserId(userId).orElse(null);
        boolean verified = account != null && currentEmail != null
                && currentEmail.equals(normalize(account.getVerifiedEmail()));
        return new EmailMfaStatusResponse(isMailConfigured(), currentEmail != null
                && currentEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"), verified,
                account != null && account.isEnabled(), currentEmail == null ? null : mask(currentEmail));
    }

    @Transactional
    public EmailMfaChallengeResponse startSetup(long userId) {
        requireMail();
        String email = normalize(getUser(userId).getEmail());
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Add a valid email address to your account before enabling email 2FA");
        }
        return createChallenge(userId, email, SETUP);
    }

    @Transactional
    public boolean verifySetup(long userId, String rawToken, String code) {
        UserEmailMfaChallenge challenge = findValidChallenge(userId, rawToken, code, SETUP);
        if (challenge == null) return false;
        User user = getUser(userId);
        String currentEmail = normalize(user.getEmail());
        if (currentEmail == null || !currentEmail.equals(normalize(challenge.getEmailAddress()))) {
            challengeRepository.delete(challenge);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your account email changed. Request a new verification code");
        }
        UserEmailMfa account = accountRepository.findByUserId(userId).orElseGet(UserEmailMfa::new);
        account.setUserId(userId);
        account.setVerifiedEmail(currentEmail);
        account.setEnabled(true);
        accountRepository.save(account);
        challengeRepository.delete(challenge);
        return true;
    }

    @Transactional
    public EmailMfaStatusResponse setEnabled(long userId, boolean enabled) {
        UserEmailMfa account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Verify your account email before enabling email 2FA"));
        String currentEmail = normalize(getUser(userId).getEmail());
        if (enabled && (currentEmail == null || !currentEmail.equals(normalize(account.getVerifiedEmail())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Verify your current account email before enabling email 2FA");
        }
        if (enabled) requireMail();
        account.setEnabled(enabled);
        accountRepository.save(account);
        if (!enabled) challengeRepository.deleteAllByUserId(userId);
        return status(userId);
    }

    @Transactional
    public EmailMfaStatusResponse disconnect(long userId) {
        accountRepository.findByUserId(userId).ifPresent(accountRepository::delete);
        challengeRepository.deleteAllByUserId(userId);
        return status(userId);
    }

    @Transactional
    public EmailMfaChallengeResponse startLogin(long userId) {
        requireMail();
        UserEmailMfa account = accountRepository.findByUserId(userId)
                .filter(UserEmailMfa::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email 2FA is not enabled"));
        String currentEmail = normalize(getUser(userId).getEmail());
        if (currentEmail == null || !currentEmail.equals(normalize(account.getVerifiedEmail()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The account email changed. Contact an administrator to restore sign-in verification");
        }
        return createChallenge(userId, currentEmail, LOGIN);
    }

    @Transactional
    public boolean verifyLogin(long userId, String rawToken, String code) {
        UserEmailMfaChallenge challenge = findValidChallenge(userId, rawToken, code, LOGIN);
        if (challenge == null) return false;
        challengeRepository.delete(challenge);
        return true;
    }

    @Transactional
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanExpiredChallenges() {
        challengeRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private EmailMfaChallengeResponse createChallenge(long userId, String email, String purpose) {
        Instant now = Instant.now();
        if (challengeRepository.existsByUserIdAndCreatedAtAfter(userId, now.minus(SEND_COOLDOWN))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another email code");
        }
        String token = randomToken(32);
        String code = String.format("%08d", random.nextInt(100_000_000));
        UserEmailMfaChallenge challenge = new UserEmailMfaChallenge();
        challenge.setUserId(userId);
        challenge.setTokenHash(sha256(token));
        challenge.setCodeHash(codeEncoder.encode(code));
        challenge.setEmailAddress(email);
        challenge.setPurpose(purpose);
        challenge.setExpiresAt(now.plus(CHALLENGE_TTL));
        challengeRepository.save(challenge);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress);
            helper.setTo(email);
            helper.setSubject(purpose.equals(SETUP) ? "Verify your TinhLuk sign-in email" : "Your TinhLuk sign-in code");
            helper.setText("Your TinhLuk verification code is " + code + ".\n\nIt expires in 5 minutes. If you did not request it, you can ignore this email.");
            mailSender.send(message);
        } catch (Exception exception) {
            challengeRepository.delete(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the email verification code");
        }
        return new EmailMfaChallengeResponse(token, challenge.getExpiresAt(), mask(email));
    }

    private UserEmailMfaChallenge findValidChallenge(long userId, String rawToken, String code, String purpose) {
        if (rawToken == null || rawToken.isBlank() || code == null || !code.matches("\\d{8}")) return null;
        UserEmailMfaChallenge challenge = challengeRepository.findByTokenHash(sha256(rawToken)).orElse(null);
        if (challenge == null || !challenge.getUserId().equals(userId) || !purpose.equals(challenge.getPurpose())) return null;
        if (challenge.getExpiresAt().isBefore(Instant.now()) || challenge.getAttempts() >= MAX_ATTEMPTS) {
            challengeRepository.delete(challenge);
            return null;
        }
        if (codeEncoder.matches(code, challenge.getCodeHash())) return challenge;
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() >= MAX_ATTEMPTS) challengeRepository.delete(challenge);
        else challengeRepository.save(challenge);
        return null;
    }

    private User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireMail() {
        if (!isMailConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email verification is not configured on the server");
    }

    private boolean isMailConfigured() {
        return mailHost != null && !mailHost.isBlank()
                && senderUsername != null && !senderUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank()
                && senderAddress != null && !senderAddress.isBlank();
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String normalize(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 1) return "•••";
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "•••" + email.substring(at);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
