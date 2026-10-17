package com.example.colormons;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Layout is computed from fixed panel boxes first, then buttons are placed inside them,
 * so nothing is drawn under a button and no coordinate drifts between draws and clicks.
 */
public class ColorWheelScreen extends Screen {
    private static final int WHEEL = 150;

    // left column
    private static final int LEFT_X = 16, LEFT_W = 168;
    // right column
    private static final int RIGHT_W = 176;
    // middle
    private static final int MID_W = 190;

    private final UUID monId;
    private final String species;
    private final PokemonEntity entity;
    private final MonColors mc;
    private final List<int[]> history = new ArrayList<>();

    private int selected = 0;
    private float brightness = 1f;
    private boolean editing = false;
    private float spin = 0f;
    private int hovered = -1;

    // computed in init()
    private int wheelX, wheelY;
    private int sizeX, sizeY;
    private int leftX, leftY;
    private int mapX, mapY, mapSize;
    private int paintX, paintY;
    private int rowY, finishY;

    public ColorWheelScreen(PokemonEntity mon) {
        super(Component.literal(mon.getPokemon().getSpecies().getName() + " Colours"));
        this.entity = mon;
        this.monId = mon.getPokemon().getUuid();
        this.species = mon.getPokemon().getSpecies().getResourceIdentifier().getPath();
        this.mc = ColorStore.of(monId);
        for (int i = 0; i < MonColors.GROUPS; i++) {
            if (mc.colors[i] >= 0) { selected = i; break; }
        }
        PaintPanel.load(species);
    }

    private void snapshot() {
        history.add(mc.colors.clone());
        if (history.size() > 20) history.remove(0);
    }

