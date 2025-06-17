package com.ggking.mydb.backend.vm;

import com.ggking.mydb.backend.common.SubArray;
import com.ggking.mydb.backend.dm.dataItem.DataItem;
import com.ggking.mydb.backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.util.Arrays;


public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN + 8;
    private static final int OF_DATA = OF_XMAX + 8;

    private long uid;
    private DataItem dataItem;
    private VersionManager vm;

    public static Entry loadEntry(VersionManager vm,long uid)throws Exception{
        DataItem di = (VersionManagerImpl)vm.dm.read(uid);
        return newEntry(vm,di,uid);
    }

    public static Entry newEntry(VersionManager vm, DataItem di, long uid) {
        if (di == null){return null;}
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = di;
        entry.vm = vm;
        return entry;
    }
    public static byte[] wrapEntryRaw(long xid,byte[] data){
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8];
        return Bytes.concat(xmin,xmax,data);
    }

    public byte[] data(){
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw,sa.start + OF_DATA,data,0,data.length);
            return data;
        }finally {
            dataItem.rUnLock();
        }
    }

    public void setXmax(long xid){
        dataItem.before();
        try {
            SubArray sa = dataItem.data();
            System.arraycopy(Parser.long2Byte(xid),0,sa.raw,sa.start + OF_XMAX, 8);
        }finally {
            dataItem.after(xid);
        }
    }

    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMIN, sa.start+OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMAX, sa.start+OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    public  void remove(){
        dataItem.release();
    }
}
