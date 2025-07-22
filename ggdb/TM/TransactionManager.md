## XID文件
1. **XID 的定义和规则：**
   - 每个事务都有一个唯一的事务标识符 XID，从 1 开始递增，并且 XID 0 被特殊定义为超级事务（Super Transaction）。
   - XID 0 用于表示在没有申请事务的情况下进行的操作，其状态永远是 committed。
2. **事务的状态：**
   - 每个事务可以处于三种状态之一：active（正在进行，尚未结束）、committed（已提交）和aborted（已撤销或回滚）。
3. **XID 文件的结构和管理：**
   - TransactionManager 负责维护一个 XID 格式的文件，用于记录各个事务的状态。
   - XID 文件中为每个事务分配了一个字节的空间，用来保存其状态。
   - XID 文件的头部保存了一个 8 字节的数字，记录了这个 XID 文件管理的事务的个数。
   - 因此，事务 XID 在文件中的状态存储在 (XID-1)+8 字节的位置处，其中 XID-1 是因为 XID 0（超级事务）的状态不需要记录。

在`TransactionManager`中提供了一些接口供其他模块调用，用来创建事务和查询事务的状态；
```java
public interface TransactionManager {
    long begin(); //开启事务
    void commit(long xid);  //提交事务
    void abort(long xid);   //撤销或回滚事务
    boolean isActive(long xid); //查询一个事务的状态是否正在运行
    boolean isCommitted(long xid);  //查询一个事务的状态是否已经提交
    boolean isAborted(long xid);    //查询一个事务的状态是否撤销或回滚
    void close();   //关闭事务
}
```
## 如何实现TM？
### 定义常量
```java
public class TransactionManagerImpl implements TransactionManager {

    // XID文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;
    // 每个事务的占用长度
    private static final int XID_FIELD_SIZE = 1;

    // 事务的三种状态
  

    // 超级事务，永远为commited状态
    public static final long SUPER_XID = 0;

    // XID文件后缀
    static final String XID_SUFFIX = ".xid";
    
}
```
### checkXIDCounter
在构造函数创建了一个 TransactionManager 之后，首先要对 XID 文件进行校验，以保证这是一个合法的 XID 文件。校验的方式也很简单，通过文件头的 8 字节数字反推文件的理论长度，与文件的实际长度做对比。如果不同则认为 XID 文件不合法。对于校验没有通过的，会直接通过 panic 方法，强制停机。在一些基础模块中出现错误都会如此处理，无法恢复的错误只能直接停机。
![checkXidCounter.jpg](https://cdn.nlark.com/yuque/0/2024/jpeg/22796888/1713080408220-5daf7b9f-4683-4a73-80eb-23e5aa8213df.jpeg#averageHue=%23fdfdfc&clientId=u7a44b2f6-3158-4&from=paste&height=769&id=ua47dde84&originHeight=961&originWidth=667&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=38141&status=done&style=none&taskId=ua7fd9621-a4b8-4741-8943-b0e83550159&title=&width=533.6)
```java
/**
 * 检查XID文件是否合法
 * 读取XID_FILE_HEADER中的xidcounter，根据它计算文件的理论长度，对比实际长度
 */
private void checkXIDCounter() {
    // 初始化文件长度为0
    long fileLen = 0;
    try {
        // 获取文件的长度，RandomAccessFile在构造函数中赋值
        fileLen = file.length();
    } catch (IOException e1) {
        // 如果出现异常，抛出BadXIDFileException错误
        Panic.panic(Error.BadXIDFileException);
    }

    // 如果文件长度小于XID头部长度，抛出BadXIDFileException错误
    if (fileLen < LEN_XID_HEADER_LENGTH) {
        Panic.panic(Error.BadXIDFileException);
    }

    // 分配一个长度为XID头部长度的ByteBuffer
    ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
    try {
        // 将文件通道的位置设置为0
        fc.position(0);
        // 从文件通道读取数据到ByteBuffer
        fc.read(buf);
    } catch (IOException e) {
        // 如果出现异常，抛出错误
        Panic.panic(e);
    }
    // 将ByteBuffer的内容解析为长整型，作为xidCounter
    this.xidCounter = Parser.parseLong(buf.array()); 
    // 计算xidCounter+1对应的XID位置
    long end = getXidPosition(this.xidCounter + 1);
    // 如果计算出的XID位置与文件长度不符，抛出BadXIDFileException错误
    if (end != fileLen) {
        Panic.panic(Error.BadXIDFileException);
    }
}

/**
 * 将数组前八位转换成长整数
 * @param buf 需要转换的字节数组
 * @return 转换后的数据
 */
public static long parseLong(byte[] buf) {
    ByteBuffer buffer = ByteBuffer.wrap(buf, 0, 8);
    return buffer.getLong();
}

@Test
public void testBufferGetLong(){
    
    // 创建一个包含8个字节的字节数组
    //因为long 在Java中占用8个字节，每个字节占用8位，一下数组可以转换成一个long数字
    // 00000000 00000000 00000000 00000000 00000000 00000000 00001010 00000001
    // 1010 00000001 --> 2561
    byte[] byteArray = new byte[]{0, 0, 0, 0, 0, 0, 10, 1};
    // 使用ByteBuffer.wrap方法将字节数组包装为一个ByteBuffer对象
    ByteBuffer buffer = ByteBuffer.wrap(byteArray);

    // 使用getLong方法从ByteBuffer中读取一个长整型数
    long longValue = buffer.getLong();

    // 输出读取的长整型数
    System.out.println("The long value is: " + longValue);
}
```
### getXidPosition
根据事务xid取得其在xid文件中对应的位置
```java
// 根据事务xid取得其在xid文件中对应的位置
private long getXidPosition(long xid) {
    return LEN_XID_HEADER_LENGTH + (xid - 1) * XID_FIELD_SIZE;
}
```
### begin()
`**begin()**` 方法会开始一个事务，更具体的，首先设置 `xidCounter+1` 事务的状态为 `active`，随后 `xidCounter` 自增，并更新文件头。
```java
// 开始一个事务，并返回XID
public long begin() {
    // 锁定计数器，防止并发问题
    counterLock.lock();
    try {
        // xidCounter是当前事务的计数器，每开始一个新的事务，就将其加1
        long xid = xidCounter + 1;
        // 调用updateXID方法，将新的事务ID和事务状态（这里是活动状态）写入到XID文件中
        updateXID(xid, FIELD_TRAN_ACTIVE);
        // 调用incrXIDCounter方法，将事务计数器加1，并更新XID文件的头部信息
        incrXIDCounter();
        // 返回新的事务ID
        return xid;
    } finally {
        // 释放锁
        counterLock.unlock();
    }
}
```
### updateXid
更新事务`ID`状态,`**commit()**`和** abort() **方法就可以直接借助 `**updateXID()**` 方法实现。
![updateXid.jpg](https://cdn.nlark.com/yuque/0/2024/jpeg/22796888/1713082395597-b77f162c-dbde-4ab2-9e85-fd92052c9215.jpeg#averageHue=%23fefefe&clientId=u7a44b2f6-3158-4&from=paste&height=766&id=ufd40c25e&originHeight=958&originWidth=528&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=23256&status=done&style=none&taskId=u04332b9d-5d27-4dce-8d48-f82c335d90f&title=&width=422.4)
```java
// 更新xid事务的状态为status
private void updateXID(long xid, byte status) {
    // 获取事务xid在xid文件中对应的位置
    long offset = getXidPosition(xid);
    // 创建一个长度为XID_FIELD_SIZE的字节数组
    byte[] tmp = new byte[XID_FIELD_SIZE];
    // 将事务状态设置为status
    tmp[0] = status;
    // 使用字节数组创建一个ByteBuffer
    ByteBuffer buf = ByteBuffer.wrap(tmp);
    try {
        // 将文件通道的位置设置为offset
        fc.position(offset);
        // 将ByteBuffer中的数据写入到文件通道
        fc.write(buf);
    } catch (IOException e) {
        // 如果出现异常，调用Panic.panic方法处理
        Panic.panic(e);
    }
    try {
        // 强制将文件通道中的所有未写入的数据写入到磁盘
        fc.force(false);
    } catch (IOException e) {
        // 如果出现异常，调用Panic.panic方法处理
        Panic.panic(e);
    }
}

```
### incrXIDCounter
![incrXIDCounter.jpg](https://cdn.nlark.com/yuque/0/2024/jpeg/22796888/1713083380792-9639a507-941f-4f76-a6e2-7409c4fdfd67.jpeg#averageHue=%23fefefe&clientId=u7a44b2f6-3158-4&from=paste&height=514&id=ub5a13dca&originHeight=643&originWidth=583&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=22506&status=done&style=none&taskId=u02067308-36b2-415a-86dd-c99a3409416&title=&width=466.4)
```java
// 将XID加一，并更新XID Header
private void incrXIDCounter() {
    // 事务总数加一
    xidCounter++;
    // 将新的事务总数转换为字节数组，并用ByteBuffer包装
    ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
    try {
        // 将文件通道的位置设置为0，即文件的开始位置
        fc.position(0);
        // 将ByteBuffer中的数据写入到文件通道，即更新了XID文件的头部信息
        fc.write(buf);
    } catch (IOException e) {
        // 如果出现异常，调用Panic.panic方法处理
        Panic.panic(e);
    }
    try {
        // 强制将文件通道中的所有未写入的数据写入到磁盘
        fc.force(false);
    } catch (IOException e) {
        // 如果出现异常，调用Panic.panic方法处理
        Panic.panic(e);
    }
}


/**
 * 将长整型值写入到字节缓冲区，将其转成为8字节的二进制形式，然后将这个8个字节写入到字节缓冲区
 * @param value
 * @return
 */
public static byte[] long2Byte(long value) {
    return ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(value).array();
}
```
### checkXID
`**isActive()、isCommitted() **`和 `**isAborted()**` 都是检查一个 **xid** 的状态
![checkXID.png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713085501720-c4b08e5f-824a-4e7e-8a43-bfc44ce8711a.png#averageHue=%23fdfdfd&clientId=u7a44b2f6-3158-4&from=paste&height=466&id=ue7927fa9&originHeight=582&originWidth=552&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=25024&status=done&style=none&taskId=u87b8ece8-aa80-43a1-a35b-143c3760716&title=&width=441.6)
```java
// 定义一个方法，接收一个事务ID（xid）和一个状态（status）作为参数
private boolean checkXID(long xid, byte status) {
    // 计算事务ID在XID文件中的位置
    long offset = getXidPosition(xid);
    // 创建一个新的字节缓冲区（ByteBuffer），长度为XID_FIELD_SIZE
    ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
    try {
        // 将文件通道的位置设置为offset
        fc.position(offset);
        // 从文件通道读取数据到字节缓冲区
        fc.read(buf);
    } catch (IOException e) {
        // 如果出现异常，调用Panic.panic方法处理
        Panic.panic(e);
    }
    // 检查字节缓冲区的第一个字节是否等于给定的状态
    // 如果等于，返回true，否则返回false
    return buf.array()[0] == status;
}
```
## 总结
TM模块主要用于**管理事务，包括开始、提交、回滚事务**，以及检查事务的状态。在类中需要定义一些常量来管理事务如`LEN_XID_HEADER_LENGTH、XID_FIELD_SIZE、FIELD_TRAN_ACTIVE、FIELD_TRAN_COMMITTED、FIELD_TRAN_ABORTED、SUPER_XID和XID_SUFFIX`，分别表示XID文件头长度、每个事务的占用长度、事务的三种状态、超级事务、XID文件后缀。
还需定义一个`RandomAccessFile` 类型的`file`和一个`FileChannel`类型的`fc`，用于操作`XID`文件。还有一个`xidCounter`用于记录事务的数量，以及一个Lock类用于保证线程安全。然后会在构造函数中给file和fc赋值，然后调用`checkXIDCounter`方法检查`XID`文件是否合法。  
`**begin**`方法用于开始一个新的事务，`**commit**`方法用于提交事务，`**abort**`方法用于回滚事务。这三个方法内部都会调用`**updateXID**`方法，将事务ID和事务状态写入到XID文件中。`**begin**`还会调用另外一个`**incrXIDCounter**`方法，用于将XID +1并更新XID Header。
`isActive、isCommitted`和`isAborted`方法用于检查事务是否处于活动、已提交或已回滚状态。这三个方法内部都会调用checkXID方法，检查XID文件中的事务状态是否与给定的状态相等；close方法用于关闭文件通道和文件。