    @Override
    protected void init() {
        // --- left column: preview on top, paint grid below when editing ---
        leftX = LEFT_X;
        leftY = 46;

        // --- right column: group slots on top, group map under them ---
        int rightX = width - RIGHT_W - 16;
        int rightY = 46;

        // --- middle: wheel ---
        wheelX = leftX + LEFT_W + 44;
        wheelY = height / 2 - WHEEL / 2 - 10;

        // --- bottom rows ---
        rowY = height - 58;
        finishY = height - 32;

        // group buttons on the right
        for (int i = 0; i < MonColors.GROUPS; i++) {
            final int g = i;
            addRenderableWidget(Button.builder(
                    Component.literal(Palette.GROUP_NAMES[i] + "  x" + GroupMaps.count(species, g)),
                    b -> selected = g)
                    .bounds(rightX, rightY + i * 24, 120, 20).build());
        }
        sizeX = rightX + 124;
        sizeY = rightY;

        addRenderableWidget(Button.builder(Component.literal(editing ? "Edit: ON" : "Edit"), b -> {
            editing = !editing;
            b.setMessage(Component.literal(editing ? "Edit: ON" : "Edit"));
        }).bounds(rightX, rightY + MonColors.GROUPS * 24 + 6, 90, 20).build());

        // group map sits under the sliders, inside the right column
        mapX = rightX + 46;
        mapY = rightY + MonColors.GROUPS * 24 + 32;
        mapSize = RIGHT_W - 60;

        // finishes
        for (int i = 0; i < 4; i++) {
            final int f = i;
            addRenderableWidget(Button.builder(Component.literal(Palette.FINISH_NAMES[i]), b -> {
                snapshot();
                mc.finish = f;
                ColorStore.touched(monId);
            }).bounds(leftX + i * 78, finishY, 74, 20).build());
        }

        // palette actions
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> snapshot())
                .bounds(leftX, rowY, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> {
            if (!history.isEmpty()) {
                System.arraycopy(history.remove(history.size() - 1), 0, mc.colors, 0, MonColors.GROUPS);
                ColorStore.touched(monId);
            }
        }).bounds(leftX + 78, rowY, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            snapshot();
            mc.reset();
            ColorStore.clear(monId);
        }).bounds(leftX + 156, rowY, 74, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(leftX + 234, rowY, 74, 20).build());

        // brush controls, in the right column beside the map
        addRenderableWidget(Button.builder(Component.literal("Brush " + (EditBrush.size + 1)), b -> {
            EditBrush.size = (EditBrush.size + 1) % 4;
            b.setMessage(Component.literal("Brush " + (EditBrush.size + 1)));
        }).bounds(rightX, mapY, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo brush"), b -> {
            EditBrush.undo(mc, (EditBrush.size + 1) * 9);
            ColorStore.touched(monId);
        }).bounds(rightX, mapY + 24, 84, 20).build());

        // four quick saves down the far right edge
        for (int i = 0; i < 4; i++) {
            final int s = i;
            addRenderableWidget(Button.builder(Component.literal("S" + (i + 1)), b -> {
                if (isSaved(s)) {
                    snapshot();
                    System.arraycopy(ColorStore.SAVED[s], 0, mc.colors, 0, MonColors.GROUPS);
                    ColorStore.touched(monId);
                } else if (!editing) {
                    System.arraycopy(mc.colors, 0, ColorStore.SAVED[s], 0, MonColors.GROUPS);
                    ColorStore.save();
                }
            }).bounds(width - 34, 16 + i * 22, 24, 18).build());
        }
    }

    private boolean isSaved(int slot) {
        for (int v : ColorStore.SAVED[slot]) if (v >= 0) return true;
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        // ---- left column: 3D preview of the real Pokemon ----
        g.fill(leftX - 2, leftY - 2, leftX + LEFT_W, leftY + LEFT_W + 2, 0xCC000000);
        Preview.draw(g, entity, leftX + LEFT_W / 2.0f, leftY + LEFT_W - 18.0f, 46.0f, spin);
        g.drawString(font, species, leftX + 4, leftY - 14, 0xAAAAAA);

        // ---- paint grid, inside the left column while editing ----
        if (editing) {
            paintX = leftX;
            paintY = leftY + LEFT_W + 22;
            g.fill(paintX - 2, paintY - 2, paintX + LEFT_W, paintY + LEFT_W + 2, 0xCC000000);
            PaintPanel.draw(g, mc, GroupMaps.groups(species), paintX, paintY, LEFT_W);
            g.drawString(font, "into " + Palette.GROUP_NAMES[selected], paintX, paintY - 12, 0x00FF00);
        }

        // ---- middle: wheel, brightness bar, saturation bar ----
        drawWheel(g);
        drawBar(g, wheelX + WHEEL + 8, wheelY, brightness, 0xFFFF0000, "v");
        drawBar(g, wheelX + WHEEL + 30, wheelY, saturation(), 0xFF00FF00, "s");

        // ---- right column: group sliders ----
        int rightX = width - RIGHT_W - 16;
        int rightY = 46;
        for (int i = 0; i < MonColors.GROUPS; i++) {
            int y = rightY + i * 24;
            if (i == selected) g.fill(rightX - 3, y - 2, rightX + 122, y + 22, 0x66FFFFFF);
            g.fill(sizeX, sizeY + i * 24, sizeX + 20, sizeY + i * 24 + 20, 0xFF000000 | Palette.shown(mc, i));
        }

        // ---- group map with the hovered group lit ----
        if (hovered >= 0) {
            g.fill(mapX - 3, mapY - 3, mapX + mapSize + 3, mapY + mapSize + 3, 0x66000000);
        }
        GroupOverlay.draw(g, species, mapX, mapY, mapSize, hovered);
        g.drawString(font, hovered >= 0 ? Palette.GROUP_NAMES[hovered] : "all groups",
                mapX, mapY - 12, hovered >= 0 ? 0xFFFF66 : 0x888888);

        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
    }

    private void drawWheel(GuiGraphics g) {
        NativeImage img = new NativeImage(WHEEL, WHEEL, true);
        float r = WHEEL / 2f;
        for (int y = 0; y < WHEEL; y++) for (int x = 0; x < WHEEL; x++) {
            float dx = x - r, dy = y - r, d = (float) Math.sqrt(dx * dx + dy * dy) / r;
            if (d > 1f) { img.setPixelRGBA(x, y, 0); continue; }
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            int rgb = Color.HSBtoRGB(hue, d, brightnessValue());
            int Rr = (rgb >> 16) & 0xFF, Gg = (rgb >> 8) & 0xFF, Bb = rgb & 0xFF;
            img.setPixelRGBA(x, y, 0xFF000000 | (Bb << 16) | (Gg << 8) | Rr);
        }
        ResourceLocation tex = minecraft.getTextureManager()
                .register("colormons/wheel", new DynamicTexture(img));
        g.blit(tex, wheelX, wheelY, 0, 0, WHEEL, WHEEL, WHEEL, WHEEL);
    }

    private float brightnessValue() { return brightness; }

    private void drawBar(GuiGraphics g, int x, int y, float value, int knobColour, String label) {
        for (int i = 0; i < WHEEL; i++) {
            int v = 255 - i * 255 / WHEEL;
            g.fill(x, y + i, x + 10, y + i + 1, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
        int knob = y + (int) ((1 - value) * WHEEL);
        g.fill(x - 3, knob - 1, x + 13, knob + 1, knobColour);
        g.drawString(font, label, x + 2, y - 12, knobColour);
    }

    private float saturation() {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        return hsb[1];
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (editing && PaintPanel.ready() && !isOverButton((int) mx, (int) my)) {
            int[] px = PaintPanel.pixelAt(mx, my, paintX, paintY, LEFT_W);
            if (px != null) {
                EditBrush.paint(mc, px[0], px[1], PaintPanel.texWidth(), PaintPanel.texHeight(), selected);
                ColorStore.touched(monId);
                return true;
            }
        }
        return pick(mx, my) || super.mouseClicked(mx, my, b);
    }

    private boolean isOverButton(int mx, int my) {
        for (var child : children()) {
            if (child instanceof Button btn && btn.isMouseOver(mx, my)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (editing && PaintPanel.ready() && !isOverButton((int) mx, (int) my)) {
            int[] px = PaintPanel.pixelAt(mx, my, paintX, paintY, LEFT_W);
            if (px != null) {
                EditBrush.paint(mc, px[0], px[1], PaintPanel.texWidth(), PaintPanel.texHeight(), selected);
                ColorStore.touched(monId);
                return true;
            }
        }
        return pick(mx, my) || super.mouseDragged(mx, my, b, dx, dy);
    }

    private boolean pick(double mx, double my) {
        float r = WHEEL / 2f;
        // brightness bar
        if (overBar(mx, my, wheelX + WHEEL + 8)) {
            brightness = 1f - (float) ((my - wheelY) / WHEEL);
            commit();
            return true;
        }
        // saturation bar
        if (overBar(mx, my, wheelX + WHEEL + 30)) {
            setSaturation(1f - (float) ((my - wheelY) / WHEEL));
            commit();
            return true;
        }
        // wheel
        double dx = mx - (wheelX + r), dy = my - (wheelY + r);
        double d = Math.sqrt(dx * dx + dy * dy) / r;
        if (d <= 1) {
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            mc.colors[selected] = Color.HSBtoRGB(hue, (float) d, brightness) & 0xFFFFFF;
            commit();
            return true;
        }
        return false;
    }

    private boolean overBar(double mx, double my, int x) {
        return mx >= x && mx <= x + 10 && my >= wheelY && my <= wheelY + WHEEL;
    }

    private void setSaturation(float sat) {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        mc.colors[selected] = Color.HSBtoRGB(hsb[0], Math.max(0f, Math.min(1f, sat)), brightness) & 0xFFFFFF;
    }

    private void commit() {
        TintCache.invalidate(monId);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        hovered = groupUnderMouse((int) mx, (int) my);
        super.mouseMoved(mx, my);
    }

    private int groupUnderMouse(int mx, int my) {
        int rightX = width - RIGHT_W - 16;
        int rightY = 46;
        for (int i = 0; i < MonColors.GROUPS; i++) {
            int y = rightY + i * 24;
            if (my >= y && my <= y + 20 && mx >= rightX && mx <= sizeX + 20) return i;
        }
        return -1;
    }

    @Override
    public void tick() {
        spin += 0.012f;
        super.tick();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        ColorStore.save();
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public void onClose() {
        ColorStore.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
