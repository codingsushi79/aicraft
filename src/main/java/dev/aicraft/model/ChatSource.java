package dev.aicraft.model;

public enum ChatSource {
    INGAME,
    WEB;

    public static ChatSource fromString(String value) {
        return "WEB".equalsIgnoreCase(value) ? WEB : INGAME;
    }
}
