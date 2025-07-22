package com.ggking.mydb.backend.im;


import com.ggking.mydb.backend.common.SubArray;
import com.ggking.mydb.backend.dm.dataItem.DataItem;
import com.ggking.mydb.backend.tm.TransactionManagerImpl;
import com.ggking.mydb.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Node结构如下：
 * [LeafFlag]1字节[KeyNumber]2字节[SiblingUid]8字节
 * [Son0][Key0][Son1][Key1]...[SonN][KeyN]
 */
public class Node {

    static final int IS_LEAF_OFFSET = 0;
    static final int NO_KEYS_OFFSET = IS_LEAF_OFFSET + 1;
    static final int SIBLING_OFFSET = NO_KEYS_OFFSET + 2;
    static final int NODE_HEADER_SIZE = SIBLING_OFFSET + 8;
    static final int BALANCE_NUMBER = 32;
    static final int NODE_SIZE = NODE_HEADER_SIZE + (2 * 8) * (BALANCE_NUMBER * 2 + 2);

    BPlusTree tree;
    DataItem dataItem;
    SubArray raw;
    long uid;

    static void setRawIsLeaf(SubArray raw,boolean isLeaf){
        if (isLeaf){
            raw.raw[raw.start + IS_LEAF_OFFSET] = 1;
        }else{
            raw.raw[raw.start + IS_LEAF_OFFSET] = 0;
        }
    }

    static boolean getRawIfLeaf(SubArray raw){
        return raw.raw[raw.start + IS_LEAF_OFFSET] == 1;
    }

    static void setRawNoKeys(SubArray raw,int nokeys){
        System.arraycopy(Parser.short2Byte((short) nokeys),0,raw.raw,raw.start + NO_KEYS_OFFSET,2);
    }

    static int getRawNoKeys(SubArray raw){
        return Parser.parseInt(Arrays.copyOfRange(raw.raw,raw.start + NO_KEYS_OFFSET,raw.start + NO_KEYS_OFFSET + 2));
    }

    static void setRawSibling(SubArray raw, long sibling) {
        System.arraycopy(Parser.long2Byte(sibling), 0, raw.raw, raw.start + SIBLING_OFFSET, 8);
    }

