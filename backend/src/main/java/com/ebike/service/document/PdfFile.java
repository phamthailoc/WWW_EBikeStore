package com.ebike.service.document;

/** A finished PDF and the name it is saved under. The name is plain ASCII, safe in a header and on any file system. */
public record PdfFile(String filename, byte[] content) {

    /** Keeps letters, digits, dot, dash and underscore; everything else (a space, a slash, a quote, a line break) becomes an underscore. */
    public static String safeName(String value) {
        String text = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return text.isEmpty() ? "document" : text;
    }
}
