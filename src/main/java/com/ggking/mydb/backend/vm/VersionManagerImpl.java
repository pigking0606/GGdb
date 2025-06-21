package com.ggking.mydb.backend.vm;


import com.ggking.mydb.backend.common.AbstractCache;
import com.ggking.mydb.backend.dm.DataManager;
import com.ggking.mydb.backend.tm.TransactionManager;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.common.Error;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager{

    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransaction;
    Lock lock;
    LockTable lt;

    public VersionManagerImpl(TransactionManager tm, DataManager dm){
        super(0);
        this.tm = tm;
    this.dm = dm;
    activeTransaction = new HashMap<>();
    lock = new ReentrantLock();
    lt = new LockTable();
    }


    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();
        if (t.err != null){throw t.err;}
        Entry entry = null;
        try{
            entry = super.get(uid);
        }catch (Exception e){
            if (e == Error.NullEntryException){
                return null;
            }else {
                throw  e;
            }
        }
        try {
            if (!Visibility.isVisible(tm,t,entry)){
                return null;
            }else {
                return entry.data();
            }
        }finally {
            entry.release();
        }
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {

        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();
        if (t.err != null){
            throw t.err;
        }

        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid,raw);
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
       lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();
        if (t.err != null){throw t.err;}
        Entry entry = null;
        try {
            entry = super.get(uid);
        }catch (Exception e){
            if (entry == null){
                return false;
            } else {
                throw e;
            }
        }
        try {
            if (!Visibility.isVisible(tm, t, entry)) {
                return false;
            }
            Lock l = null;
            try {
                l = lt.add(xid, uid);
            } catch (Exception e) {
                t.err = Error.ConcurrentUpdateException;
                t.autoAborted = true;
                throw t.err;
            }

            if (l != null) {
                l.lock();
                l.unlock();
            }

            if (entry.getXmax() == xid) {
                return false;
            }
            if (Visibility.isVersionSkip(tm, t, entry)) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid,true);
                t.autoAborted = true;
                throw t.err;
            }
            entry.setXmax(xid);
            return true;
        }finally {
        entry.release();}
    }

    @Override
    public long begin(int level) {
        lock.lock();
        try {
            long xid = tm.begin();
            Transaction t = Transaction.newTransaction(xid,level,activeTransaction);
            activeTransaction.put(xid,t);
            return xid;
        }finally {
            lock.unlock();
        }

    }

    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();
        try{
            if (t.err != null){
                throw t.err;
            }
        }catch (NullPointerException e){
            System.out.println(xid);
            System.out.println(activeTransaction.keySet());
            Panic.panic(e);
        }
        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();
        lt.remove(xid);
        tm.commit(xid);
    }

    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        if (!autoAborted){
            activeTransaction.remove(xid);
        }
        lock.unlock();
        if (t.autoAborted) return;
        lt.remove(xid);
        tm.abort(xid);
    }

    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if(entry == null) {
            throw Error.NullEntryException;
        }
        return entry;
    }

    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }

    public void releaseEntry(Entry entry){
        super.release(entry.getUid());
    }
}
