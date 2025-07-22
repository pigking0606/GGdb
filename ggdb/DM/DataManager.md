## 基本介绍
DataManager（DM）是数据库管理系统中的一层，主要负责底层数据的管理和操作。其功能和作用包括：

1. **数据缓存和管理**：DataManager 实现了对 DataItem 对象的缓存管理，通过缓存管理，可以提高数据的访问效率，并减少对底层存储的频繁访问，从而提高系统的性能。
2. **数据访问和操作**：DataManager 提供了读取、插入和修改等数据操作方法，上层模块可以通过这些方法对数据库中的数据进行操作和管理。
3. **事务管理**：DataManager 支持事务的管理，通过事务管理，可以保证对数据的修改是原子性的，并且在事务提交或回滚时能够保持数据的一致性和完整性。
4. **日志记录和恢复**：DataManager 在数据修改操作前后会执行一系列的流程，包括日志记录和数据恢复等操作，以确保数据的安全性和可靠性，即使在系统崩溃或异常情况下也能够保证数据的完整性。
5. **页面索引管理**：DataManager 中实现了页面索引管理功能，通过页面索引可以快速定位到合适的空闲空间，从而提高数据插入的效率和性能。
6. **文件初始化和校验**：DataManager 在创建和打开数据库文件时，会进行文件的初始化和校验操作，以确保文件的正确性和完整性，同时在文件关闭时会执行相应的清理操作。
7. **资源管理和释放**：DataManager 在关闭时会执行资源的释放和清理操作，包括缓存和日志的关闭，以及页面的释放和页面索引的清理等。

DataManager 在数据库管理系统中扮演着重要的角色，负责底层数据的管理和操作，为上层模块提供了方便的数据访问和操作接口，同时通过事务管理和日志记录等功能保证了数据的安全性和可靠性。
> 注：以上内容来自GPT

## 具体实现
`DataManager` 是 `DM` 层直接对外提供方法的类，同时，也实现成 `DataItem` 对象的缓存。`DataItem` 存储的 `**key**`，是由页号和页内偏移组成的一个 **8** 字节无符号整数，页号和偏移各占 **4** 字节。
### Uid生成以及解析
> 初始化：假设是从第二个页面开始的，并且偏移量为0
> **pgno: 2;**
> **offset: 0;**

1. 先通过页面编号以及偏移量生成唯一标识 `uid`

