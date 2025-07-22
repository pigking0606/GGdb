## VM的基本定义
VM 层通过 VersionManager 接口，向上层提供功能，如下：
```java
public interface VersionManager {
    byte[] read(long xid, long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    boolean delete(long xid, long uid) throws Exception;

    long begin(int level);
    void commit(long xid) throws Exception;
    void abort(long xid);
}
```
同时，VM 的实现类还被设计为 `**Entry**`** **的缓存，需要继承`** AbstractCache<Entry>**`。需要实现的获取到缓存和从缓存释放的方法很简单：
```java
@Override
protected Entry getForCache(long uid) throws Exception {
    // 核心还是调用dm.read()方法
    Entry entry = Entry.loadEntry(this, uid);
    if (entry == null) {
        throw Error.NullEntryException;
    }
    return entry;
}

@Override
protected void releaseForCache(Entry entry) {
    entry.remove();
}

```
## 具体实现
### `begin()`
开启一个事务，并初始化事务的结构
![vm.begin.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713665382401-39c47f4d-03c8-4044-9103-939a2d3aaee1.png#averageHue=%23fcfbfb&clientId=uec8e7209-dde0-4&from=paste&height=1202&id=u03a89a06&originHeight=1502&originWidth=1902&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=205579&status=done&style=none&taskId=ud7e402c6-c46d-4c40-99a4-34ab16f0388&title=&width=1521.6)
```java
@Override
public long begin(int level) {
    lock.lock(); // 获取锁，防止并发问题
    try {
        long xid = tm.begin(); // 调用事务管理器的begin方法，开始一个新的事务，并获取事务ID
        Transaction t = Transaction.newTransaction(xid, level, activeTransaction); // 创建一个新的事务对象
        activeTransaction.put(xid, t); // 将新的事务对象添加到活动事务的映射中
        return xid; // 返回新的事务ID
    } finally {
        lock.unlock(); // 释放锁
    }
}

// 创建一个新的事务
public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
    Transaction t = new Transaction();
    // 设置事务ID
    t.xid = xid;
    // 设置事务隔离级别
    t.level = level;
    // 如果隔离级别不为0，创建快照
    if (level != 0) {
        t.snapshot = new HashMap<>();
        // 将活跃事务的ID添加到快照中
        for (Long x : active.keySet()) {
            t.snapshot.put(x, true);
        }
    }
    // 返回新创建的事务
    return t;
}
```
### `commit()`
![vm.commit.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713665916032-656a33ad-ad49-42d9-9e16-97f200a2b629.png#averageHue=%23fdfcfc&clientId=uec8e7209-dde0-4&from=paste&height=1026&id=ubf962146&originHeight=1762&originWidth=1322&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=159098&status=done&style=none&taskId=u53df02d8-0a35-4512-b08f-169bc068f76&title=&width=770)
```java
@Override
public void commit(long xid) throws Exception {
    lock.lock(); // 获取锁，防止并发问题
    Transaction t = activeTransaction.get(xid); // 从活动事务中获取事务对象
    lock.unlock(); // 释放锁

    try {
        if (t.err != null) { // 如果事务已经出错，那么抛出错误
            throw t.err;
        }
    } catch (NullPointerException n) { // 如果事务对象为null，打印事务ID和活动事务的键集，然后抛出异常
        System.out.println(xid);
        System.out.println(activeTransaction.keySet());
        Panic.panic(n);
    }

    lock.lock(); // 获取锁，防止并发问题
    activeTransaction.remove(xid); // 从活动事务中移除这个事务
    lock.unlock(); // 释放锁

    lt.remove(xid); // 从锁表中移除这个事务的锁
    tm.commit(xid); // 调用事务管理器的commit方法，进行事务的提交操作
}

```
### `abort()`
`abort` 事务的方法则有两种，手动和自动。手动指的是调用 `**abort()**` 方法，而自动，则是在事务被检测出出现死锁时，会自动撤销回滚事务；或者出现版本跳跃时，也会自动回滚
![vm.abort.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713666499554-c1c6d692-c599-4f02-9e66-cc1a50fa7539.png#averageHue=%23fcfafa&clientId=uec8e7209-dde0-4&from=paste&height=1214&id=u0c6442e7&originHeight=1517&originWidth=797&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=88378&status=done&style=none&taskId=u7581bd30-11e1-4fd1-b4ca-abd1f014ee4&title=&width=637.6)
```java
@Override
// 公开的abort方法，用于中止一个事务
public void abort(long xid) {
    // 调用内部的abort方法，autoAborted参数为false表示这不是一个自动中止的事务
    internAbort(xid, false);
}

// 内部的abort方法，处理事务的中止
private void internAbort(long xid, boolean autoAborted) {
    // 获取锁，防止并发问题
    lock.lock();
    // 从活动事务中获取事务对象
    Transaction t = activeTransaction.get(xid);
    // 如果这不是一个自动中止的事务，那么从活动事务中移除这个事务
    if (!autoAborted) {
        activeTransaction.remove(xid);
    }
    // 释放锁
    lock.unlock();

    // 如果事务已经被自动中止，那么直接返回，不做任何处理
    if (t.autoAborted) return;
    // 从锁表中移除这个事务的锁
    lt.remove(xid);
    // 调用事务管理器的abort方法，进行事务的中止操作
    tm.abort(xid);
}
```
### `read()`
`read() `方法读取一个 `entry`，需要注意判断可见性
![vm.read.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713667325823-9289ebff-31cd-42de-92dc-819a6eaa2173.png#averageHue=%23fdfcfc&clientId=uec8e7209-dde0-4&from=paste&height=565&id=u824deda3&originHeight=706&originWidth=1021&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=42762&status=done&style=none&taskId=u5985195c-5deb-463d-836c-3a31f4babeb&title=&width=816.8)
```java
@Override
public byte[] read(long xid, long uid) throws Exception {
    lock.lock(); // 获取锁，防止并发问题
    Transaction t = activeTransaction.get(xid); // 从活动事务中获取事务对象
    lock.unlock(); // 释放锁

    if (t.err != null) { // 如果事务已经出错，那么抛出错误
        throw t.err;
    }

    Entry entry = null;
    try {
        entry = super.get(uid); // 尝试获取数据项
    } catch (Exception e) {
        if (e == Error.NullEntryException) { // 如果数据项不存在，那么返回null
            return null;
        } else { // 如果出现其他错误，那么抛出错误
            throw e;
        }
    }
    try {
        // 在事务隔离级别中讲解了该方法
        if (Visibility.isVisible(tm, t, entry)) { // 如果数据项对当前事务可见，那么返回数据项的数据
            return entry.data();
        } else { // 如果数据项对当前事务不可见，那么返回null
            return null;
        }
    } finally {
        entry.release(); // 释放数据项
    }
}
```
### `insert()`
将数据包裹成 `Entry`，然后交给 DM 插入即可
```java
@Override
public long insert(long xid, byte[] data) throws Exception {
    lock.lock(); // 获取锁，防止并发问题
    Transaction t = activeTransaction.get(xid); // 从活动事务中获取事务对象
    lock.unlock(); // 释放锁

    if (t.err != null) { // 如果事务已经出错，那么抛出错误
        throw t.err;
    }

    byte[] raw = Entry.wrapEntryRaw(xid, data); // 将事务ID和数据包装成一个新的数据项
    return dm.insert(xid, raw); // 调用数据管理器的insert方法，插入新的数据项，并返回数据项的唯一标识符
}
```
### `delete()`
![VersionManager.delete().png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713668823978-285bd11c-93fd-4c05-8005-7ffb18bf28a7.png#averageHue=%23f6f6f6&clientId=ue3f527e9-6e2d-4&from=paste&height=1834&id=u7f45e790&originHeight=2292&originWidth=2020&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=322005&status=done&style=none&taskId=ua7d2b092-da74-4cc0-9454-5b4c214c440&title=&width=1616)
```java
@Override
// 删除一个数据项的方法
public boolean delete(long xid, long uid) throws Exception {
    // 获取锁，防止并发问题
    lock.lock();
    // 从活动事务中获取事务对象
    Transaction t = activeTransaction.get(xid);
    // 释放锁
    lock.unlock();

    // 如果事务已经出错，那么抛出错误
    if (t.err != null) {
        throw t.err;
    }
    Entry entry = null;
    try {
        // 尝试获取数据项
        entry = super.get(uid);
    } catch (Exception e) {
        // 如果数据项不存在，那么返回false
        if (e == Error.NullEntryException) {
            return false;
        } else {
            // 如果出现其他错误，那么抛出错误
            throw e;
        }
    }
    try {
        // 如果数据项对当前事务不可见，那么返回false
        if (!Visibility.isVisible(tm, t, entry)) {
            return false;
        }
        Lock l = null;
        try {
            // 尝试为数据项添加锁
            l = lt.add(xid, uid);
        } catch (Exception e) {
            // 如果出现并发更新的错误，那么中止事务，并抛出错误
            t.err = Error.ConcurrentUpdateException;
            internAbort(xid, true);
            t.autoAborted = true;
            throw t.err;
        }
        // 如果成功获取到锁，那么锁定并立即解锁
        if (l != null) {
            l.lock();
            l.unlock();
        }

        // 如果数据项已经被当前事务删除，那么返回false
        if (entry.getXmax() == xid) {
            return false;
        }

        // 如果数据项的版本被跳过，那么中止事务，并抛出错误
        if (Visibility.isVersionSkip(tm, t, entry)) {
            t.err = Error.ConcurrentUpdateException;
            internAbort(xid, true);
            t.autoAborted = true;
            throw t.err;
        }

        // 设置数据项的xmax为当前事务的ID，表示数据项被当前事务删除
        entry.setXmax(xid);
        // 返回true，表示删除操作成功
        return true;

    } finally {
        // 释放数据项
        entry.release();
    }
}
```
