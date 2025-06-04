package com.ggking.mydb.backend.tm;


import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.utils.Parser;
import com.ggking.mydb.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionManagerImpl implements TransactionManager{

    static final int LEN_XID_HEADER_LEGTH = 8;

    private static final int XID_FIELD_SIZE = 1;

    private static final byte FIELD_TRAN_ACTIVE = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED = 2;

    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile file, FileChannel fc){
        this.file = file;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }

    private void checkXIDCounter(){
            long fileLen = 0;
        try {
            fileLen = file.length();
        } catch (IOException e) {
            Panic.panic(Error.BadXIDFileException);
        }

        if (fileLen < LEN_XID_HEADER_LEGTH){
            Panic.panic(Error.BadXIDFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LEGTH);
        try {
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        xidCounter = Parser.parseLong(buf.array());
        long end = getXidPosition(xidCounter + 1);

        if (end != fileLen){
            Panic.panic(Error.BadXIDFileException);
        }
    }

    private long getXidPosition(long xid){
        return LEN_XID_HEADER_LEGTH + (xid - 1)*XID_FIELD_SIZE;
    }

    private void updateXID(long xid,byte stauts){
        long offset = getXidPosition(xid);
        byte[] tmp = new byte[LEN_XID_HEADER_LEGTH];
        tmp[0] = stauts;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            fc.position(offset);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }

    }
    private boolean checkXID(long xid,byte status){
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.allocate(1);

        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf.array()[0] == status;
    }

    private void incrXIDCounter(){
        xidCounter++;
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try {
            fc.position(0);
            fc.write(buf);

        } catch (IOException e) {
            Panic.panic(e);
        }

        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }



    @Override
    public long begin() {
        counterLock.lock();
        try{
            long xid = xidCounter + 1;
            updateXID(xid,FIELD_TRAN_ACTIVE);
            incrXIDCounter();
            return xid;
        } finally {
          counterLock.unlock();
        }
    }

    @Override
    public void commit(long xid) {
        updateXID(xid,FIELD_TRAN_COMMITTED);
    }

    @Override
    public void abort(long xid) {
        updateXID(xid,FIELD_TRAN_ABORTED);
    }

    @Override
    public boolean isActive(long xid) {
        if (xid == SUPER_XID)return false;
        return checkXID(xid,FIELD_TRAN_ACTIVE);
    }

    @Override
    public boolean isCommitted(long xid) {
        if (xid == SUPER_XID)return true;
        return checkXID(xid,FIELD_TRAN_COMMITTED);
    }

    @Override
    public boolean isAborted(long xid) {
        if (xid == SUPER_XID)return false;
        return checkXID(xid,FIELD_TRAN_ABORTED);
    }


    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
}
