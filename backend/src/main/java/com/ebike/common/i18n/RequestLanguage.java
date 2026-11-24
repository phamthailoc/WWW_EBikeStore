package com.ebike.common.i18n;

import jakarta.servlet.http.HttpServletRequest;

/** The shop speaks Vietnamese only: these helpers return the Vietnamese text and are being removed call site by call site. */
public final class RequestLanguage {

    private RequestLanguage() {
    }

    public static String current() {
        return "vi";
    }

    public static String text(String vietnamese, String english) {
        return vietnamese;
    }

    public static String text(HttpServletRequest request, String vietnamese, String english) {
        return vietnamese;
    }
}
