package com.ggking.mydb.backend.dm.dataItem;

import com.ggking.mydb.backend.common.SubArray;
import com.ggking.mydb.backend.dm.DataManagerImpl;
import com.ggking.mydb.backend.dm.page.Page;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataItemImpl implements DataItem{

    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;

    private SubArray raw;
    private byte[] oldRaw;
    private DataManagerImpl dm;
    private long uid;
    private Page pg;
    private Lock rLock;
    private Lock wLock;

    public DataItemImpl(SubArray raw,byte[] oldRaw,Page pg,long uid,DataManagerImpl dm){
    this.dm = dm;
    this.raw = raw;
    this.oldRaw = oldRaw;
    this.pg = pg;
    this.uid = uid;
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
    }

    public boolean isValid(){return raw.raw[raw.start + OF_VALID] == (byte) 0;}
    @Override
    public SubArray data() {
        return new SubArray(raw.raw,raw.start + OF_DATA,raw.end);
    }

    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw,raw.start,oldRaw,0,oldRaw.length);
    }

    @Override
    public void unBefore() {
        System.arraycopy(oldRaw,0,raw.raw,raw.start,oldRaw.length);
        wLock.unlock();
    }

    @Override
    public void after(long xid) {
        dm.logDataItem(xid,this);
        wLock.unlock();
    }

    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unlock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void rUnLock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return pg;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    @Override
    public SubArray getRaw() {
        return raw;
    }
}
