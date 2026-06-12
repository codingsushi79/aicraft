package dev.aicraft.util;

public final class ChatText {

    private static final int MAX_SAME_CHAR_RUN = 24;

    private ChatText() {
    }

    public static String sanitizeForDisplay(String input) {
        return normalizeWhitespace(stripTemplateTokens(sanitize(input)));
    }

    public static String sanitizeModelOutput(String input) {
        String cleaned = normalizeWhitespace(stripTemplateTokens(sanitize(input)));
        return collapseRepeatingCharacters(cleaned);
    }

    public static String[] linesForChat(String input) {
        String sanitized = sanitizeForDisplay(input);
        if (sanitized.isEmpty()) {
            return new String[0];
        }
        return sanitized.split("\n");
    }

    private static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(input.length());
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (shouldDrop(codePoint)) {
                continue;
            }
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    private static boolean shouldDrop(int codePoint) {
        if (codePoint == 0 || codePoint == 0xA7) {
            return true;
        }
        if (codePoint >= 0xE000 && codePoint <= 0xF8FF) {
            return true;
        }
        if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
            return false;
        }
        return Character.isISOControl(codePoint);
    }

    private static String stripTemplateTokens(String input) {
        return input
                .replaceAll("<\\|[^|<>]*\\|>", "")
                .replaceAll("<\\|[^|<>]*>", "")
                .replaceAll("</s>", "")
                .replaceAll("<s>", "")
                .replaceAll("\\[/?INST\\]", "")
                .replaceAll("<start_of_turn>\\s*(user|model|system)\\s*", "")
                .replaceAll("<end_of_turn>\\s*", "")
                .replaceAll("</?bos>", "")
                .replaceAll("</?eos>", "");
    }

    private static String normalizeWhitespace(String input) {
        if (input.isEmpty()) {
            return "";
        }

        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replace('\t', ' ');
        normalized = normalizeLineBreakMarkers(normalized);
        // Models often use underscores or dashes as improvised paragraph breaks.
        normalized = normalized.replaceAll("_{2,}", "\n\n");
        normalized = normalized.replaceAll("(?m)^[ \\t]*-{3,}[ \\t]*$", "\n");
        normalized = normalized.replaceAll("(?m)^[ \\t]*\\*{3,}[ \\t]*$", "\n");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        normalized = normalized.replaceAll("(?m)[ \\t]+$", "");
        normalized = normalized.replaceAll("(?m)^[ \\t]+", "");
        normalized = normalized.replaceAll("(?<=[.!?…]) +", "\n\n");
        normalized = collapseInlineSpaces(normalized);
        return normalized.trim();
    }

    private static String normalizeLineBreakMarkers(String input) {
        String normalized = input
                .replace('\u2581', '_') // Gemma SentencePiece word-boundary marker
                .replace('\uFF3F', '_') // fullwidth low line
                .replace('\u2017', '_') // double low line
                .replace('\u203E', '_') // overline
                .replace('\u00A0', ' '); // non-breaking space
        return normalized.replaceAll("(?<=[.!?…])_+", "\n\n");
    }

    private static String collapseInlineSpaces(String input) {
        StringBuilder output = new StringBuilder(input.length());
        boolean lastWasSpace = false;
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                lastWasSpace = false;
                output.appendCodePoint(codePoint);
                continue;
            }
            if (Character.isWhitespace(codePoint)) {
                if (!lastWasSpace) {
                    output.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }
            lastWasSpace = false;
            output.appendCodePoint(codePoint);
        }
        return output.toString();
    }

    private static String collapseRepeatingCharacters(String input) {
        if (input.length() < MAX_SAME_CHAR_RUN) {
            return input;
        }

        StringBuilder output = new StringBuilder(input.length());
        int runCodePoint = -1;
        int runLength = 0;

        for (int offset = 0; offset <= input.length(); ) {
            int codePoint = offset < input.length() ? input.codePointAt(offset) : -1;
            if (codePoint == runCodePoint) {
                runLength++;
            } else {
                appendRun(output, runCodePoint, runLength);
                runCodePoint = codePoint;
                runLength = codePoint >= 0 ? 1 : 0;
            }
            if (offset < input.length()) {
                offset += Character.charCount(codePoint);
            } else {
                break;
            }
        }

        return output.toString();
    }

    private static void appendRun(StringBuilder output, int codePoint, int length) {
        if (codePoint < 0 || length <= 0) {
            return;
        }
        if (length > MAX_SAME_CHAR_RUN) {
            output.appendCodePoint(codePoint)
                    .appendCodePoint(codePoint)
                    .appendCodePoint(codePoint)
                    .append("...");
            return;
        }
        for (int i = 0; i < length; i++) {
            output.appendCodePoint(codePoint);
        }
    }
}
