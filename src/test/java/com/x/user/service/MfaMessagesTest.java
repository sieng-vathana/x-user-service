package com.x.user.service;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaMessagesTest {
    private static final Instant EXPIRES = Instant.parse("2026-10-19T10:05:00Z");

    @Test
    void emailShowsTheLogoCodeAndPhnomPenhExpiry() throws Exception {
        String html = MfaMessages.emailHtml(MfaMessages.Purpose.EMAIL_LOGIN, "Sok <Dara>", "12345678", EXPIRES);
        assertTrue(html.contains("src=\"cid:tinhluk-logo\""));
        assertTrue(html.contains("12345678"));
        assertTrue(html.contains("19 Oct 2026, 17:05"));
        assertTrue(html.contains("Hello Sok &lt;Dara&gt;"), "names are escaped");
        assertFalse(html.contains("<Dara>"));

        String preview = System.getProperty("mfa.preview.dir");
        if (preview != null) {
            Files.writeString(Path.of(preview, "email-login.html"), html.replace("cid:tinhluk-logo", "logo-email.png"));
            Files.writeString(Path.of(preview, "email-setup.html"), MfaMessages.emailHtml(MfaMessages.Purpose.EMAIL_SETUP, "Sok Dara", "87654321", EXPIRES).replace("cid:tinhluk-logo", "logo-email.png"));
            Files.writeString(Path.of(preview, "telegram.txt"), MfaMessages.telegramCode(MfaMessages.Purpose.TELEGRAM_LOGIN, "Sok Dara", "12345678", EXPIRES)
                    + "\n-----\n" + MfaMessages.telegramLinked() + "\n-----\n" + MfaMessages.telegramWelcome() + "\n-----\n" + MfaMessages.telegramLinkInvalid());
        }
    }

    @Test
    void emailCarriesThePlainTextVersionAndTheInlineLogo() throws Exception {
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom("no-reply@example.com", MfaMessages.PRODUCT);
        helper.setTo("owner@example.com");
        helper.setSubject(MfaMessages.emailSubject(MfaMessages.Purpose.EMAIL_LOGIN));
        helper.setText(MfaMessages.emailText(MfaMessages.Purpose.EMAIL_LOGIN, "Sok Dara", "12345678", EXPIRES),
                MfaMessages.emailHtml(MfaMessages.Purpose.EMAIL_LOGIN, "Sok Dara", "12345678", EXPIRES));
        helper.addInline(MfaMessages.EMAIL_LOGO_ID, new ClassPathResource(MfaMessages.EMAIL_LOGO_PATH), "image/png");
        message.saveChanges();

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        String mime = raw.toString(StandardCharsets.UTF_8);
        assertTrue(mime.contains("TinhLuk <no-reply@example.com>"));
        assertTrue(mime.contains("Content-ID: <tinhluk-logo>"));
        assertTrue(mime.contains("text/plain"));
        assertTrue(mime.contains("text/html"));
        assertTrue(hasPart(message, "image/png"));
    }

    @Test
    void telegramCaptionFitsTheTelegramLimitAndEscapesNames() {
        String caption = MfaMessages.telegramCode(MfaMessages.Purpose.TELEGRAM_LOGIN, "A&B " + "x".repeat(200), "12345678", EXPIRES);
        assertTrue(caption.length() <= 1024, "Telegram photo captions are limited to 1024 characters");
        assertTrue(caption.contains("<code>12345678</code>"));
        assertTrue(caption.contains("A&amp;B"));
    }

    private static boolean hasPart(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return true;
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                if (hasPart(multipart.getBodyPart(i), type)) return true;
            }
        }
        return false;
    }
}
