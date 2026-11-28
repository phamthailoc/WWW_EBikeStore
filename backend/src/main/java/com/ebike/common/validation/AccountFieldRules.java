package com.ebike.common.validation;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * One set of rules for the fields of an account (sign-up, own profile, admin "create account").
 * Column sizes follow the users table: username 100, email 255, first/last name 100.
 * Callers validate only the fields that are present in the request, so an old account with legacy data can still change other fields.
 */
public final class AccountFieldRules {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_EMAIL_LENGTH = 255;
    public static final int MAX_USERNAME_LENGTH = 100;
    public static final int MAX_NAME_LENGTH = 100;

    // labels of letters/digits/hyphen only, a dotted domain with a 2+ letter ending: "a@b.com." and "a@b" are refused
    private static final Pattern EMAIL = Pattern.compile(
        "^[A-Z0-9._%+-]+@([A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?\\.)+[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private AccountFieldRules() {
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(email).matches();
    }

    /** Trimmed and lower-cased; 400 "Email không hợp lệ." when it is missing, too long or malformed. */
    public static String email(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!isValidEmail(email)) {
            throw bad("Email không hợp lệ.");
        }
        return email;
    }

    /** A name part (first or last): trimmed, null when blank, 400 when longer than the column. */
    public static String nameOrNull(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw bad(label + " tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
        return name;
    }

    /** A name part that was sent and so must not be blank (the profile form); 400 when blank or too long. */
    public static String requiredName(String raw, String label) {
        String name = nameOrNull(raw, label);
        if (name == null) {
            throw bad(label + " không được để trống.");
        }
        return name;
    }

    /** Trimmed; no spaces, no "@" (it would shadow somebody's email at sign-in), at most 100 characters. */
    public static String username(String raw) {
        String username = raw == null ? "" : raw.trim();
        if (username.isEmpty()) {
            throw bad("Vui lòng nhập tên đăng nhập.");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw bad("Tên đăng nhập tối đa " + MAX_USERNAME_LENGTH + " ký tự.");
        }
        if (username.chars().anyMatch(Character::isWhitespace) || username.indexOf('@') >= 0) {
            throw bad("Tên đăng nhập không được chứa khoảng trắng hoặc ký tự @.");
        }
        return username;
    }

    /** A login name made from the part of an email before the "@" (the email must already be valid). */
    public static String usernameFromEmail(String validEmail) {
        String local = validEmail.substring(0, validEmail.indexOf('@'));
        return username(local.length() > MAX_USERNAME_LENGTH ? local.substring(0, MAX_USERNAME_LENGTH) : local);
    }

    /** The password is used exactly as typed (never trimmed), here and at sign-in. */
    public static String password(String raw) {
        if (raw == null || raw.isBlank()) {
            throw bad("Vui lòng nhập mật khẩu.");
        }
        if (raw.length() < MIN_PASSWORD_LENGTH) {
            throw bad("Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự.");
        }
        return raw;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
