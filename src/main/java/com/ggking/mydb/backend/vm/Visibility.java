package com.ggking.mydb.backend.vm;

import com.ggking.mydb.backend.tm.TransactionManager;

public class Visibility {
    public static boolean readCommitted(TransactionManager tm,Transaction t,Entry entry){
        long xid = t.xid;
        long xmin = entry.getXmin();
        long xmax = entry.getXmax();
        if (xid == xmin && xmax == 0){return true;}
        if (tm.isCommitted(xmin)){
            if (xmax == 0){return true;}
            if (xmax != xid){
                if (!tm.isCommitted(xmax)){
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean repeatableRead(TransactionManager tm,Transaction t,Entry entry){
        long xid = t.xid;
        long xmin = entry.getXmin();
        long xmax = entry.getXmax();
        if (xid == xmin && xmax == 0)return true;
        if (tm.isCommitted(xmin) && xmin < xid && !t.idInSnapdhot(xmin)){
            if (xmax == 0)return true;
            if (xid != xmax){
                if (!tm.isCommitted(xmax) || xmax > xid || t.idInSnapdhot(xmax)){
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isVersionSkip(TransactionManager tm,Transaction t,Entry entry){
        long xmax = entry.getXmax();
        if (t.level == 0){
            return false;
        }else {
            return tm.isCommitted(xmax) && (xmax > t.xid || t.idInSnapdhot(xmax));
        }
    }

    public static boolean isVisible(TransactionManager tm,Transaction t,Entry entry){
        if (t.level == 0){
            return readCommitted(tm,t,entry);
        }else {
            return repeatableRead(tm,t,entry);
        }
    }

}
