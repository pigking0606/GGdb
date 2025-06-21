package com.ggking.mydb.backend.tbm;

import com.ggking.mydb.backend.im.BPlusTree;
import com.ggking.mydb.backend.tm.TransactionManagerImpl;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.utils.ParseStringRes;
import com.ggking.mydb.backend.utils.Parser;
import com.ggking.mydb.common.Error;
import com.google.common.primitives.Bytes;
import com.sun.jdi.event.ExceptionEvent;

import java.util.Arrays;

public class Field {
    long uid;
    private Table tb;
    String fieldName;
    String fieldType;
    private long index;
    private BPlusTree bt;

    public Field(long uid, Table tb) {
        this.uid = uid;
        this.tb = tb;
    }

    public Field(Table tb, String fieldName, String fieldType, long index) {
        this.tb = tb;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
    }

    public static Field createField(Table tb, long xid, String fieldName, String fieldType, boolean indexed)throws Exception{
        typeCheck(fieldType);
        Field f = new Field(tb,fieldName,fieldType,0);
        if (indexed){
            long index = BPlusTree.create(((TableManagerImpl) tb.tbm).dm);
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            f.index = index;
            f.bt = bt;
        }
        f.persistSelf(xid);
        return f;
    }

    private static void typeCheck(String fieldType) throws Exception {
        if (!"int32".equals(fieldType) && !"int64".equals(fieldType) && !"string".equals(fieldType)) {
            throw Error.InvalidFieldException;
        }
    }

    private void persistSelf(long xid) throws Exception {
        // 将字段名转换为字节数组
        byte[] nameRaw = Parser.string2Byte(fieldName);
        // 将字段类型转换为字节数组
        byte[] typeRaw = Parser.string2Byte(fieldType);
        // 将索引转换为字节数组
        byte[] indexRaw = Parser.long2Byte(index);
        // 将字段名、字段类型和索引的字节数组合并，然后插入到持久化存储中
        // 插入成功后，会返回一个唯一的uid，将这个uid设置为当前Field对象的uid
        this.uid = ((TableManagerImpl) tb.tbm).vm.insert(xid, Bytes.concat(nameRaw, typeRaw, indexRaw));
    }

    public static Field loadField(Table tb,long uid) throws Exception{
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) tb.tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        }catch (Exception e){
            Panic.panic(e);
        }

        assert raw != null;
        return new Field(uid,tb).parseSele(raw);
    }

    private Field parseSele(byte[] raw) throws Exception{
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        fieldName = res.str;
        position += res.next;
        res = Parser.parseString(Arrays.copyOfRange(raw, position, raw.length));
        fieldType = res.str;
        position += res.next;
        this.index =  Parser.parseLong(Arrays.copyOfRange(raw,position,position + 8));
        if (index != 0){
            try{
                bt = BPlusTree.load(index,((TableManagerImpl) tb.tbm).dm);
            }catch (Exception e){
                Panic.panic(e);
            }
        }
        return this;
    }


}
