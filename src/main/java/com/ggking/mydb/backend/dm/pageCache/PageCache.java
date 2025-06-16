package com.ggking.mydb.backend.dm.pageCache;

import com.ggking.mydb.backend.dm.page.Page;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public interface PageCache {

    public static final int PAGE_SIZE = 1 << 13;

    static PageCache create(String path, long memory) {
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        try{
            if (!f.createNewFile()){Panic.panic(Error.FileExistsException);}
        }catch (Exception e){
            Panic.panic(e);
        }
        if (!f.canWrite() || !f.canRead()){Panic.panic(Error.FileCannotRWException);}

        FileChannel fc = null;
        RandomAccessFile raf = null;

        try {
            raf = new RandomAccessFile(f, "fw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new PageCacheImpl(raf,fc,(int)memory/PAGE_SIZE);
    }

    static PageCache open(String path, long memory) {
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        if (!f.exists()){Panic.panic(Error.FileExistsException);}
        if (!f.canRead() || !f.canWrite()){Panic.panic(Error.FileCannotRWException);}

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc, (int)memory/PAGE_SIZE);
    }

    int newPage(byte[] initData);
    Page getPage(int pgno) throws Exception;
    void close();
    void release(Page page);
    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);
}
