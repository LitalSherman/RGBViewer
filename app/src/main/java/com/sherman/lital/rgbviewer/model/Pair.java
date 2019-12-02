package com.sherman.lital.rgbviewer.model;

public class Pair {

    private int[] keys;
    private int[] values;

    public Pair() {
    }

    public int[] getKeys() {
        return keys;
    }

    public int[] getValues() {
        return values;
    }

    public void setPair(int[] keys, int[] values) {
        this.keys = keys;
        this.values = values;
    }
}
