package com.ggking.mydb.backend.dm.logger;

import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.utils.Parser;
import com.ggking.mydb.common.Error;
import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LoggerImpl implements Logger{


    private static final int SEED = 13331;
    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;

    public static final String LOG_SUFFIX = ".log";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position;
    private long fileSize;
    private int xChecksum;

    LoggerImpl(RandomAccessFile file,FileChannel fc){
        this.file = file;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile file,FileChannel fc,int xChecksum){
        this.file = file;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    void init(){
        long size = 0;

        try {
            size = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }

        if (size < 4){
            Panic.panic(Error.BadLogFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(4);
        try {
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        int xChecksum = Parser.parseInt(buf.array());
        this.xChecksum = xChecksum;
        this.fileSize = size;

        checkAndRemoveTail();
    }

    private void checkAndRemoveTail() {
        int xCheck = 0;
        while (true){
            byte[] log = internNext();
            if (log == null){break;}
            xCheck = calChecksum(xCheck,log);
        }
        if (xCheck != xChecksum){
            Panic.panic(Error.BadLogFileException);}

        try {
            truncate(position);
        }catch (Exception e){
            Panic.panic(e);
        }

        try {
            file.seek(position);
        } catch (IOException e) {
            Panic.panic(e);
        }

        rewind();
    }

    private int calChecksum(int xCheck, byte[] log) {
        for (byte b: log) {
            xCheck = xChecksum * SEED + b;
        }
        return xCheck;

    }

    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try {
            fc.position(position);
            fc.write(buf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            lock.unlock();
        }
        updateXChecksum(log);
    }

    private void updateXChecksum(byte[] log) {
        this.xChecksum = calChecksum(this.xChecksum,log);
        ByteBuffer buf = ByteBuffer.wrap(Parser.int2Byte(xChecksum));
        try{
            fc.position(0);
            fc.write(buf);
            fc.force(false);
        }catch (IOException e){
            Panic.panic(e);
        }
    }

    private byte[] wrapLog(byte[] data) {
        byte[] checksum = Parser.int2Byte(calChecksum(0,data));
        byte[] size = Parser.int2Byte(data.length);
        return Bytes.concat(size,checksum,data);
    }

    @Override
    public void truncate(long x) throws Exception {

    }

    @Override
    public byte[] next() {
        lock.lock();
        try{
            byte[] log = internNext();
            if (log == null)return null;
            return Arrays.copyOfRange(log,OF_DATA,log.length);
        }finally {
            lock.unlock();
        }
    }

    private byte[] internNext(){
        if (position + OF_DATA > fileSize){
            return null;
        }

        ByteBuffer tmp = ByteBuffer.allocate(4);
        try {
            fc.position(position);
            fc.read(tmp);
        } catch (IOException e) {
            Panic.panic(e);
        }

        int size = Parser.parseInt(tmp.array());
        if (position + size + OF_DATA > fileSize){
            return  null;
        }
        ByteBuffer buf = ByteBuffer.allocate(size + OF_DATA);
        try {
            fc.position(position);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        byte[] log = buf.array();
        int checksum1 = calChecksum(0, Arrays.copyOfRange(log,OF_DATA,log.length));
        int checksum2 = Parser.parseInt(Arrays.copyOfRange(log,OF_CHECKSUM,OF_DATA));
        if (checksum1 != checksum2){
            return null;}
        position += log.length;

        return log;
    }

    @Override
    public void rewind() {
        position = 4;
    }

    @Override
    public void close() {

    }
}
