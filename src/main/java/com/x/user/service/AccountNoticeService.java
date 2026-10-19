package com.x.user.service;

import com.x.user.model.User;
import com.x.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.ZoneId;

/** Emails a user a notice from the TinhLuk platform, such as their merchant or store being suspended. */
@Service
@RequiredArgsConstructor
public class AccountNoticeService {
    private static final Logger log = LoggerFactory.getLogger(AccountNoticeService.class);

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${security.mfa.email.from:}")
    private String senderAddress;
    @Value("${spring.mail.host:}")
    private String mailHost;

    /** Returns false when the user has no email address or mail is not set up; the notice is then skipped. */
    public boolean send(long userId, String subject, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (email.isEmpty() || senderAddress == null || senderAddress.isBlank() || mailHost == null || mailHost.isBlank()) {
            return false;
        }
        String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress, MfaMessages.PRODUCT);
            helper.setTo(email);
            helper.setSubject(MfaMessages.PRODUCT + ": " + subject);
            helper.setText(text(name, message), html(title, name, message));
            helper.addInline(MfaMessages.EMAIL_LOGO_ID, new ClassPathResource(MfaMessages.EMAIL_LOGO_PATH), "image/png");
            mailSender.send(mime);
            return true;
        } catch (Exception exception) {
            log.warn("Account notice to user {} was not sent: {}", userId, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the account notice email");
        }
    }

    static String text(String name, String message) {
        return "Hello " + name + ",\n\n" + message + "\n\nRegards,\nThe " + MfaMessages.PRODUCT + " Team\n\n"
                + "This is an automated message. Please do not reply.";
    }

    static String html(String title, String name, String message) {
        String paragraphs = HtmlUtils.htmlEscape(message).replace("\n\n", "</p><p style=\"margin:0 0 14px;font-size:15px;line-height:22px\">").replace("\n", "<br>");
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%1$s</title></head>
                <body style="margin:0;padding:0;background:#f3f6fb;font-family:Segoe UI,Helvetica,Arial,sans-serif;color:#1b2a4a">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f6fb;padding:32px 12px"><tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border:1px solid #e3e8f0;border-radius:12px">
                <tr><td style="padding:28px 32px 20px;border-bottom:1px solid #eef1f6"><img src="cid:%2$s" width="132" alt="%3$s" style="display:block;border:0;height:auto"></td></tr>
                <tr><td style="padding:28px 32px 8px">
                <h1 style="margin:0 0 16px;font-size:20px;font-weight:600;color:#1b2a4a">%1$s</h1>
                <p style="margin:0 0 14px;font-size:15px;line-height:22px">Hello %4$s,</p>
                <p style="margin:0 0 14px;font-size:15px;line-height:22px">%5$s</p>
                <p style="margin:14px 0 28px;font-size:15px;line-height:22px">Regards,<br>The %3$s Team</p>
                </td></tr>
                <tr><td style="padding:16px 32px;background:#f8fafc;border-top:1px solid #eef1f6;border-radius:0 0 12px 12px;font-size:12px;line-height:18px;color:#7a879c">
                This is an automated message from %3$s. Please do not reply to this email.<br>&copy; %6$s %3$s
                </td></tr></table></td></tr></table></body></html>
                """.formatted(HtmlUtils.htmlEscape(title), MfaMessages.EMAIL_LOGO_ID, MfaMessages.PRODUCT,
                HtmlUtils.htmlEscape(name), paragraphs, Year.now(ZoneId.of("Asia/Phnom_Penh")));
    }
}
