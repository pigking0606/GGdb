package com.ggking.mydb.backend.common;

public class SubArray {
    public byte[] raw;
    public int start;
    public int end;

    public SubArray(byte[] raw,int start,int end){
        this.end = end;
        this.start = start;
        this.raw = raw;
    }
}
