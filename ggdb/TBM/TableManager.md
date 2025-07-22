## 基本定义
- **TableManager**中的方法直接返回执行结果，比如错误信息或者可读的结果信息的字节数组。
- 这些方法的实现相对简单，主要是调用（VM）相关的方法来完成数据库操作。
- 在创建新表时，采用了头插法，即每次创建表都将新表插入到链表的头部。这意味着最新创建的表会成为链表的第一个元素。由于使用了头插法，每次创建表都会改变表链表的头部，因此需要更新**Booter**文件，以便记录新的头表的UID。
- 在创建TBM对象时，会初始化表信息
```java
public interface TableManager {
    BeginRes begin(Begin begin);
    byte[] commit(long xid) throws Exception;
    byte[] abort(long xid);

    byte[] show(long xid);
    byte[] create(long xid, Create create) throws Exception;

    byte[] insert(long xid, Insert insert) throws Exception;
    byte[] read(long xid, Select select) throws Exception;
    byte[] update(long xid, Update update) throws Exception;
    byte[] delete(long xid, Delete delete) throws Exception;
}

```
```java
public class TableManagerImpl implements TableManager {
    VersionManager vm; // 版本管理器，用于管理事务的版本
    DataManager dm; // 数据管理器，用于管理数据的存储和读取
    private Booter booter; // 启动信息管理器，用于管理数据库启动信息
    private Map<String, Table> tableCache; // 表缓存，用于缓存已加载的表，键是表名，值是表对象
    private Map<Long, List<Table>> xidTableCache; // 事务表缓存，用于缓存每个事务修改过的表，键是事务ID，值是表对象列表
    private Lock lock; // 锁，用于同步多线程操作
    
    TableManagerImpl(VersionManager vm, DataManager dm, Booter booter) {
        this.vm = vm;
        this.dm = dm;
        this.booter = booter;
        this.tableCache = new HashMap<>();
        this.xidTableCache = new HashMap<>();
        lock = new ReentrantLock();
        loadTables();
    }
}
```
### ` loadTables()`
```java
/**
 * 加载所有的数据库表。
 */
private void loadTables() {
    // 获取第一个表的UID
    long uid = firstTableUid();
    // 当UID不为0时，表示还有表需要加载
    while (uid != 0) {
        // 加载表，并获取表的UID
        Table tb = Table.loadTable(this, uid);
        // 更新UID为下一个表的UID
        uid = tb.nextUid;
        // 将加载的表添加到表缓存中
        tableCache.put(tb.name, tb);
    }
}

/**
 * 获取 Botter 文件的前八位字节
 * @return
 */
private long firstTableUid() {
    byte[] raw = booter.load();
    return Parser.parseLong(raw);
}
```
### `create()`
这里主要讲解一下 `create`方法，其他方法都是调用 VM 层
![tablemanager.create.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713754983616-62951908-a923-48c3-9736-0fd3a44e6902.png#averageHue=%23fdfcfc&clientId=u57a558c8-b293-4&from=paste&height=938&id=u85502c7a&originHeight=1172&originWidth=802&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=61009&status=done&style=none&taskId=u80853d11-e08c-4c0d-a731-4ada314d09f&title=&width=641.6)
```java
@Override
public byte[] create(long xid, Create create) throws Exception {
    // 加锁，防止多线程并发操作
    lock.lock();
    try {
        // 检查表是否已存在，如果存在则抛出异常
        if (tableCache.containsKey(create.tableName)) {
            throw Error.DuplicatedTableException;
        }
        // 创建新的表，并获取表的UID
        Table table = Table.createTable(this, firstTableUid(), xid, create);
        // 更新第一个表的UID
        updateFirstTableUid(table.uid);
        // 将新创建的表添加到表缓存中
        tableCache.put(create.tableName, table);
        // 如果事务表缓存中没有当前事务ID的条目，则添加一个新的条目
        if (!xidTableCache.containsKey(xid)) {
            xidTableCache.put(xid, new ArrayList<>());
        }
        // 将新创建的表添加到当前事务的表列表中
        xidTableCache.get(xid).add(table);
        // 返回创建成功的消息
        return ("create " + create.tableName).getBytes();
    } finally {
        // 解锁
        lock.unlock();
    }
}
```
