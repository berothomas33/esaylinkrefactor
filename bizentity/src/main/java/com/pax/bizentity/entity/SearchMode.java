package com.pax.bizentity.entity;

public class SearchMode {
    public static final byte SWIPE = 0x01;
    public static final byte INSERT = 0x02;
    public static final byte INTERNAL_WAVE = 0x04;
    public static final byte EXTERNAL_WAVE = 0x08;
    public static final byte WAVE = 0xC;
    public static final byte KEYIN = 0x10;
    public static final byte QR = 0x20;

    private SearchMode() {

    }

    public static boolean contain(byte mode, byte mode2) {
        return (mode & mode2) == mode2;
    }

    public static boolean isWave(byte mode) {
        return ((mode & INTERNAL_WAVE) == INTERNAL_WAVE) || ((mode & EXTERNAL_WAVE) == EXTERNAL_WAVE);
    }

    public static boolean isSupportMag(byte readType) {
        return (readType & SWIPE) == SWIPE;
    }
    public static boolean isSupportIcc(byte readType){
        return (readType & INSERT) == INSERT;
    }
    public static boolean isSupportInternalPicc(byte readType){
        return (readType & INTERNAL_WAVE) == INTERNAL_WAVE;
    }
    public static boolean isSupportExternalPicc(byte readType){
        return (readType & EXTERNAL_WAVE) == EXTERNAL_WAVE;
    }

    public static boolean isSupportKeyIn(byte readType) {
        return (readType & KEYIN) == KEYIN;
    }

    public static boolean isSupportQR(byte readType) {
        return (readType & QR) == QR;
    }
}
