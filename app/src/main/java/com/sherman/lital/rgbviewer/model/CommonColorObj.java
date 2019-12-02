package com.sherman.lital.rgbviewer.model;

public class CommonColorObj {

    private int r;
    private int g;
    private int b;
    private String hexColor;
    private float percentage;

    public CommonColorObj() {
    }

    public int getR() {
        return r;
    }

    public void setR(int r) {
        this.r = r;
    }

    public int getG() {
        return g;
    }

    public void setG(int g) {
        this.g = g;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }

    public String getHexColor() {
        return hexColor;
    }

    public void setHexColor(int decimalColor) {
        this.hexColor = String.format("#%06X", (0xFFFFFF & decimalColor));
    }

    public float getPercentage() {
        return percentage;
    }

    public void setPercentage(int appearances, int total) {
        this.percentage = ((float)appearances / (float)total);
    }

    private int[] getRGB(final String hexColor) {
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            rgb[i] = Integer.parseInt(hexColor.substring(i * 2, i * 2 + 2), 16);
        }
        return rgb;
    }

    public void setCommonColorObj(int decimalColor, int appearances, int total) {
        hexColor = Integer.toHexString(decimalColor);//String.format("#%06X", (0xFFFFFF & decimalColor));
        while (hexColor.length() < 6) {
            hexColor = "0" + hexColor;
        }
        int [] rgb = getRGB(hexColor);
        setR(rgb[0]);
        setG(rgb[1]);
        setB(rgb[2]);
        setPercentage(appearances, total);
    }
}