![types.createuid.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713321777190-54f37d34-0e20-4cb8-9180-225d752920f6.png#averageHue=%23f8f7f7&clientId=u7408379f-9c6c-4&from=paste&height=467&id=ud25601cc&originHeight=584&originWidth=1466&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=36427&status=done&style=none&taskId=u8f7db4ca-6d54-46b9-a731-5b5efeaee38&title=&width=1172.8)
```java
public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long)pgno;
        long u1 = (long)offset;
        return u0 << 32 | u1; //或运算是全0则0，见1则1
    }
}
```

2. 从 uid 中提取出偏移量（**offset**）

![datamanger.offset.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713322916134-fb59207b-faca-4c01-a0d2-5c8b90391616.png#averageHue=%23f8f7f7&clientId=uc36d585a-ef7a-4&from=paste&height=334&id=ubbf04be4&originHeight=417&originWidth=1502&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=26238&status=done&style=none&taskId=u8fbf0340-c2de-49c5-8316-c2b7a599a65&title=&width=1201.6)
```java
// 从 uid 中提取出偏移量（offset），这是通过位操作实现的，偏移量是 uid 的低16位
// & 运算：有0则0，全1才1
short offset = (short) (uid & ((1L << 16) - 1)); 
```

3. 将 uid 右移32位，再获取页面编号

![datamanager.pgno.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713323518540-fc7509b9-df87-48c9-ac8a-cdc432b3a2b0.png#averageHue=%23f8f7f7&clientId=uc36d585a-ef7a-4&from=paste&height=4008&id=u95bb1d64&originHeight=5010&originWidth=12610&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=1524578&status=done&style=none&taskId=u2338447a-0aff-4a1b-9e7d-f4f96513611&title=&width=10088)
```java
// 将 uid 右移32位，以便接下来提取出页面编号（pgno）
uid >>>= 32;
// 从 uid 中提取出页面编号（pgno），页面编号是 uid 的高32位
// & 运算：有0则0，全1才1
int pgno = (int) (uid & ((1L << 32) - 1));
```
### `getForCache()`
也是继承自`AbstractCache`，只需要从 key 中解析出页号，从 pageCache 中获取到页面，再根据偏移，解析出 DataItem 即可
```java
@Override
protected DataItem getForCache(long uid) throws Exception {
    // 从 uid 中提取出偏移量（offset），这是通过位操作实现的，偏移量是 uid 的低16位
    short offset = (short) (uid & ((1L << 16) - 1));
    // 将 uid 右移32位，以便接下来提取出页面编号（pgno）
    uid >>>= 32;
    // 从 uid 中提取出页面编号（pgno），页面编号是 uid 的高32位
    int pgno = (int) (uid & ((1L << 32) - 1));
    // 使用页面缓存（pc）的 getPage(int pgno) 方法根据页面编号获取一个 Page 对象
    Page pg = pc.getPage(pgno);
    // 使用 DataItem 接口的静态方法 parseDataItem(Page pg, short offset, DataManagerImpl dm)
    // 根据获取到的 Page 对象、偏移量和当前的 DataManagerImpl 对象（this）解析出一个 DataItem 对象，并返回这个对象
    return DataItem.parseDataItem(pg, offset, this);
}
```
### `releaseForCache()`
DataItem 缓存释放，需要将 DataItem 写回数据源，由于对文件的读写是以页为单位进行的，只需要将 DataItem 所在的页 release 即可：
```java
@Override
protected void releaseForCache(DataItem di) {
    di.page().release();
}
```
### DataManager初始化
对于`**DataManager**`文件的创建有两种流程，一种是从已有文件创建`**DataManager**`**，**另外一种是从空文件创建`**DataManager**`**。**对于两者的不同主要在于第一页的初始化和校验问题：

1. 从空文件创建首先需要对第一页进行初始化
2. 而从已有文件创建，则需要对第一页进行校验，来判断是否需要执行恢复流程，并重新对第一页生成随机字节
#### 从空文件创建`create()`
![DataManager.create( ).png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713340034399-fe79bad6-74ee-47a0-9148-4a22f525b61b.png#averageHue=%23f6f6f6&clientId=uc36d585a-ef7a-4&from=paste&height=973&id=u32cbd9d0&originHeight=1216&originWidth=2368&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=187442&status=done&style=none&taskId=u9b92a73a-651a-451a-a79c-e08fec84269&title=&width=1894.4)
```java
// 静态方法，用于创建DataManager实例
public static DataManager create(String path, long mem, TransactionManager tm) {
    // 创建一个PageCache实例，path是文件路径，mem是内存大小
    PageCache pc = PageCache.create(path, mem);
    // 创建一个Logger实例，path是文件路径
    Logger lg = Logger.create(path);

    // 创建一个DataManagerImpl实例，pc是PageCache实例，lg是Logger实例，tm是TransactionManager实例
    DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
    // 初始化PageOne
    dm.initPageOne();
    // 返回创建的DataManagerImpl实例
    return dm;
}
```
#### 从已有文件创建`open()`
![DataManager.open( ).png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713341576533-16dc5caf-ed3b-42a8-94bb-eb10e3a0551e.png#averageHue=%23f8f8f8&clientId=ue2bd1640-0df9-4&from=paste&height=1200&id=uf41527aa&originHeight=1500&originWidth=3222&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=275393&status=done&style=none&taskId=u143ff4c8-7e60-4397-94d6-a15386d9320&title=&width=2577.6)
```java
// 静态方法，用于打开已存在的DataManager实例
public static DataManager open(String path, long mem, TransactionManager tm) {
    // 打开一个PageCache实例，path是文件路径，mem是内存大小
    PageCache pc = PageCache.open(path, mem);
    // 打开一个Logger实例，path是文件路径
    Logger lg = Logger.open(path);
    // 创建一个DataManagerImpl实例，pc是PageCache实例，lg是Logger实例，tm是TransactionManager实例
    DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
    // 加载并检查PageOne，如果检查失败，则进行恢复操作
    if (!dm.loadCheckPageOne()) {
        Recover.recover(tm, lg, pc);
    }
    // 填充PageIndex，遍历从第二页开始的每一页，将每一页的页面编号和空闲空间大小添加到 PageIndex 中
    dm.fillPageIndex();
    // 设置PageOne为打开状态
    PageOne.setVcOpen(dm.pageOne);
    // 将PageOne立即写入到磁盘中，确保PageOne的数据被持久化
    dm.pc.flushPage(dm.pageOne);

    // 返回创建的DataManagerImpl实例
    return dm;
}
```
### DataManager 提供的三个功能
`**DM**`层主要提供了三个功能供上层使用，分别是读、插入和修改。由于修改是通过读出的 `**DataItem**` 实现的，也就是说 `**DataManager**` 只需要 `**read()**` 和 `**insert()**` 方法；
#### `read（）`
`**read（）**`** **是根据 `UID` 从缓存中获取的 `**DataItem**`，并校验有效位；
```java
@Override
public DataItem read(long uid) throws Exception {
    //从缓存页面中读取到DataItemImpl
    DataItemImpl di = (DataItemImpl) super.get(uid);
    //校验di是否有效
    if (!di.isValid()) {
        // 无效释放缓存
        di.release();
        return null;
    }
    return di;
}

@Override
protected DataItem getForCache(long uid) throws Exception {
    // 从 uid 中提取出偏移量（offset），这是通过位操作实现的，偏移量是 uid 的低16位
    short offset = (short) (uid & ((1L << 16) - 1));
    // 将 uid 右移32位，以便接下来提取出页面编号（pgno）
    uid >>>= 32;
    // 从 uid 中提取出页面编号（pgno），页面编号是 uid 的高32位
    int pgno = (int) (uid & ((1L << 32) - 1));
    // 使用页面缓存（pc）的 getPage(int pgno) 方法根据页面编号获取一个 Page 对象
    Page pg = pc.getPage(pgno);
    // 使用 DataItem 接口的静态方法 parseDataItem(Page pg, short offset, DataManagerImpl dm)
    // 根据获取到的 Page 对象、偏移量和当前的 DataManagerImpl 对象（this）解析出一个 DataItem 对象，并返回这个对象
    return DataItem.parseDataItem(pg, offset, this);
}
```
#### `insert()`
在 `**pageIndex**` 中获取一个足以存储插入内容的页面的页号，获取页面后，首先需要写入插入日志，接着才可以通过 `**pageX**` 插入数据，并返回插入位置的偏移。最后需要将页面信息重新插入 `**pageIndex**`。
![DataManagerImpl.insert( ).png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713352917187-f498b6a7-34c7-4d90-a21b-499f85c6a041.png#averageHue=%23f5f5f5&clientId=ue2bd1640-0df9-4&from=paste&height=2214&id=u637e5c9b&originHeight=2768&originWidth=2192&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=458457&status=done&style=none&taskId=u96d09852-6b22-4c4c-8064-685c51aa29c&title=&width=1753.6)
```java
@Override
public long insert(long xid, byte[] data) throws Exception {
    // 将输入的数据包装成DataItem的原始格式
    byte[] raw = DataItem.wrapDataItemRaw(data);
    // 如果数据项的大小超过了页面的最大空闲空间，抛出异常
    if (raw.length > PageX.MAX_FREE_SPACE) {
        throw Error.DataTooLargeException;
    }

    // 初始化一个页面信息对象
    PageInfo pi = null;
    // 尝试5次找到一个可以容纳新数据项的页面
    for (int i = 0; i < 5; i++) {
        // 从页面索引中选择一个可以容纳新数据项的页面
        pi = pIndex.select(raw.length);
        // 如果找到了合适的页面，跳出循环
        if (pi != null) {
            break;
        } else {
            // 如果没有找到合适的页面，创建一个新的页面，并将其添加到页面索引中
            int newPgno = pc.newPage(PageX.initRaw());
            pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
        }
    }
    // 如果还是没有找到合适的页面，抛出异常
    if (pi == null) {
        throw Error.DatabaseBusyException;
    }

    // 初始化一个页面对象
    Page pg = null;
    // 初始化空闲空间大小为0
    int freeSpace = 0;
    try {
        // 获取页面信息对象中的页面
        pg = pc.getPage(pi.pgno);
        // 生成插入日志
        byte[] log = Recover.insertLog(xid, pg, raw);
        // 将日志写入日志文件
        logger.log(log);

        // 在页面中插入新的数据项，并获取其在页面中的偏移量
        short offset = PageX.insert(pg, raw);

        // 释放页面
        pg.release();
        // 返回新插入的数据项的唯一标识符
        return Types.addressToUid(pi.pgno, offset);

    } finally {
        // 将页面重新添加到页面索引中
        if (pg != null) {
            pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
        } else {
            pIndex.add(pi.pgno, freeSpace);
        }
    }
}

/**
 *  返回一个完整的 DataItem 结构数据
 *  dataItem 结构如下：
 *  [ValidFlag] [DataSize] [Data]
 *  ValidFlag 1字节，0为合法，1为非法
 *  DataSize  2字节，标识Data的长度
 * @param raw
 * @return
 */
public static byte[] wrapDataItemRaw(byte[] raw) {
    byte[] valid = new byte[1]; //证明此时为非法数据
    byte[] size = Parser.short2Byte((short)raw.length); //计算数据字节大小
    return Bytes.concat(valid, size, raw); //拼接DataItem 结构数据
}

/**
 * 根据给定的空间大小选择一个 PageInfo 对象。
 *
 * @param spaceSize 需要的空间大小
 * @return 一个 PageInfo 对象，其空闲空间大于或等于给定的空间大小。如果没有找到合适的 PageInfo，返回 null。
 */
public PageInfo select(int spaceSize) {
    lock.lock(); // 获取锁，确保线程安全
    try {
        int number = spaceSize / THRESHOLD; // 计算需要的空间大小对应的区间编号
        // 此处+1主要为了向上取整
        /*
            1、假需要存储的字节大小为5168，此时计算出来的区间号是25，但是25*204=5100显然是不满足条件的
            2、此时向上取整找到 26，而26*204=5304，是满足插入条件的
         */
        if (number < INTERVALS_NO) number++; // 如果计算出的区间编号小于总的区间数，编号加一
        while (number <= INTERVALS_NO) { // 从计算出的区间编号开始，向上寻找合适的 PageInfo
            if (lists[number].size() == 0) { // 如果当前区间没有 PageInfo，继续查找下一个区间
                number++;
                continue;
            }
            return lists[number].remove(0); // 如果当前区间有 PageInfo，返回第一个 PageInfo，并从列表中移除
        }
        return null; // 如果没有找到合适的 PageInfo，返回 null
    } finally {
        lock.unlock(); // 释放锁
    }
}

// 定义一个静态方法，用于创建插入日志
public static byte[] insertLog(long xid, Page pg, byte[] raw) {
    // 创建一个表示日志类型的字节数组，并设置其值为LOG_TYPE_INSERT
    byte[] logTypeRaw = {LOG_TYPE_INSERT};
    // 将事务ID转换为字节数组
    byte[] xidRaw = Parser.long2Byte(xid);
    // 将页面编号转换为字节数组
    byte[] pgnoRaw = Parser.int2Byte(pg.getPageNumber());
    // 获取页面的第一个空闲空间的偏移量，并将其转换为字节数组
    byte[] offsetRaw = Parser.short2Byte(PageX.getFSO(pg));
    // 将所有字节数组连接在一起，形成一个完整的插入日志，并返回这个日志
    return Bytes.concat(logTypeRaw, xidRaw, pgnoRaw, offsetRaw, raw);
}

// 将raw插入pg中，返回插入位置
public static short insert(Page pg, byte[] raw) {
    pg.setDirty(true); // 将pg的dirty标志设置为true，表示pg的数据已经被修改
    short offset = getFSO(pg.getData()); // 获取pg的空闲空间偏移量
    System.arraycopy(raw, 0, pg.getData(), offset, raw.length); // 将raw的数据复制到pg的数据中的offset位置
    setFSO(pg.getData(), (short) (offset + raw.length)); // 更新pg的空闲空间偏移量
    return offset; // 返回插入位置
}
```
### `close()`
DataManager 正常关闭时，需要执行缓存和日志的关闭流程，还需要设置第一页的字节校验：
```java
@Override
public void close() {
    super.close();
    logger.close();

    PageOne.setVcClose(pageOne);
    pageOne.release();
    pc.close();
}
```
