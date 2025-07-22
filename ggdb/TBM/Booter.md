## 前言
### 启动信息管理

- MYDB的启动信息存储在**bt**文件中，其中所需的信息只有一个，即头表的UID。
- **Booter**类提供了**load**和**update**两个方法，用于加载和更新启动信息。
- **update**方法在修改**bt**文件内容时，采取了一种保证原子性的策略，即先将内容写入一个临时文件**bt_tmp**中，然后通过操作系统的重命名操作将临时文件重命名为**bt**文件。
- 通过这种方式，利用操作系统重命名文件的原子性，来确保对**bt**文件的修改操作是原子的，从而保证了启动信息的一致性和正确性。
### 基本定义
```java

// 记录第一个表的uid
public class Booter {
    // 数据库启动信息文件的后缀
    public static final String BOOTER_SUFFIX = ".bt";
    // 数据库启动信息文件的临时后缀
    public static final String BOOTER_TMP_SUFFIX = ".bt_tmp";
    // 数据库启动信息文件的路径
    String path;
    // 数据库启动信息文件
    File file;
}
```
### `create() and open()`
通过创建或打开启动信息文件，来进行数据库的校验
```java
/**
 * 创建一个新的Booter对象
 */
public static Booter create(String path) {
    // 删除可能存在的临时文件
    removeBadTmp(path);
    // 创建一个新的文件对象，文件名是路径加上启动信息文件的后缀
    File f = new File(path + BOOTER_SUFFIX);
    try {
        // 尝试创建新的文件，如果文件已存在，则抛出异常
        if (!f.createNewFile()) {
            Panic.panic(Error.FileExistsException);
        }
    } catch (Exception e) {
        // 如果创建文件过程中出现异常，则处理异常
        Panic.panic(e);
    }
    // 检查文件是否可读写，如果不可读写，则抛出异常
    if (!f.canRead() || !f.canWrite()) {
        Panic.panic(Error.FileCannotRWException);
    }
    // 返回新创建的Booter对象
    return new Booter(path, f);
}

/**
 * 打开一个已存在的Booter对象
 */
public static Booter open(String path) {
    // 删除可能存在的临时文件
    removeBadTmp(path);
    // 创建一个新的文件对象，文件名是路径加上启动信息文件的后缀
    File f = new File(path + BOOTER_SUFFIX);
    // 如果文件不存在，则抛出异常
    if (!f.exists()) {
        Panic.panic(Error.FileNotExistsException);
    }
    // 检查文件是否可读写，如果不可读写，则抛出异常
    if (!f.canRead() || !f.canWrite()) {
        Panic.panic(Error.FileCannotRWException);
    }
    // 返回打开的Booter对象
    return new Booter(path, f);
}

/**
 * 删除可能存在的临时文件
 */
private static void removeBadTmp(String path) {
    // 删除路径加上临时文件后缀的文件
    new File(path + BOOTER_TMP_SUFFIX).delete();
}
```
### `load()`
加载文件启动信息文件
```java
public byte[] load() {
    byte[] buf = null;
    try {
        // 读取文件的所有字节
        buf = Files.readAllBytes(file.toPath());
    } catch (IOException e) {
        Panic.panic(e);
    }
    return buf;
}
```
### `update()`
```java
/**
 * 更新启动信息文件的内容。
 *
 * @param data 要写入文件的数据
 */
public void update(byte[] data) {
    // 创建一个新的临时文件
    File tmp = new File(path + BOOTER_TMP_SUFFIX);
    try {
        // 尝试创建新的临时文件
        tmp.createNewFile();
    } catch (Exception e) {
        // 如果创建文件过程中出现异常，则处理异常
        Panic.panic(e);
    }
    // 检查临时文件是否可读写，如果不可读写，则抛出异常
    if (!tmp.canRead() || !tmp.canWrite()) {
        Panic.panic(Error.FileCannotRWException);
    }
    try (FileOutputStream out = new FileOutputStream(tmp)) {
        // 将数据写入临时文件
        out.write(data);
        // 刷新输出流，确保数据被写入文件
        out.flush();
    } catch (IOException e) {
        // 如果写入文件过程中出现异常，则处理异常
        Panic.panic(e);
    }
    try {
        // 将临时文件移动到启动信息文件的位置，替换原来的文件
        Files.move(tmp.toPath(), new File(path + BOOTER_SUFFIX).toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
        // 如果移动文件过程中出现异常，则处理异常
        Panic.panic(e);
    }
    // 更新file字段为新的启动信息文件
    file = new File(path + BOOTER_SUFFIX);
    // 检查新的启动信息文件是否可读写，如果不可读写，则抛出异常
    if (!file.canRead() || !file.canWrite()) {
        Panic.panic(Error.FileCannotRWException);
    }
}

```
