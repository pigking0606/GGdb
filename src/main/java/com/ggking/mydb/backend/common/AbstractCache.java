package com.ggking.mydb.backend.common;

import com.ggking.mydb.common.Error;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractCache<T> {

    //实际缓存数据
    private HashMap<Long,T> cache;
    //记录资源当前被引用次数 为0时才能安全释放资源
    private HashMap<Long,Integer> references;
    //记录资源当前是否正在被获取
    private HashMap<Long,Boolean> getting;

    private int maxResource;                            // 缓存的最大缓存资源数
    private int count = 0;                              // 缓存中元素的个数
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();
        lock = new ReentrantLock();
    }

    //从缓存中获取数据 若为空 则从磁盘中获取 并存入缓存
    protected T get(long key) throws  Exception{
        while (true){
            lock.lock();
            if (getting.containsKey(key)){
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }
            if (cache.containsKey(key)){
                T obj = cache.get(key);
                references.put(key,references.get(key) + 1);
                lock.unlock();
                return obj;
            }

            if (maxResource > 0 && count == maxResource){
                lock.unlock();
                throw Error.CacheFullException;
            }

            count++;
            getting.put(key,true);
            lock.unlock();
            break;

        }
        T obj = null;
        try{
            obj = getForCache(key);
        }catch (Exception e){
            lock.lock();
            count--;
            getting.remove(key);
            throw e;
        }

        lock.lock();
        getting.remove(key);
        cache.put(key,obj);
        references.put(key,1);
        lock.unlock();

        return obj;
    }

    protected void release(long key){
        try {
            lock.lock();
            Integer ref = references.get(key) - 1;
            if (ref == 0){
                cache.remove(key);
                references.remove(key);
                count--;
            }else {
                references.put(key,ref);
            }
        } finally {
            lock.unlock();
        }
    }

    protected void close(){
        lock.lock();
        try{
            Set<Long> keys = references.keySet();
            for (Long key : keys) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
            }
        }finally {
            lock.unlock();
        }
    }
    protected abstract T getForCache(long key) throws Exception;

    protected abstract void releaseForCache(T obj);


}
