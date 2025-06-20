package com.ggking.mydb.backend.dm;

import com.ggking.mydb.backend.common.SubArray;
import com.ggking.mydb.backend.dm.dataItem.DataItem;
import com.ggking.mydb.backend.dm.dataItem.DataItemImpl;
import com.ggking.mydb.backend.dm.logger.Logger;
import com.ggking.mydb.backend.dm.page.Page;
import com.ggking.mydb.backend.dm.page.PageX;
import com.ggking.mydb.backend.dm.pageCache.PageCache;
import com.ggking.mydb.backend.tm.TransactionManager;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.util.*;

public class Recover {
    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;

    private static final int REDO = 0;
    private static final int UNDO = 1;





    static class InsertLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    static class UpdateLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    public static void  recover(TransactionManager tm, Logger lg, PageCache pc){
        System.out.println("Recovering......");

        lg.rewind();
       int maxPgno = 0;
       while (true){
           byte[] log = lg.next();
           if (log == null)break;
           int pgno;
           if (isInsertLog(log)){
               InsertLogInfo li = parseInsertLog(log);
               pgno = li.pgno;
           }else {
               UpdateLogInfo li = parseUpdateLog(log);
               pgno = li.pgno;
           }
           if (pgno > maxPgno)maxPgno = pgno;
           if (maxPgno == 0)maxPgno = 1;

           pc.truncateByBgno(maxPgno);
           System.out.println("Truncate to " + maxPgno + " pages.");

           redoTranscations(tm, lg, pc);
           System.out.println("Redo Transactions Over.");

           undoTranscations(tm, lg, pc);
           System.out.println("Undo Transactions Over.");

           System.out.println("Recovery Over.");

       }

    }

    private static void redoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        lg.rewind();
        while (true){
            byte[] log = lg.next();
            if (log == null)break;
            if (isInsertLog(log)){
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                if (!tm.isActive(xid)){
                    doInsertLog(pc,log,REDO);
                }
            }else {
                UpdateLogInfo li = parseUpdateLog(log);
                long xid = li.xid;
                if (!tm.isActive(xid)){
                    doUpdateLog(pc,log,REDO);
                }
            }
        }
    }

    private static void undoTranscations(TransactionManager tm,Logger lg,PageCache pc){
        lg.rewind();
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        while (true){
            byte[] log = lg.next();
            if (log == null)break;
             if (isInsertLog(log)){
                 InsertLogInfo li = parseInsertLog(log);
                 long xid = li.xid;
                 if (tm.isActive(xid)){
                     if (!logCache.containsKey(xid)){
                         logCache.put(xid,new ArrayList<>());
                     }
                     logCache.get(xid).add(log);
                 }
             }else {
                 UpdateLogInfo li = parseUpdateLog(log);
                 long xid = li.xid;
                 if (tm.isActive(xid)){
                     if (!logCache.containsKey(xid)){
                         logCache.put(xid,new ArrayList<>());
                     }
                     logCache.get(xid).add(log);
                 }
             }
        }

        for (Map.Entry<Long,List<byte[]>> entry : logCache.entrySet()) {
            List<byte[]> logs = entry.getValue();
            for(int i = logs.size() - 1;i >= 0;i--){
                byte[] log = logs.get(i);
                if (isInsertLog(log)){
                    doInsertLog(pc,log,UNDO);
                }else {
                    doUpdateLog(pc,log,UNDO);
                }
            }
            tm.abort(entry.getKey());
        }
    }
    
    // [LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE+1;
    private static final int OF_UPDATE_UID = OF_XID+8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID+8;

    public static byte[] updateLog(long xid, DataItemImpl di) {
        byte[] logTypeRaw = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] uidRaw = Parser.long2Byte(di.getUid());
        byte[] oldRaw = di.getOldRaw();
        SubArray raw = di.getRaw();
        byte[] newRaw = Arrays.copyOfRange(raw.raw,raw.start,raw.end);
        return Bytes.concat(logTypeRaw,xidRaw,uidRaw,oldRaw,newRaw);
    }
    private static void doUpdateLog(PageCache pc, byte[] log, int flag) {
        int pgno;
        short offset;
        byte[] raw;
        if (flag == REDO){
            UpdateLogInfo li = parseUpdateLog(log);
            pgno = li.pgno;
            offset = li.offset;
            raw = li.newRaw;
        }else {
            UpdateLogInfo li = parseUpdateLog(log);
            pgno = li.pgno;
            offset = li.offset;
            raw = li.oldRaw;
        }

        Page pg = null;
        try {
            pg = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }

        try {
            PageX.recoverUpdate(pg,raw,offset);
        } finally {
            pg.release();
        }
    }

    private static UpdateLogInfo parseUpdateLog(byte[] log) {
        UpdateLogInfo li = new UpdateLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log,OF_XID,OF_UPDATE_UID));
        long uid = Parser.parseLong(Arrays.copyOfRange(log, OF_UPDATE_UID, OF_UPDATE_RAW));
        li.offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        li.pgno = (int)(uid & ((1L << 32) - 1));
        int length = (log.length - OF_UPDATE_RAW);
        li.oldRaw = Arrays.copyOfRange(log,OF_UPDATE_RAW,OF_UPDATE_RAW + length);
        li.newRaw = Arrays.copyOfRange(log,OF_UPDATE_RAW + length,OF_UPDATE_RAW + 2 * length);

        return li;
    }

    // [LogType] [XID] [Pgno] [Offset] [Raw]
    private static final int OF_INSERT_PGNO = OF_XID+8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PGNO+4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET+2;

    public static byte[] insertLog(long xid, Page pg, byte[] raw) {
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] pgnoRaw = Parser.int2Byte(pg.getPageNumber());
        byte[] offsetRaw = Parser.short2Byte(PageX.getFSO(pg));

        return Bytes.concat(logTypeRaw,xidRaw,pgnoRaw,offsetRaw,raw);
    }
    private static void doInsertLog(PageCache pc, byte[] log, int flag) {
        InsertLogInfo li = parseInsertLog(log);

        Page pg = null;
        try {
            pg = pc.getPage(li.pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        try {
            if (flag == UNDO){
                DataItem.setDataItemRawInvalid(li.raw);
            }
            PageX.recoverInsert(pg,li.raw,li.offset);
        } finally {
            pg.release();
        }
    }

    private static InsertLogInfo parseInsertLog(byte[] log) {
        InsertLogInfo li = new InsertLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_INSERT_PGNO));
        li.pgno = Parser.parseInt(Arrays.copyOfRange(log, OF_INSERT_PGNO, OF_INSERT_OFFSET));
        li.offset = Parser.parseShort(Arrays.copyOfRange(log, OF_INSERT_OFFSET, OF_INSERT_RAW));
        li.raw = Arrays.copyOfRange(log, OF_INSERT_RAW, log.length);
        return li;
    }

    private static boolean isInsertLog(byte[] log) {
         return log[0] == LOG_TYPE_INSERT;
    }
}
