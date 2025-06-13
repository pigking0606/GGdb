package com.ggking.mydb.backend.dm.pageCache;

import com.ggking.mydb.backend.common.AbstractCache;
import com.ggking.mydb.backend.dm.page.Page;
import com.ggking.mydb.backend.dm.page.PageImpl;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageCacheImpl extends AbstractCache<Page> implements PageCache{

    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;


    public PageCacheImpl(RandomAccessFile file,FileChannel fc,int maxResource) {
        super(maxResource);
        if (maxResource < MEM_MIN_LIM){
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.fc = fc;
        this.file = file;
        fileLock = new ReentrantLock();
        pageNumbers = new AtomicInteger((int) length/PAGE_SIZE);
    }

    @Override
    public void close() {
        super.close();
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    @Override
    protected Page getForCache(long key) throws Exception {
        int pano = (int) key;
        long offset = pageOffset(pano);
        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (Exception e) {
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImpl(pano, buf.array(), this);
    }

    private long pageOffset(int pano) {
        return (pano - 1) * PAGE_SIZE;
    }

    @Override
    protected void releaseForCache(Page page) {
            if (page.isDirty()){
                flush(page);
                page.setDirty(false);
            }
            
    }

    @Override
    public int newPage(byte[] initData) {
        int pano = pageNumbers.incrementAndGet();
        Page pg = new PageImpl(pano,initData,null);
        flush(pg);
        return pano;
    }

    @Override
    public Page getPage(int pgno) throws Exception {
        return get((long) pgno);
    }

    @Override
    public void release(Page page) {
        release((long) page.getPageNumber());
    }

    @Override
    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try{
            file.setLength(size);
        }catch (IOException e){
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    @Override
    public int getPageNumber() {
        return pageNumbers.intValue();
    }

    @Override
    public void flushPage(Page pg) {
        flush(pg);
    }

    private void flush(Page page){
        int pano = page.getPageNumber();
        long offset = pageOffset(pano);
        fileLock.lock();
        ByteBuffer buf = ByteBuffer.wrap(page.getData());
        try {
            fc.position(offset);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }finally {
            fileLock.unlock();
        }
    }
}
