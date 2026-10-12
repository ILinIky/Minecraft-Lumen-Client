package com.example.examplemod.gui;

import com.example.examplemod.ClientKeybinds;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Hauptmenue (Taste X): Kategorien links, Module als Karten mit Schalter und aufklappbaren Einstellungen. */
public class ClickGuiScreen extends ClientScreen {
    private static final int SIDEBAR_W = 108;
    private static final int HEADER_H = 30;
    private static final int CARD_H = 32;
    private static final int ROW_BOOL = 18, ROW_SLIDER = 26, ROW_MODE = 18, ROW_ACTION = 22, ROW_KEY = 18;

    // Bleibt zwischen dem Öffnen erhalten, wie bei bekannten Clients.
    private static Category selectedCategory = Category.RENDER;
    private static final Set<String> EXPANDED = new HashSet<>();
    private static String searchText = "";
    private static final String VERSION = ModList.get().getModContainerById(ExampleMod.MODID)
            .map(container -> container.getModInfo().getVersion().toString()).orElse("?");

    /** Aus "1.21.7_v12" wird "12"; ältere Versionsangaben bleiben unverändert. */
    private static String buildNumber() {
        int index = VERSION.lastIndexOf("_v");
        return index < 0 ? VERSION : VERSION.substring(index + 2);
    }

    private EditBox search;
    private final Scroll scroll = new Scroll();
    private Module binding;
    private Setting.Slider dragging;
    private int dragTrackX, dragTrackW;

    public ClickGuiScreen(Screen parent) {
        super(Component.literal("Client-Menü"), parent);
    }

