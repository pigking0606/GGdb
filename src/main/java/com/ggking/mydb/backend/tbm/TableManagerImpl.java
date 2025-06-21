package com.ggking.mydb.backend.tbm;

import com.ggking.mydb.backend.dm.DataManager;
import com.ggking.mydb.backend.parser.statement.*;
import com.ggking.mydb.backend.vm.VersionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TableManagerImpl implements  TableManager{

    VersionManager vm;
    DataManager dm;
    private Booter booter;
    private Map<String, Table> tableCache;
    private Map<Long, List<Table>> xidTableCache;
    private Lock lock;

    TableManagerImpl(VersionManager vm, DataManager dm, Booter booter) {
        this.vm = vm;
        this.dm = dm;
        this.booter = booter;
        this.tableCache = new HashMap<>();
        this.xidTableCache = new HashMap<>();
        lock = new ReentrantLock();
        loadTables();
    }

    @Override
    public BeginRes begin(Begin begin) {
        return null;
    }

    @Override
    public byte[] commit(long xid) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] abort(long xid) {
        return new byte[0];
    }

    @Override
    public byte[] show(long xid) {
        return new byte[0];
    }

    @Override
    public byte[] create(long xid, Create create) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] insert(long xid, Insert insert) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] read(long xid, Select select) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] update(long xid, Update update) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] delete(long xid, Delete delete) throws Exception {
        return new byte[0];
    }
}
