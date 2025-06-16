package com.ggking.mydb.backend.dm;

import com.ggking.mydb.backend.common.AbstractCache;
import com.ggking.mydb.backend.dm.dataItem.DataItem;
import com.ggking.mydb.backend.dm.dataItem.DataItemImpl;
import com.ggking.mydb.backend.dm.logger.Logger;
import com.ggking.mydb.backend.dm.page.Page;
import com.ggking.mydb.backend.dm.page.PageOne;
import com.ggking.mydb.backend.dm.page.PageX;
import com.ggking.mydb.backend.dm.pageCache.PageCache;
import com.ggking.mydb.backend.dm.pageIndex.PageIndex;
import com.ggking.mydb.backend.dm.pageIndex.PageInfo;
import com.ggking.mydb.backend.tm.TransactionManager;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.utils.Types;
import com.ggking.mydb.common.Error;

import java.nio.ByteBuffer;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager{

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }


    void fillPageIndex(){
        int pageNum = pc.getPageNumber();
        for (int i = 2;i <= pageNum;i++){
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short) (uid & ((1L << 16) -1));
        uid >>>=  32;
        int pgno = (int) (uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg,offset,this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl) super.get(uid);
        if (!di.isValid()){
            di.release();
            return null;
        }
        return di;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if (raw.length > PageX.MAX_FREE_SPACE){
            throw Error.DataTooLargeException;
        }
        PageInfo pi = null;
        for (int i = 0;i < 5;i++){
            pi = pIndex.select(raw.length);
            if (pi != null){
                break;
            }else {
                int pgno = pc.newPage(PageX.initRaw());
                pIndex.add(pgno,PageX.MAX_FREE_SPACE);
            }
        }
        if (pi == null){
            throw Error.DatabaseBusyException;
        }
        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            byte[] log = Recover.insertLog(xid,pg,raw);
            logger.log(log);
            short offset = PageX.insert(pg, raw);
            pg.release();
            return Types.addressToUid(pi.pgno, offset);
        } finally {
            if (pg != null){
                pIndex.add(pi.pgno,PageX.getFreeSpace(pg));
            }else {
                pIndex.add(pi.pgno,freeSpace);
            }
        }

    }

    @Override
    public void close() {
        super.close();
        logger.close();
        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    void initPageOne() {
        int pgno = pc.newPage(PageOne.initRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    boolean loadCheckPageOne(){
        try {
            pageOne = pc.getPage(1);

        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    public void releaseDataItem(DataItemImpl di) {
        super.release(di.getUid());
    }

    public void logDataItem(long xid, DataItemImpl di) {
        byte[] log = Recover.updateLog(xid,di);
        logger.log(log);
    }
}
