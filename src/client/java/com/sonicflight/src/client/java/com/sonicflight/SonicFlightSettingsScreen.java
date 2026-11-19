package com.sonicflight;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

public class SonicFlightSettingsScreen extends Screen {
    public SonicFlightSettingsScreen() {
        super(Component.translatable("screen.sonicflight.settings"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 40;
        this.addRenderableWidget(new VolumeSlider(cx - 100, y, 200, 20,
                "Sonic Boom Volume", SonicFlightConfig.boom, v -> SonicFlightConfig.boom = (float) v));
        this.addRenderableWidget(new VolumeSlider(cx - 100, y + 30, 200, 20,
                "Water Volume", SonicFlightConfig.water, v -> SonicFlightConfig.water = (float) v));
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(cx - 100, y + 70, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = this.title.getString();
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2,
                this.height / 2 - 70, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        SonicFlightConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static class VolumeSlider extends AbstractSliderButton {
        private final String label;
        private final DoubleConsumer setter;

        VolumeSlider(int x, int y, int w, int h, String label, double value, DoubleConsumer setter) {
            super(x, y, w, h, Component.empty(), value);
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + Math.round(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            setter.accept(this.value);
        }
    }
}
