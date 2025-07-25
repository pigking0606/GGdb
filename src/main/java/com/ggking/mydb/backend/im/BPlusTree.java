package com.ggking.mydb.backend.im;

import com.ggking.mydb.backend.common.SubArray;
import com.ggking.mydb.backend.dm.DataManager;
import com.ggking.mydb.backend.dm.dataItem.DataItem;
import com.ggking.mydb.backend.tm.TransactionManagerImpl;
import com.ggking.mydb.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BPlusTree {

    DataManager dm;
    long bootUid;
    DataItem bootDataItem;
    Lock bootLock;

    public static long create(DataManager dm)throws Exception{
        byte[] rawRoot = Node.newNilRootRaw();
        long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID,rawRoot);
        return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
    }

    public static BPlusTree load(long bootUid,DataManager dm)throws Exception{
        DataItem bootDataItem = dm.read(bootUid);
        assert bootDataItem != null;
        BPlusTree t = new BPlusTree();
        t.bootUid = bootUid;
        t.bootDataItem = bootDataItem;
        t.dm = dm;
        t.bootLock = new ReentrantLock();
        return t;
    }

    private long rootUid(){
        bootLock.lock();
        try{
            SubArray sa = bootDataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw,sa.start,sa.start + 9));
        }finally {
            bootLock.unlock();
        }
    }

    private void updateRootUid(long left,long right,long rightKey)throws Exception{
        bootLock.lock();
        try{
            byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
            long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
            bootDataItem.before();
            SubArray diRaw = bootDataItem.data();
            System.arraycopy(Parser.long2Byte(newRootUid),0,diRaw.raw,diRaw.start,8);
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);
        }finally {
            bootLock.unlock();
        }
    }

    private long searchLeaf(long nodeUid,long key)throws Exception{
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        if (isLeaf){
            return nodeUid;
        }else {
            long next = searchNext(nodeUid,key);
            return searchLeaf(next,key);
        }
    }

    private long searchNext(long nodeUid,long key)throws Exception{
        while (true){
            Node node = Node.loadNode(this, nodeUid);
            Node.SearchNextRes res = node.searchNext(key);
            node.release();
            if (res.uid != 0){return res.uid;}
            nodeUid = res.siblingUid;
        }
    }

    public List<Long> search(long key)throws Exception{
        return searchRange(key,key);
    }
    public List<Long> searchRange(long lKey,long rKey)throws Exception{
        long rootUid = rootUid();
        long leafUid = searchLeaf(rootUid, lKey);
        List<Long> uids = new ArrayList<>();
        while (true){
            Node leaf = Node.loadNode(this, leafUid);
            Node.LeafSearchRangeRes res = leaf.leafSearchRangeRes(lKey, rKey);
            leaf.release();
            uids.addAll(res.uids);
            if (res.siblingUid == 0){
                break;
            }else {
                leafUid = res.siblingUid;
            }
        }
        return uids;
    }

    public void insert(long key,long uid)throws Exception{
        long rootUid = rootUid();
       InsertRes res =  insert(rootUid,uid,key);
       assert res != null;
       if (res.newNode != 0){
           updateRootUid(rootUid,res.newNode,res.newKey);
       }
    }

    class InsertRes {
        long newNode, newKey;
    }

    private InsertRes insert(long nodeUid,long uid,long key)throws Exception{
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        InsertRes res = null;
        if (isLeaf){
            res = insertAndSplit(nodeUid,uid, key);
        }else {
            long next = searchNext(nodeUid,key);
            InsertRes ir = insert(next, uid, key);
            if (ir.newNode != 0){
                res = insertAndSplit(nodeUid,ir.newNode,ir.newKey);
            }else {
                res = new InsertRes();
            }
        }
        return res;
    }

    private InsertRes insertAndSplit(long nodeUid,long uid,long key)throws Exception{
        while (true){
            Node node = Node.loadNode(this, nodeUid);
            Node.InsertAndSplitRes iRes = node.insertAndSplit(uid, key);
            if (iRes.siblingUid != 0){
                nodeUid = iRes.siblingUid;
            }else {
                InsertRes res = new InsertRes();
                res.newNode = iRes.newSon;
                res.newKey = iRes.newKey;
                return res;
            }
        }
    }
    public void close() {
        bootDataItem.release();
    }
}
