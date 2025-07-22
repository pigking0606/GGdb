## 基本介绍
DataItem 是一个数据抽象层，它提供了一种在上层模块和底层数据存储之间进行交互的接口。其功能和作用主要包括：

1. **数据存储和访问**：DataItem 存储了数据的具体内容，以及一些相关的元数据信息，如数据的大小、有效标志等。上层模块可以通过 DataItem 对象获取到其中的数据内容，以进行读取、修改或删除等操作。
2. **数据修改和事务管理**：DataItem 提供了一些方法来支持数据的修改操作，并在修改操作前后执行一系列的流程，如保存原始数据、落日志等。这些流程保证了数据修改的原子性和一致性，同时支持事务管理，确保了数据的安全性。
3. **数据共享和内存管理**：DataItem 的数据内容通过 SubArray 对象返回给上层模块，这使得上层模块可以直接访问数据内容而无需进行拷贝。这种数据共享的方式提高了数据的访问效率，同时减少了内存的开销。
4. **缓存管理**：DataItem 对象由底层的 DataManager 缓存管理，通过调用 release() 方法可以释放缓存中的 DataItem 对象，以便回收内存资源，提高系统的性能和效率。

DataItem 提供了一种高层次的数据抽象，隐藏了底层数据存储的细节，为上层模块提供了方便的数据访问和管理接口，同时保证了数据的安全性和一致性。
## 具体实现
DataItem 中保存的数据，结构如下：
```
[ValidFlag] [DataSize] [Data]
```
其中 `**ValidFlag**` 占用 1 字节，标识了该 `**DataItem**` 是否有效。删除一个 `**DataItem**`，只需要简单地将其有效位设置为 0。`**DataSize**` 占用 2 字节，标识了后面 `**Data**` 的长度。
```java
public class DataItemImpl implements DataItem {
    private SubArray raw; //原始数据
    private byte[] oldRaw; //旧的原始数据
    private DataManagerImpl dm; //数据管理器
    private long uid; //唯一标识符
    private Page pg; //页面对象
}
```
### `data()`
返回数据项中的数据部分，返回的是原始数据的引用，而不是数据的拷贝
```java
@Override
public SubArray data() {
    // 返回 [data] 部分
    return new SubArray(raw.raw, raw.start+OF_DATA, raw.end);
}
```
### `before()`
在修改数据项之前调用，用于锁定数据项并保存原始数据
```java
@Override
public void before() {
    wLock.lock();
    pg.setDirty(true);
    //保存原始数据的副本，以便在需要时进行回滚
    System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length); 
}
```
### `unbefore()`
在需要撤销修改时调用，用于恢复原始数据并解锁数据项
```java
@Override
public void unBefore() {
    System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
    wLock.unlock();
}
```
### `after()`
在修改完成之后调用，用于记录日志并解锁数据项
```java
@Override
public void after(long xid) {
    dm.logDataItem(xid, this);
    wLock.unlock();
}

/**
 * 创建一个更新日志。
 *
 * @param xid 事务ID
 * @param di  DataItem对象
 * @return 更新日志，包含日志类型、事务ID、DataItem的唯一标识符、旧原始数据和新原始数据
 */
public static byte[] updateLog(long xid, DataItem di) {
    byte[] logType = {LOG_TYPE_UPDATE}; // 创建一个表示日志类型的字节数组，并设置其值为LOG_TYPE_UPDATE
    byte[] xidRaw = Parser.long2Byte(xid); // 将事务ID转换为字节数组
    byte[] uidRaw = Parser.long2Byte(di.getUid()); // 将DataItem对象的唯一标识符转换为字节数组
    byte[] oldRaw = di.getOldRaw(); // 获取DataItem对象的旧原始数据
    SubArray raw = di.getRaw(); // 获取DataItem对象的新原始数据
    byte[] newRaw = Arrays.copyOfRange(raw.raw, raw.start, raw.end); // 将新原始数据转换为字节数组
    return Bytes.concat(logType, xidRaw, uidRaw, oldRaw, newRaw); // 将所有字节数组连接在一起，形成一个完整的更新日志，并返回这个日志
}

```
### `release()`
在使用完 `**DataItem**`后，需要调用 `release()` 释放调 `DataItem`的缓存
```java
@Override
public void release() {
    dm.releaseDataItem(this);
}
```