    @Override protected int preferredWidth() { return 480; }
    @Override protected int preferredHeight() { return 324; }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(150, winW / 3);
        search = borderlessField(winX + winW - fieldW - 2, winY + 11, fieldW - 18, "Module suchen...", 40);
        search.setValue(searchText);
        search.setResponder(text -> {
            searchText = text;
            scroll.reset();
        });
        addRenderableWidget(search);
    }

    private boolean searching() { return !searchText.isBlank(); }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawLogo(g, winX + 14, winY + 11);
        int fx = search.getX() - 18, fy = winY + 7;
        UiRender.panel(g, fx, fy, search.getWidth() + 24, 16, 4, Theme.FIELD, search.isFocused() ? Theme.ACCENT : Theme.FIELD_BORDER);
        g.drawString(font, "⌕", fx + 6, fy + 4, Theme.TEXT_MUTED, false);
        g.fill(winX + 1, winY + HEADER_H, winX + winW - 1, winY + HEADER_H + 1, Theme.DIVIDER);

        renderSidebar(g, mouseX, mouseY);
        renderModules(g, mouseX, mouseY);
    }

    private void drawLogo(GuiGraphics g, int x, int y) {
        String name = Theme.CLIENT_NAME;
        int cursor = x;
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            g.drawString(font, ch, cursor, y, Theme.accent(i / (float) Math.max(1, name.length() - 1)), false);
            cursor += font.width(ch);
        }
        UiRender.scaledText(g, font, Theme.CLIENT_TAG, cursor + 4, y + 2, Theme.TEXT_MUTED, 0.75F);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int sx = winX + 1, sy = winY + HEADER_H + 1, sh = winH - HEADER_H - 2;
        g.fill(sx, sy, sx + SIDEBAR_W, sy + sh - 6, Theme.SIDEBAR);
        UiRender.roundRect(g, sx, sy + sh - 12, SIDEBAR_W, 12, 5, Theme.SIDEBAR);
        g.fill(sx + SIDEBAR_W - 6, sy + sh - 12, sx + SIDEBAR_W, sy + sh, Theme.SIDEBAR);
        g.fill(sx + SIDEBAR_W, sy, sx + SIDEBAR_W + 1, sy + sh, Theme.DIVIDER);

        UiRender.scaledText(g, font, "KATEGORIEN", sx + 12, sy + 9, Theme.TEXT_MUTED, 0.75F);
        int y = sy + 22;
        for (Category category : Category.values()) {
            int ix = sx + 6, iw = SIDEBAR_W - 12, ih = 20;
            boolean selected = category == selectedCategory && !searching();
            boolean hover = hovered(mouseX, mouseY, ix, y, iw, ih);
            if (selected) {
                UiRender.roundRect(g, ix, y, iw, ih, 4, Theme.ACCENT_SOFT);
                UiRender.roundRect(g, ix, y + 5, 2, ih - 10, 1, Theme.ACCENT);
            } else if (hover) {
                UiRender.roundRect(g, ix, y, iw, ih, 4, Theme.CARD);
            }
            g.drawString(font, category.icon, ix + 8, y + 6, selected ? Theme.ACCENT_2 : Theme.TEXT_MUTED, false);
            g.drawString(font, category.title, ix + 22, y + 6, selected || hover ? Theme.TEXT : Theme.TEXT_DIM, false);

            long active = ModuleManager.byCategory(category).stream().filter(Module::isEnabled).count();
            if (active > 0) {
                String count = String.valueOf(active);
                int pw = font.width(count) + 7;
                UiRender.roundRect(g, ix + iw - pw - 4, y + 5, pw, 10, 5, selected ? Theme.ACCENT : Theme.SWITCH_OFF);
                g.drawString(font, count, ix + iw - pw, y + 6, selected ? Theme.ON_ACCENT : Theme.TEXT, false);
            }
            addHit(ix, y, iw, ih, (mx, my, b) -> {
                selectedCategory = category;
                search.setValue("");
                scroll.reset();
            });
            y += 22;
        }

        // Werkzeuge mit eigenem Fenster; nur zeigen, wenn sie nicht in die Kategorien ragen.
        int toolsY = sy + sh - 110;
        if (toolsY >= y + 4) UiRender.scaledText(g, font, "WERKZEUGE", sx + 12, toolsY + 4, Theme.TEXT_MUTED, 0.75F);
        BlueprintPlan plan = ModuleManager.BLUEPRINTS == null ? null : ModuleManager.BLUEPRINTS.plan();
        String blueprints = "⌂ Baupläne";
        if (plan != null) {
            int total = Math.max(1, plan.blueprint.cells().size());
            blueprints += " · " + Math.round(100F * plan.count(BlueprintPlan.Status.CORRECT) / total) + "%";
        }
        button(g, mouseX, mouseY, sx + 6, sy + sh - 88, SIDEBAR_W - 12, 18, blueprints,
                plan != null ? Style.PRIMARY : Style.SECONDARY, () -> minecraft.setScreen(new BlueprintScreen(this)));
        button(g, mouseX, mouseY, sx + 6, sy + sh - 66, SIDEBAR_W - 12, 18, "✦ Wegpunkte", Style.SECONDARY,
                () -> minecraft.setScreen(new WaypointScreen(this)));
        button(g, mouseX, mouseY, sx + 6, sy + sh - 44, SIDEBAR_W - 12, 18, "☰ Profile", Style.SECONDARY,
                () -> minecraft.setScreen(new ProfileScreen(this)));
        UiRender.scaledText(g, font, "Forge 1.21.7 · v" + buildNumber(), sx + 10, sy + sh - 18, Theme.TEXT_MUTED, 0.75F);
    }

    private void renderModules(GuiGraphics g, int mouseX, int mouseY) {
        int cx = winX + SIDEBAR_W + 13;
        int cw = winW - SIDEBAR_W - 26;
        int top = winY + HEADER_H + 9;

        List<Module> modules = visibleModules();
        String heading = searching() ? "Suchergebnisse" : selectedCategory.title;
        long enabled = modules.stream().filter(Module::isEnabled).count();
        String info = searching() ? modules.size() + " Treffer" : enabled + " von " + modules.size() + " aktiv";
        g.drawString(font, heading, cx, top + 2, Theme.TEXT, false);
        g.drawString(font, info, cx + cw - font.width(info), top + 2, Theme.TEXT_MUTED, false);

        int listY = top + 16;
        int listH = winY + winH - 8 - listY;
        int cardW = cw - 7;
        int offset = scroll.update(frameDelta);

        pushClip(g, cx, listY, cw, listH);
        int y = listY - offset;
        for (Module module : modules) {
            y += renderCard(g, module, cx, y, cardW, listY, listH, mouseX, mouseY) + 6;
        }
        popClip(g);

        int contentHeight = y + offset - listY - 6;
        scroll.setMax(contentHeight - listH);
        scrollbar(g, scroll, cx + cw - 3, listY, listH, contentHeight);

        if (modules.isEmpty()) {
            String empty = "Keine Module gefunden";
            g.drawString(font, empty, cx + (cw - font.width(empty)) / 2, listY + 30, Theme.TEXT_MUTED, false);
        }
    }

    private List<Module> visibleModules() {
        if (!searching()) return ModuleManager.byCategory(selectedCategory);
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        List<Module> result = new ArrayList<>();
        for (Module module : ModuleManager.all()) {
            if (module.getName().toLowerCase(Locale.ROOT).contains(query)
                    || module.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(module);
            }
        }
        return result;
    }

    private int renderCard(GuiGraphics g, Module module, int x, int y, int w, int listY, int listH, int mouseX, int mouseY) {
        boolean expanded = EXPANDED.contains(module.getId());
        module.toggleAnimation = UiRender.approach(module.toggleAnimation, module.isEnabled() ? 1 : 0, frameDelta, 6);
        module.expandAnimation = UiRender.approach(module.expandAnimation, expanded ? 1 : 0, frameDelta, 5);
        int extra = Math.round(settingsHeight(module) * UiRender.ease(module.expandAnimation));
        int h = CARD_H + extra;
        if (y + h < listY || y > listY + listH) return h;

        boolean hoverHeader = hovered(mouseX, mouseY, x, y, w, CARD_H);
        UiRender.roundRect(g, x, y, w, h, 5, hoverHeader ? Theme.CARD_HOVER : Theme.CARD);
        if (module.toggleAnimation > 0.01F) {
            UiRender.roundRect(g, x, y + 7, 2, CARD_H - 14, 1, Theme.alpha(Theme.ACCENT, module.toggleAnimation));
        }

        g.drawString(font, module.getName(), x + 12, y + 7, Theme.TEXT, false);
        g.drawString(font, UiRender.ellipsize(font, module.getDescription(), w - 120), x + 12, y + 18, Theme.TEXT_MUTED, false);

        boolean toggleable = module.hasToggle();
        int switchX = x + w - 32, switchY = y + 11;
        boolean switchHover = toggleable && hovered(mouseX, mouseY, switchX - 3, switchY - 3, 26, 16);
        if (toggleable) UiRender.toggle(g, switchX, switchY, module.toggleAnimation, switchHover);

        int arrowX = toggleable ? switchX - 16 : x + w - 20;
        boolean arrowHover = hovered(mouseX, mouseY, arrowX - 4, y + 6, 14, 20);
        g.drawString(font, expanded ? "▾" : "▸", arrowX, y + 12, arrowHover || expanded ? Theme.TEXT : Theme.TEXT_MUTED, false);

        if (module.getKey() != GLFW.GLFW_KEY_UNKNOWN) {
            String key = keyName(module.getKey());
            int chipW = font.width(key) + 8;
            int chipX = arrowX - 8 - chipW;
            UiRender.panel(g, chipX, y + 10, chipW, 12, 3, Theme.FIELD, Theme.FIELD_BORDER);
            g.drawString(font, key, chipX + 4, y + 12, Theme.TEXT_DIM, false);
        }

        addHit(x, y, w, CARD_H, (mx, my, button) -> {
            if (button == 1 || !toggleable) toggleExpanded(module);
            else if (button == 0) module.toggle();
        });
        addHit(arrowX - 4, y + 6, 14, 20, (mx, my, button) -> toggleExpanded(module));
        if (toggleable) addHit(switchX - 3, switchY - 3, 26, 16, (mx, my, button) -> module.toggle());

        if (extra > 0) {
            g.fill(x + 10, y + CARD_H - 1, x + w - 10, y + CARD_H, Theme.DIVIDER);
            pushClip(g, x, y + CARD_H, w, extra);
            renderSettings(g, module, x + 12, y + CARD_H + 2, w - 24, mouseX, mouseY);
            popClip(g);
        }
        return h;
    }

    private void toggleExpanded(Module module) {
        if (!EXPANDED.remove(module.getId())) EXPANDED.add(module.getId());
    }

    private int settingsHeight(Module module) {
        int height = 8 + (module.hasToggle() ? ROW_KEY : 0);
        for (Setting setting : module.getSettings()) {
            if (!setting.isVisible()) continue;
            if (setting instanceof Setting.Bool) height += ROW_BOOL;
            else if (setting instanceof Setting.Slider) height += ROW_SLIDER;
            else if (setting instanceof Setting.Mode) height += ROW_MODE;
            else if (setting instanceof Setting.Action) height += ROW_ACTION;
        }
        return height;
    }

    private void renderSettings(GuiGraphics g, Module module, int x, int y, int w, int mouseX, int mouseY) {
        int ry = y + 4;
        for (Setting setting : module.getSettings()) {
            if (!setting.isVisible()) continue;
            if (setting instanceof Setting.Bool bool) {
                boolean hover = hovered(mouseX, mouseY, x, ry, w, ROW_BOOL);
                g.drawString(font, bool.getName(), x, ry + 5, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
                UiRender.toggle(g, x + w - 20, ry + 4, bool.get() ? 1 : 0, hover);
                addHit(x, ry, w, ROW_BOOL, (mx, my, b) -> bool.toggle());
                tooltip(g, setting, hover, mouseX, mouseY);
                ry += ROW_BOOL;
            } else if (setting instanceof Setting.Slider slider) {
                boolean hover = hovered(mouseX, mouseY, x, ry, w, ROW_SLIDER) || dragging == slider;
                String value = slider.format();
                g.drawString(font, slider.getName(), x, ry + 3, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
                g.drawString(font, value, x + w - font.width(value), ry + 3, Theme.TEXT, false);
                int trackY = ry + 16;
                UiRender.roundRect(g, x, trackY, w, 4, 2, Theme.SWITCH_OFF);
                int filled = Math.round(w * (float) slider.getProgress());
                if (filled > 0) UiRender.gradientH(g, x, trackY, filled, 4, Theme.ACCENT, Theme.ACCENT_2);
                int knobX = Math.max(x, Math.min(x + w - 8, x + filled - 4));
                UiRender.roundRect(g, knobX, trackY - 2, 8, 8, 4, Theme.KNOB);
                int trackX = x;
                addHit(x, ry + 10, w, 14, (mx, my, b) -> {
                    dragging = slider;
                    dragTrackX = trackX;
                    dragTrackW = w;
                    updateDrag(mx);
                });
                tooltip(g, setting, hovered(mouseX, mouseY, x, ry, w, 10), mouseX, mouseY);
                ry += ROW_SLIDER;
            } else if (setting instanceof Setting.Mode mode) {
                boolean hover = hovered(mouseX, mouseY, x, ry, w, ROW_MODE);
                g.drawString(font, mode.getName(), x, ry + 5, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
                String chip = "‹  " + mode.get() + "  ›";
                int chipW = font.width(chip) + 10;
                UiRender.panel(g, x + w - chipW, ry + 2, chipW, 14, 3, hover ? Theme.CARD_HOVER : Theme.FIELD, Theme.FIELD_BORDER);
                g.drawString(font, chip, x + w - chipW + 5, ry + 5, Theme.TEXT, false);
                addHit(x, ry, w, ROW_MODE, (mx, my, b) -> mode.cycle(b == 1 ? -1 : 1));
                tooltip(g, setting, hover, mouseX, mouseY);
                ry += ROW_MODE;
            } else if (setting instanceof Setting.Action action) {
                button(g, mouseX, mouseY, x, ry + 2, w, 16, action.getName() + "  →", Style.SECONDARY, action::run);
                tooltip(g, setting, hovered(mouseX, mouseY, x, ry + 2, w, 16), mouseX, mouseY);
                ry += ROW_ACTION;
            }
        }

        if (!module.hasToggle()) return;
        boolean listening = binding == module;
        g.drawString(font, module.keyLabel(), x, ry + 5, Theme.TEXT_DIM, false);
        String keyLabel = listening ? "Taste drücken..." : module.getKey() == GLFW.GLFW_KEY_UNKNOWN ? "Keine" : keyName(module.getKey());
        int chipW = font.width(keyLabel) + 12;
        int chipX = x + w - chipW;
        int border = listening ? Theme.accentWave(0) : Theme.FIELD_BORDER;
        UiRender.panel(g, chipX, ry + 2, chipW, 14, 3, Theme.FIELD, border);
        g.drawString(font, keyLabel, chipX + 6, ry + 5, listening ? Theme.TEXT : Theme.TEXT_DIM, false);
        addHit(chipX, ry + 2, chipW, 14, (mx, my, b) -> {
            if (b == 1) {
                module.setKey(GLFW.GLFW_KEY_UNKNOWN);
                binding = null;
            } else {
                binding = module;
            }
        });
        if (hovered(mouseX, mouseY, chipX, ry + 2, chipW, 14) && !listening) {
            g.setTooltipForNextFrame(font, Component.literal("Linksklick: Taste festlegen · Rechtsklick: entfernen"), mouseX, mouseY);
        }
    }

    private void tooltip(GuiGraphics g, Setting setting, boolean hover, int mouseX, int mouseY) {
        if (hover && dragging == null && !setting.getDescription().isEmpty()) {
            g.setTooltipForNextFrame(font, Component.literal(setting.getDescription()), mouseX, mouseY);
        }
    }

    private static String keyName(int key) {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase(Locale.ROOT);
    }

    private void updateDrag(double mouseX) {
        if (dragging == null || dragTrackW <= 0) return;
        dragging.setProgress(Math.max(0, Math.min(1, (mouseX - dragTrackX) / dragTrackW)));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            updateDrag(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scroll(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (binding != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                binding.setKey(GLFW.GLFW_KEY_UNKNOWN);
            } else if (key != GLFW.GLFW_KEY_ESCAPE) {
                binding.setKey(key);
                Notifications.info(binding.getName(), "Taste: " + keyName(key));
            }
            binding = null;
            return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (ClientKeybinds.MENU != null && ClientKeybinds.MENU.matches(key, scanCode)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) {
            setFocused(search);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Einfach lostippen startet die Suche.
        if (binding == null && !search.isFocused() && Character.isLetterOrDigit(codePoint)) {
            setFocused(search);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        ModuleManager.saveNow();
        super.onClose();
    }
}
