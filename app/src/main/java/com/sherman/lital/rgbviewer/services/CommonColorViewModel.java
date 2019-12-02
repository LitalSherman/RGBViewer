package com.sherman.lital.rgbviewer.services;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.media.Image;
import android.util.Log;

import com.sherman.lital.rgbviewer.model.Pair;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class CommonColorViewModel extends ViewModel {

    private int[] rgbBuffer;
    private Pair commonColorsKeyValue;
    private int totalSize;
    private MutableLiveData<Pair> commonColorsKeyValueLiveData;

    public LiveData<Pair> getColorsKeyValueLiveData(int totalSize, Image.Plane[] planes, int mHeight){
        if(commonColorsKeyValue == null){
            commonColorsKeyValueLiveData = new MutableLiveData<>();
            commonColorsKeyValue = new Pair();
        }
        getCommonColorKeyValues(totalSize, planes, mHeight);
        return commonColorsKeyValueLiveData;

    }


    public int[] getRGBIntFromPlanes(Image.Plane[] planes, int mHeight) {

        if (rgbBuffer == null || rgbBuffer.length < totalSize) {
            rgbBuffer = new int[totalSize];
        }
        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();
        ByteBuffer vPlane = planes[2].getBuffer();

        int bufferIndex = 0;
        final int total = yPlane.capacity();
        final int uvCapacity = uPlane.capacity();
        final int width = planes[0].getRowStride();

        int yPos = 0;
        for (int i = 0; i < mHeight; i++) {
            int uvPos = (i >> 1) * width;

            for (int j = 0; j < width; j++) {
                if (uvPos >= uvCapacity - 1)
                    break;
                if (yPos >= total)
                    break;

                final int y1 = yPlane.get(yPos++) & 0xff;

                final int u = (uPlane.get(uvPos) & 0xff) - 128;
                final int v = (vPlane.get(uvPos + 1) & 0xff) - 128;
                if ((j & 1) == 1) {
                    uvPos += 2;
                }
                final int y1192 = 1192 * y1;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
                g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
                b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);

                rgbBuffer[bufferIndex++] = ((r << 6) & 0xff0000) |
                        ((g >> 2) & 0xff00) |
                        ((b >> 10) & 0xff);
            }
        }
        return rgbBuffer;
    }

    // hash map format of key- rgb number, value- number of appearances
    private HashMap<Integer, Integer> rgbBufferHashMap() {
        HashMap<Integer, Integer> rgbBufferHashMap = new HashMap<>();

        for (int rgb : rgbBuffer) {
            if (!rgbBufferHashMap.containsKey(rgb)) {
                Log.e("rgbBuffer", String.valueOf(rgb));
                rgbBufferHashMap.put(rgb, 1);
            } else {
                int appearance = rgbBufferHashMap.get(rgb);
                rgbBufferHashMap.put(rgb, appearance + 1);
            }
        }
        return rgbBufferHashMap;
    }

    private void commonColorKeyValuesPair(HashMap<Integer, Integer> rgbBufferHashMap) {

        int[] commonKeys = {0, 0, 0, 0, 0}; // Most common [0], Second Most Common=[1]
        int[] commonValues = {0, 0, 0, 0, 0};

        for (Map.Entry<Integer, Integer> pair : rgbBufferHashMap.entrySet()) {
            int key = pair.getKey();
            int current = pair.getValue();

            if (current > commonValues[0]) {
                commonValues[4] = commonValues[3];
                commonValues[3] = commonValues[2];
                commonValues[2] = commonValues[1];
                commonValues[1] = commonValues[0];
                commonValues[0] = current;

                commonKeys[4] = commonKeys[3];
                commonKeys[3] = commonKeys[2];
                commonKeys[2] = commonKeys[1];
                commonKeys[1] = commonKeys[0];
                commonKeys[0] = key;
            } else if (current > commonValues[1]) {
                commonValues[4] = commonValues[3];
                commonValues[3] = commonValues[2];
                commonValues[2] = commonValues[1];
                commonValues[1] = current;

                commonKeys[4] = commonKeys[3];
                commonKeys[3] = commonKeys[2];
                commonKeys[2] = commonKeys[1];
                commonKeys[1] = key;
            } else if (current > commonValues[2]) {
                commonValues[4] = commonValues[3];
                commonValues[3] = commonValues[2];
                commonValues[2] = current;

                commonKeys[4] = commonKeys[3];
                commonKeys[3] = commonKeys[2];
                commonKeys[2] = key;
            } else if (current > commonValues[3]) {
                commonValues[4] = commonValues[3];
                commonValues[3] = current;

                commonKeys[4] = commonKeys[3];
                commonKeys[3] = key;
            } else if (current > commonValues[4]) {
                commonValues[4] = current;

                commonKeys[4] = key;
            }
        }

        commonColorsKeyValue.setPair(commonKeys, commonValues);
    }

    public Pair getCommonColorsKeyValue() {
        return commonColorsKeyValue;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public int getRgbBufferLength(){
        return rgbBuffer.length;
    }

    public void getCommonColorKeyValues(int totalSize, Image.Plane[] planes, int mHeight) {
        setTotalSize(totalSize);
        getRGBIntFromPlanes(planes, mHeight);
        commonColorKeyValuesPair(rgbBufferHashMap());
        commonColorsKeyValueLiveData.postValue(getCommonColorsKeyValue());
    }

}
