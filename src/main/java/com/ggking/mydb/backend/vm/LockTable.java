package com.ggking.mydb.backend.vm;

import com.ggking.mydb.common.Error;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockTable {

    private Map<Long, List<Long>> x2u;
    private Map<Long,Long> u2x;
    private Map<Long,List<Long>> wait;
    private Map<Long, Lock> waitLock;
    private Map<Long,Long> waitU;
    private Lock lock;

    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    public Lock add(long xid,long uid) throws Exception{
        lock.lock();
        try{
            if (x2u.get(xid).contains(uid)){
                return null;
            }
            if (!u2x.containsKey(uid)){
                u2x.put(uid,xid);
                putIntoList(x2u,uid,xid);
                return null;
            }
            waitU.put(xid,uid);
            putIntoList(wait,xid,uid);
            if (hasDeadLock()){
                waitU.remove(xid);
                removeFromList(wait,uid,xid);
                throw Error.DeadlockException;
            }
            Lock newLock = new ReentrantLock();
            newLock.lock();
            waitLock.put(xid,newLock);
            return newLock;
        }finally {
            lock.unlock();
        }

    }

    private Map<Long, Integer> xidStamp;
    private int stamp;

    private boolean hasDeadLock(){
        xidStamp = new HashMap<>();
        stamp = 1;
        for (long xid : x2u.keySet()) {
            Integer s = xidStamp.get(xid);
            if (s != null && s > 0){
                continue;
            }
            stamp++;
            if (dfs(xid)){return true;}
        }
        return false;
    }

    private boolean dfs(long xid){
        Integer stp = xidStamp.get(xid);
        if (stp != null && stamp == stp){return true;}
        if (stp != null && stp < stamp){return false;}
        xidStamp.put(xid,stamp);
        Long uid = waitU.get(xid);
        if (uid == null){
            return false;
        }
        Long x = u2x.get(uid);
        assert x != null;
        return dfs(x);
    }

    private void removeFromList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if(l == null) return;
        Iterator<Long> i = l.iterator();
        while(i.hasNext()) {
            long e = i.next();
            if(e == uid1) {
                i.remove();
                break;
            }
        }
        if(l.size() == 0) {
            listMap.remove(uid0);
        }
    }

    private boolean isInList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if(l == null) return false;
        Iterator<Long> i = l.iterator();
        while(i.hasNext()) {
            long e = i.next();
            if(e == uid1) {
                return true;
            }
        }
        return false;
    }

    private void putIntoList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        if(!listMap.containsKey(uid0)) {
            listMap.put(uid0, new ArrayList<>());
        }
        listMap.get(uid0).add(0, uid1);
    }
    public void remove(long xid) {
        lock.lock();
        try{
            List<Long> uids = x2u.get(xid);
            if (!uids.isEmpty()){
                while (uids.size() > 0){
                    Long uid = uids.remove(0);
                    selectNewXID(uid);
                }
            }
            waitU.remove(xid);
            x2u.remove(xid);
            waitLock.remove(xid);
        }finally {
            lock.unlock();
        }
    }

    private void selectNewXID(Long uid) {
        u2x.remove(uid);
        List<Long> xids = wait.get(uid);
        if (xids == null){return;}
        assert !xids.isEmpty();
        while (!xids.isEmpty()){
            Long xid = xids.remove(0);
            if (!waitLock.containsKey(xid)){continue;}
            u2x.put(uid,xid);
            Lock lo = waitLock.remove(uid);
            waitU.remove(xid);
            lo.unlock();
            break;
        }
        if (xids.isEmpty()){wait.remove(uid);}
    }
}
