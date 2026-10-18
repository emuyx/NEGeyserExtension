/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.text.Normalizer;

/**
 * Converts NetEase player names containing CJK characters into ASCII pinyin and trims them to
 * the Java username limit (16 characters). Only used when {@code translate-netease-names} is on.
 */
public final class NeteaseNameTranslator {

    private static final int MAX_LENGTH = 16;
    private static final String FALLBACK = "Player";

    private NeteaseNameTranslator() {
    }

    public static String toAscii(String input) {
        if (input == null || input.isBlank()) {
            return FALLBACK;
        }

        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);

        StringBuilder out = new StringBuilder(input.length());
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (codePoint < 128) {
                char c = (char) codePoint;
                if (isNameChar(c)) {
                    out.append(c);
                } else {
                    appendSeparator(out);
                }
                continue;
            }

            String pinyin = null;
            if (codePoint <= Character.MAX_VALUE) {
                try {
                    String[] readings = PinyinHelper.toHanyuPinyinStringArray((char) codePoint, format);
                    if (readings != null && readings.length > 0) {
                        pinyin = readings[0];
                    }
                } catch (Exception ignored) {
                    // Not a CJK character or unsupported format; fall through.
                }
            }
            if (pinyin != null && !pinyin.isEmpty()) {
                for (int i = 0; i < pinyin.length(); i++) {
                    char c = pinyin.charAt(i);
                    if (isNameChar(c)) {
                        out.append(c);
                    }
                }
                continue;
            }

            // Accented Latin letters can be reduced to ASCII; other scripts become separators.
            String normalized = Normalizer.normalize(
                new String(Character.toChars(codePoint)), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
            boolean appended = false;
            for (int i = 0; i < normalized.length(); i++) {
                char c = normalized.charAt(i);
                if (c < 128 && isNameChar(c)) {
                    out.append(c);
                    appended = true;
                }
            }
            if (!appended) {
                appendSeparator(out);
            }
        }

        String name = out.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) {
            return FALLBACK;
        }
        if (Character.isDigit(name.charAt(0))) {
            name = "P" + name;
        }
        if (name.length() < 3) {
            name = name + "_".repeat(3 - name.length());
        }
        if (name.length() > MAX_LENGTH) {
            String suffix = Integer.toHexString(input.hashCode());
            suffix = suffix.substring(Math.max(0, suffix.length() - 4));
            int keep = MAX_LENGTH - 1 - suffix.length();
            name = name.substring(0, Math.max(0, keep)) + "_" + suffix;
        }
        return name;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_';
    }

    private static void appendSeparator(StringBuilder out) {
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '_') {
            out.append('_');
        }
    }
}
