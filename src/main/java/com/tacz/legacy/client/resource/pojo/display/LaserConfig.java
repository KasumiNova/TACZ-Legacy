package com.tacz.legacy.client.resource.pojo.display;

import com.google.gson.annotations.SerializedName;

/**
 * Structured laser-beam display config parsed from gun-pack display JSON.
 *
 * Port of upstream TACZ LaserConfig, adapted for Legacy's 1.12.2 runtime.
 */
public class LaserConfig {
    private Integer defaultColor;

    @SerializedName("default_color")
    private String color = "#FF0000";

    @SerializedName("can_edit")
    private boolean canEdit = true;

    @SerializedName("length")
    private int length = 25;

    @SerializedName("width")
    private float width = 0.008f;

    @SerializedName("third_person_length")
    private float thirdPersonLength = 2f;

    @SerializedName("third_person_width")
    private float thirdPersonWidth = 0.008f;

    public int getDefaultColor() {
        if (defaultColor == null) {
            String rawColor = color == null ? "" : color.trim();
            if (rawColor.isEmpty()) {
                defaultColor = 0xFF0000;
            } else {
                try {
                    defaultColor = Integer.decode(rawColor) & 0xFFFFFF;
                } catch (NumberFormatException e) {
                    defaultColor = 0xFFFFFF;
                }
            }
        }
        return defaultColor;
    }

    public boolean canEdit() {
        return canEdit;
    }

    public int getLength() {
        return length;
    }

    public float getWidth() {
        return width;
    }

    public float getLengthThird() {
        return thirdPersonLength;
    }

    public float getWidthThird() {
        return thirdPersonWidth;
    }
}