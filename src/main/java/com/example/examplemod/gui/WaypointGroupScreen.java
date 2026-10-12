package com.example.examplemod.gui;

import com.example.examplemod.waypoint.WaypointGroups;
import com.example.examplemod.waypoint.WaypointStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Gruppenauswahl für Filter und Bearbeitung; eigene Gruppen entstehen mit dem gespeicherten Wegpunkt. */
public final class WaypointGroupScreen extends ClientScreen {
    private record Choice(String value, String label) {}
    private final boolean filtering;
    private final String selected;
    private final Consumer<String> onSelect;
    private final Scroll scroll = new Scroll();
    private String query = "";
    private EditBox search;

    public WaypointGroupScreen(Screen parent, boolean filtering, String selected, Consumer<String> onSelect) {
        super(Component.literal(filtering ? "Gruppenfilter" : "Gruppe wählen"), parent);
        this.filtering = filtering;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override protected int preferredWidth() { return 310; }
    @Override protected int preferredHeight() { return 238; }
    @Override protected boolean isModal() { return true; }

    @Override protected void init() {
        super.init();
        search = borderlessField(winX + 20, winY + 44, winW - 40,
                filtering ? "Gruppe suchen" : "Gruppe suchen oder eigenen Namen eingeben", WaypointGroups.MAX_NAME);
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll.reset(); });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawTitle(g, filtering ? "Gruppenfilter" : "Gruppe wählen", null);
        fieldBackground(g, search, 6, 4);
        List<Choice> choices = new ArrayList<>();
        if (filtering) choices.add(new Choice(null, "Alle Gruppen"));
        choices.add(new Choice("", "Ohne Gruppe"));
        for (String group : WaypointStore.groups()) choices.add(new Choice(group, "„" + group + "“"));
        String needle = query.strip().toLowerCase(Locale.ROOT);
        choices.removeIf(choice -> !choice.label.toLowerCase(Locale.ROOT).contains(needle));
        int listX = winX + 14, listY = winY + 65, listW = winW - 34;
        int listH = Math.max(0, winH - 100), contentH = choices.size() * 23;
        scroll.setMax(contentH - listH);
        int y = listY - scroll.update(frameDelta);
        pushClip(g, listX, listY, listW, listH);
        for (Choice choice : choices) {
            if (y + 20 >= listY && y < listY + listH) {
                long count = WaypointStore.all().stream().filter(w -> WaypointGroups.matches(w, choice.value)).count();
                boolean active = Objects.equals(choice.value, selected)
                        || (choice.value != null && selected != null && choice.value.equalsIgnoreCase(selected));
                button(g, mouseX, mouseY, listX, y, listW, 20, choice.label + " · " + count,
                        active ? Style.PRIMARY : Style.SECONDARY, () -> choose(choice.value));
            }
            y += 23;
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 14, listY, listH, contentH);
        int footer = winY + winH - 26;
        button(g, mouseX, mouseY, winX + 14, footer, 74, 18, "Abbrechen", Style.SECONDARY, this::onClose);
        if (!filtering && !WaypointGroups.normalize(query).isEmpty()) {
            button(g, mouseX, mouseY, winX + 96, footer, winW - 110, 18, "Namen verwenden", Style.PRIMARY, this::chooseTyped);
        }
    }

    private void chooseTyped() {
        String name = WaypointGroups.canonical(query, WaypointStore.all());
        if (!name.isEmpty()) choose(name);
    }

    private void choose(String group) {
        onSelect.accept(group);
        onClose();
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!filtering && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            chooseTyped();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scroll(scrollY);
        return true;
    }
}
