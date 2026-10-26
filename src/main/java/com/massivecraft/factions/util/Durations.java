package com.massivecraft.factions.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms]?)");

    private Durations() { }

    public static long parse(String text) {
        if (text == null) return -1;
        String s = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) return -1;
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            long n = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "d":
                    total += n * 86400;
                    break;
                case "h":
                    total += n * 3600;
                    break;
                case "m":
                    total += n * 60;
                    break;
                default:
                    total += n;
            }
            end = m.end();
        }
        return end == s.length() ? total : -1;
    }

    public static String ago(long seconds) {
        long total = Math.max(0, seconds / 60);
        return total == 0 ? "<1m" : format(total * 60);
    }

    public static String format(long seconds) {
        long total = Math.max(0, (seconds + 59) / 60);
        long d = total / 1440, h = total % 1440 / 60, m = total % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0 || b.length() == 0) b.append(m).append("m");
        return b.toString().trim();
    }
}