    static long getRawSibling(SubArray raw) {
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, raw.start + SIBLING_OFFSET, raw.start + SIBLING_OFFSET + 8));
    }

    static void setRawKthSon(SubArray raw,long uid,int kth){
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        System.arraycopy(Parser.long2Byte(uid),0,raw.raw,offset,8);
    }

    static long getRawKthSon(SubArray raw, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset + 8));
    }

    static void setRawKthKey(SubArray raw, long key, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        System.arraycopy(Parser.long2Byte(key), 0, raw.raw, offset, 8);
    }

    static long getRawKthKey(SubArray raw, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset + 8));
    }

    static void copyRawFromKth(SubArray from, SubArray to, int kth) {
        int offset = from.start + NODE_HEADER_SIZE + kth * (8 * 2);
        System.arraycopy(from.raw, offset, to.raw, to.start + NODE_HEADER_SIZE, from.end - offset);
    }

    static void shiftRawKth(SubArray raw,int kth){
        int begin = raw.start + NODE_HEADER_SIZE + (kth + 1)*(2 * 8);
        int end = raw.start + NODE_SIZE - 1;
        for (int i = end;i >= begin;i--){
            raw.raw[i] = raw.raw[i - (8 * 2)];
        }
    }

    static byte[] newRootRaw(long left,long right,long key){
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(raw,false);
        setRawNoKeys(raw,2);
        setRawSibling(raw,0);
        setRawKthSon(raw,left,0);
        setRawKthKey(raw,key,0);
        setRawKthSon(raw,right,1);
        setRawKthKey(raw,Long.MAX_VALUE,1);
        return raw.raw;
    }

    static byte[] newNilRootRaw(){
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(raw,true);
        setRawNoKeys(raw,0);
        setRawSibling(raw,0);
        return raw.raw;
    }

    static Node loadNode(BPlusTree bTree,long uid)throws Exception{
        DataItem di = bTree.dm.read(uid);
        assert di != null;
        Node node = new Node();
        node.tree = bTree;
        node.dataItem = di;
        node.uid = uid;
        node.raw = di.data();
        return node;
    }

    public void release(){dataItem.release();}

    public boolean isLeaf() {
        dataItem.rLock();
        try {
            return getRawIfLeaf(raw);
        } finally {
            dataItem.rUnLock();
        }
    }

    class SearchNextRes {
        long uid;
        long siblingUid;
    }

    public SearchNextRes searchNext(long key){
        dataItem.rLock();
        try{
            SearchNextRes res = new SearchNextRes();
            int noKeys = getRawNoKeys(raw);
            for (int i = 0;i < noKeys;i++){
                long ik = getRawKthKey(raw, i);
                if (key < ik){
                    res.uid = getRawKthSon(raw,i);
                    res.siblingUid = 0;
                    return res;
                }
            }
            res.uid = 0;
            res.siblingUid = getRawSibling(raw);
            return res;
        }finally {
            dataItem.rUnLock();
        }
    }

    class LeafSearchRangeRes {
        List<Long> uids;
        long siblingUid;
    }

    public LeafSearchRangeRes leafSearchRangeRes(long lKey,long rKey){
        dataItem.rLock();
        try{
            LeafSearchRangeRes res = new LeafSearchRangeRes();
            int noKeys = getRawNoKeys(raw);
            int kth = 0;
            while (kth < noKeys){
                long key = getRawKthKey(raw, kth);
                if (lKey <= key){
                    break;
                }
                kth++;
            }
            List<Long> uids = new ArrayList<>();
            while (kth < noKeys){
                long key = getRawKthKey(raw,kth);
                if (key <= rKey){
                    uids.add(getRawKthSon(raw,kth));
                }else {break;}
            }
            long siblingUid = 0;
            if (kth == noKeys){
                siblingUid = getRawSibling(raw);
            }
            res.uids = uids;
            res.siblingUid = siblingUid;
            return res;
        }finally {
            dataItem.rUnLock();
        }
    }


    class InsertAndSplitRes {
        long siblingUid, newSon, newKey;
    }

    public InsertAndSplitRes insertAndSplit(long uid,long key) throws  Exception{
        boolean success = false;
        Exception err = null;
        InsertAndSplitRes res = new InsertAndSplitRes();

        dataItem.before();
        try{
            success = insert(uid,key);
            if (!success){
                res.siblingUid = getRawSibling(raw);
                return res;
            }
            if (needSplit()){
                try{
                    SplitRes r = split();
                    res.newSon = r.newSon;
                    res.newKey = r.newKey;
                    return res;
                }catch (Exception e){
                    err = e;
                    throw e;
                }
            }else {
                return res;
            }
        }finally {
            if (err == null && success){
                dataItem.after(TransactionManagerImpl.SUPER_XID);
            }else {
                dataItem.unBefore();
            }
        }
    }

    private boolean insert(long uid,long key){
        int noKeys = getRawNoKeys(raw);
        int kth = 0;
        while (kth < noKeys){
            long ik = getRawKthKey(raw,kth);
            if (ik >= key){
                break;
            }
            kth++;
        }
        if (kth == noKeys && getRawSibling(raw) != 0){
            return false;
        }

        if (getRawIfLeaf(raw)){
            shiftRawKth(raw,kth);
            setRawKthKey(raw,key,kth);
            setRawKthSon(raw,uid,kth);
            setRawNoKeys(raw,getRawNoKeys(raw) + 1);
        }else {
            long kk = getRawKthKey(raw, kth);
            setRawKthKey(raw, key, kth);
            shiftRawKth(raw, kth+1);
            setRawKthKey(raw, kk, kth+1);
            setRawKthSon(raw, uid, kth+1);
            setRawNoKeys(raw, noKeys+1);
        }
        return true;
    }

    private boolean needSplit(){return BALANCE_NUMBER * 2 == getRawNoKeys(raw);}

    class SplitRes {
        long newSon, newKey;
    }

    private SplitRes split()throws Exception{
        SubArray nodeRaw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(nodeRaw,getRawIfLeaf(raw));
        setRawNoKeys(nodeRaw,BALANCE_NUMBER);
        setRawSibling(nodeRaw,getRawSibling(raw));
        copyRawFromKth(raw,nodeRaw,BALANCE_NUMBER);
        long son = tree.dm.insert(TransactionManagerImpl.SUPER_XID,nodeRaw.raw);
        setRawNoKeys(raw,BALANCE_NUMBER);
        setRawSibling(raw,son);
        SplitRes res = new SplitRes();
        res.newSon = son;
        res.newKey = getRawKthKey(nodeRaw,0);
        return  res;
    }
}
