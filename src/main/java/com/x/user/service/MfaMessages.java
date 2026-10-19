package com.x.user.service;

import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Wording of the verification codes sent by email and Telegram, kept in one place so both channels read alike. */
public final class MfaMessages {
    static final String PRODUCT = "TinhLuk";
    static final String EMAIL_LOGO_ID = "tinhluk-logo";
    static final String EMAIL_LOGO_PATH = "branding/tinhluk-logo-email.png";

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    enum Purpose {
        EMAIL_SETUP("Verify your email address",
                "Use the code below to confirm this email address for two-step verification on your " + PRODUCT + " account.",
                "If you did not request this, you can ignore this email. Two-step verification will not be turned on."),
        EMAIL_LOGIN("Sign-in verification",
                "Use the code below to complete your sign-in to " + PRODUCT + ".",
                "If you did not try to sign in, someone may know your password. Please change your password as soon as possible."),
        TELEGRAM_LOGIN("Sign-in verification",
                "Use the code below to complete your sign-in to " + PRODUCT + ".",
                "If you did not try to sign in, someone may know your password. Please change your password as soon as possible.");

        final String title;
        final String intro;
        final String warning;

        Purpose(String title, String intro, String warning) {
            this.title = title;
            this.intro = intro;
            this.warning = warning;
        }
    }

    private MfaMessages() {
    }

    static String emailSubject(Purpose purpose) {
        return purpose == Purpose.EMAIL_SETUP
                ? PRODUCT + ": verify your email address"
                : PRODUCT + ": your sign-in verification code";
    }

    static String emailText(Purpose purpose, String name, String code, Instant expiresAt) {
        return greeting(name) + ",\n\n"
                + purpose.intro + "\n\n"
                + "Verification code: " + code + "\n\n"
                + "This code expires in 5 minutes (" + TIME.format(expiresAt.atZone(ZONE)) + ", Phnom Penh time). "
                + "Do not share it with anyone. " + PRODUCT + " staff will never ask for your code.\n\n"
                + purpose.warning + "\n\n"
                + "Regards,\nThe " + PRODUCT + " Team\n\n"
                + "This is an automated message. Please do not reply.";
    }

    static String emailHtml(Purpose purpose, String name, String code, Instant expiresAt) {
        String expires = TIME.format(expiresAt.atZone(ZONE));
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%1$s</title></head>
                <body style="margin:0;padding:0;background:#f3f6fb;font-family:Segoe UI,Helvetica,Arial,sans-serif;color:#1b2a4a">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f6fb;padding:32px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border:1px solid #e3e8f0;border-radius:12px">
                <tr><td style="padding:28px 32px 20px;border-bottom:1px solid #eef1f6">
                <img src="cid:%2$s" width="132" alt="%3$s" style="display:block;border:0;height:auto">
                </td></tr>
                <tr><td style="padding:28px 32px 8px">
                <h1 style="margin:0 0 16px;font-size:20px;font-weight:600;color:#1b2a4a">%1$s</h1>
                <p style="margin:0 0 12px;font-size:15px;line-height:22px">%4$s,</p>
                <p style="margin:0 0 20px;font-size:15px;line-height:22px">%5$s</p>
                <div style="margin:0 0 20px;padding:18px;background:#f0f6ff;border:1px solid #d6e6ff;border-radius:8px;text-align:center">
                <div style="font-size:12px;letter-spacing:1px;text-transform:uppercase;color:#5b6b86;margin-bottom:6px">Verification code</div>
                <div style="font-family:Consolas,Menlo,monospace;font-size:30px;font-weight:700;letter-spacing:6px;color:#1b2a4a">%6$s</div>
                </div>
                <p style="margin:0 0 12px;font-size:14px;line-height:21px;color:#41506b">This code expires in <strong>5 minutes</strong> (%7$s, Phnom Penh time). Do not share it with anyone. %3$s staff will never ask for your code.</p>
                <p style="margin:0 0 24px;font-size:14px;line-height:21px;color:#41506b">%8$s</p>
                <p style="margin:0 0 28px;font-size:15px;line-height:22px">Regards,<br>The %3$s Team</p>
                </td></tr>
                <tr><td style="padding:16px 32px;background:#f8fafc;border-top:1px solid #eef1f6;border-radius:0 0 12px 12px;font-size:12px;line-height:18px;color:#7a879c">
                This is an automated message from %3$s. Please do not reply to this email.<br>&copy; %9$s %3$s
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body></html>
                """.formatted(
                HtmlUtils.htmlEscape(purpose.title), EMAIL_LOGO_ID, PRODUCT,
                HtmlUtils.htmlEscape(greeting(name)), HtmlUtils.htmlEscape(purpose.intro), code,
                expires, HtmlUtils.htmlEscape(purpose.warning), Year.now(ZONE));
    }

    /** Telegram caption in HTML parse mode; the code sits in a code tag so it can be copied with a tap. */
    static String telegramCode(Purpose purpose, String name, String code, Instant expiresAt) {
        return "<b>" + PRODUCT + " · " + purpose.title + "</b>\n\n"
                + HtmlUtils.htmlEscape(greeting(name)) + ",\n\n"
                + purpose.intro + "\n\n"
                + "Verification code: <code>" + code + "</code>\n\n"
                + "This code expires in 5 minutes (" + TIME.format(expiresAt.atZone(ZONE)) + ", Phnom Penh time). "
                + "Do not share it with anyone. " + PRODUCT + " staff will never ask for your code.\n\n"
                + "<i>" + purpose.warning + "</i>";
    }

    public static String telegramLinked() {
        return "<b>" + PRODUCT + " · Telegram connected</b>\n\n"
                + "This Telegram account is now linked to your " + PRODUCT + " account.\n\n"
                + "To finish, return to <b>Account Settings → Security</b> and turn on Telegram two-step verification. "
                + "Sign-in verification codes will then be sent to this chat.";
    }

    public static String telegramLinkInvalid() {
        return "<b>" + PRODUCT + " · Connection link expired</b>\n\n"
                + "This connection link is invalid or has expired. "
                + "Please create a new link in <b>Account Settings → Security</b> and open it within 10 minutes.";
    }

    public static String telegramWelcome() {
        return "<b>Welcome to " + PRODUCT + " account security</b>\n\n"
                + "This bot sends sign-in verification codes for your " + PRODUCT + " account.\n\n"
                + "To connect it, sign in to " + PRODUCT + ", open <b>Account Settings → Security</b> and choose <b>Connect Telegram</b>.";
    }

    private static String greeting(String name) {
        return name == null || name.isBlank() ? "Hello" : "Hello " + name.trim();
    }
}
