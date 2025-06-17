package com.ggking.mydb.backend.vm;

import com.ggking.mydb.backend.tm.TransactionManagerImpl;

import java.util.HashMap;
import java.util.Map;

public class Transaction {

    public long xid;
    public int level;
    public Map<Long,Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;

    public static Transaction newTransaction(long xid,int level,Map<Long,Transaction> active){
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        if (level != 0){
            t.snapshot = new HashMap<>();
            for (Long key : active.keySet()) {
                t.snapshot.put(key,true);
            }
        }
        return t;
    }

    public boolean idInSnapdhot(long xid){
        if (xid == TransactionManagerImpl.SUPER_XID){
            return false;
        }
        return snapshot.containsKey(xid);
    }
}
