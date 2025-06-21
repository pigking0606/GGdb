package com.ggking.mydb.backend.tbm;

import com.ggking.mydb.backend.parser.statement.Create;
import com.ggking.mydb.backend.tm.TransactionManagerImpl;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.utils.ParseStringRes;
import com.ggking.mydb.backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Table {
    TableManager tbm;
    long uid;
    String name;
    byte status;
    long nextUid;
    List<Field> fields = new ArrayList<>();

    public Table(TableManager tbm, long uid) {
        this.tbm = tbm;
        this.uid = uid;
    }

    public Table(TableManager tbm, String tableName, long nextUid) {
        this.tbm = tbm;
        this.name = tableName;
        this.nextUid = nextUid;
    }

    public static Table createTable(TableManager tbm, long nextUid, long xid, Create create)throws Exception{
        Table tb = new Table(tbm, create.tableName, nextUid);
        for (int i = 0;i < create.fieldName.length;i++){
            String fieldName = create.fieldName[i];
            String fieldType = create.fieldType[i];
            boolean indexed = false;
            for (int j = 0 ;j < create.index.length;j++){
                if (fieldName.equals(create.index[j])){
                    indexed = true;
                    break;
                }
            }
            tb.fields.add(Field.createField(tb,xid,fieldName,fieldType,indexed));
        }
        return tb.persistSelf(xid);
    }

    private Table persistSelf(long xid) throws Exception{
        byte[] nameRaw = Parser.string2Byte(name);
        byte[] nextRaw = Parser.long2Byte(nextUid);
        byte[] fieldRaw = new byte[0];
        for (Field field : fields) {
            fieldRaw = Bytes.concat(fieldRaw,Parser.long2Byte(field.uid));
        }
        uid = ((TableManagerImpl)tbm).vm.insert(xid,Bytes.concat(nameRaw,nextRaw,fieldRaw));
        return this;
    }

    public static Table loadTable(TableManager tbm,long uid) throws Exception{
            byte[] raw = null;
            try {
                raw = ((TableManagerImpl)tbm).vm.read(TransactionManagerImpl.SUPER_XID,uid);
            }catch (Exception e){
                Panic.panic(e);
            }
            assert raw != null;
        Table tb = new Table(tbm, uid);
        return tb.perseSelf(raw);
    }

    private Table perseSelf(byte[] raw) throws Exception{
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        name = res.str;
        position += res.next;
        nextUid = Parser.parseLong(Arrays.copyOfRange(raw,position,position + 8));
         position += 8;
         while (position < raw.length){
             long uid = Parser.parseLong(Arrays.copyOfRange(raw,position,position + 8));
             position += 8;
             fields.add(Field.loadField(this,uid));
         }
         return this;
    }
}
