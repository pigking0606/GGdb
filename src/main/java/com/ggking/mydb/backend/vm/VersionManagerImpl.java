package com.ggking.mydb.backend.vm;

import com.ggking.mydb.backend.common.AbstractCache;

public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager{
    @Override
    public byte[] read(long xid, long uid) throws Exception {
        return new byte[0];
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        return 0;
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
        return false;
    }

    @Override
    public long begin(int level) {
        return 0;
    }

    @Override
    public void commit(long xid) throws Exception {

    }

    @Override
    public void abort(long xid) {

    }

    @Override
    protected Entry getForCache(long uid) throws Exception {
        return null;
    }

    @Override
    protected void releaseForCache(Entry entry) {

    }
}
