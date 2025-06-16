package com.ggking.mydb.backend.dm.pageIndex;

import com.ggking.mydb.backend.dm.pageCache.PageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageIndex {

    private static final int INTERVALS_NO = 40;
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private Lock lock;
    private List<PageInfo>[] lists;

    public PageIndex(){
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO + 1];
        for (int i = 0; i < INTERVALS_NO + 1; i++) {
            lists[i] = new ArrayList<>();
        }
    }

    public PageInfo select(int spaceSize){
        lock.lock();
        try{
            int num = spaceSize/THRESHOLD;
            if (num < INTERVALS_NO)num++;
            while (num <= INTERVALS_NO){
                if (lists[num].size() == 0){
                    num++;
                    continue;
                }
                return lists[num].remove(0);
            }
            return null;
        }finally {
            lock.unlock();
        }
    }

    public void add(int pgno,int freeSpace){
        lock.lock();
        try{
            int num = freeSpace / THRESHOLD;
            lists[num].add(new PageInfo(pgno,freeSpace));
        }finally {
            lock.unlock();
        }
    }

}
