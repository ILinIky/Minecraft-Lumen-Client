package com.example.examplemod.module;

public enum Category {
    RENDER("Render", "◉"),
    MOVEMENT("Bewegung", "➤"),
    PLAYER("Spieler", "☺"),
    WORLD("Welt", "✦"),
    CLIENT("Client", "⚙");

    public final String title;
    public final String icon;

    Category(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }
}
