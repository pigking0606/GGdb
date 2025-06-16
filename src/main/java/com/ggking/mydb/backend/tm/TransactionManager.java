package com.ggking.mydb.backend.tm;

import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface TransactionManager {
    long begin();
    void commit(long xid);
    void abort(long xid);
    boolean isActive(long xid);
    boolean isCommitted(long xid);
    boolean isAborted(long xid);
    void close();

    public static TransactionManagerImpl create(String path){
        File f = new File(path + TransactionManagerImpl.XID_SUFFIX);
        try {
            if (!f.createNewFile()){Panic.panic(Error.FileExistsException);}
        } catch (IOException e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()){Panic.panic(Error.FileCannotRWException);}

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        ByteBuffer buf = ByteBuffer.wrap(new byte[TransactionManagerImpl.LEN_XID_HEADER_LEGTH]);
        try{
            fc.position(0);
            fc.write(buf);
        }catch (IOException e){
            Panic.panic(e);
        }
        return new TransactionManagerImpl(raf,fc);
    }

    public static TransactionManagerImpl open(String path){
        File f = new File(path + TransactionManagerImpl.XID_SUFFIX);

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

        return new TransactionManagerImpl(raf,fc);
    }
}
