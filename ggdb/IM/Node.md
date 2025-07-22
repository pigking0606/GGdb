### 基本结构
```latex
[LeafFlag][KeyNumber][SiblingUid]
[Son0][Key0][Son1][Key1]...[SonN][KeyN]
```

- **[LeafFlag]**：标记该节点是否为叶子节点
- **[KeyNumber]**：该节点中 key 的个数
- **[SiblingUid]**：是其兄弟节点存储在 DM 中的 UID，用于实现节点的连接
- [**SonN] [KeyN]**：后续穿插的子节点，最后一个 Key 始终为 MAX_VALUE，以方便查找
## Node具体实现
```java
public class Node {
    static final int IS_LEAF_OFFSET = 0; // 表示该节点是否为叶子节点
    static final int NO_KEYS_OFFSET = IS_LEAF_OFFSET + 1; // 表示该节点中key的个数
    static final int SIBLING_OFFSET = NO_KEYS_OFFSET + 2; // 表示节点的兄弟节点的UID属性
    static final int NODE_HEADER_SIZE = SIBLING_OFFSET + 8; // 表示节点头部的大小的常量

    static final int BALANCE_NUMBER = 32; // 节点的平衡因子的常量，一个节点最多可以包含32个key
    static final int NODE_SIZE = NODE_HEADER_SIZE + (2 * 8) * (BALANCE_NUMBER * 2 + 2); // 节点的大小

    
    /**
     * 设置是否为叶子节点，1表示是叶子节点，0表示非叶子节点
     */
    static void setRawIsLeaf(SubArray raw, boolean isLeaf) {
        if (isLeaf) {
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte) 1;
        } else {
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte) 0;
        }
    }

    /**
     * 判断是否为叶子节点
     */
    static boolean getRawIfLeaf(SubArray raw) {
        return raw.raw[raw.start + IS_LEAF_OFFSET] == (byte) 1;
    }

    /**
     * 设置节点个数
     */
    static void setRawNoKeys(SubArray raw, int noKeys) {
        System.arraycopy(Parser.short2Byte((short) noKeys), 0, raw.raw, raw.start + NO_KEYS_OFFSET, 2);
    }

    /**
     * 获取节点个数
     */
    static int getRawNoKeys(SubArray raw) {
        return (int) Parser.parseShort(Arrays.copyOfRange(raw.raw, raw.start + NO_KEYS_OFFSET, raw.start + NO_KEYS_OFFSET + 2));
    }

    /**
     * 设置兄弟节点的uid，占用八个字节
     *
     * @param raw
     * @param sibling
     */
    static void setRawSibling(SubArray raw, long sibling) {
        System.arraycopy(Parser.long2Byte(sibling), 0, raw.raw, raw.start + SIBLING_OFFSET, 8);
    }

    /**
     * 获取兄弟节点的uid
     *
     * @param raw
     * @return
     */
    static long getRawSibling(SubArray raw) {
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, raw.start + SIBLING_OFFSET, raw.start + SIBLING_OFFSET + 8));
    }

     /**
     * 设置第k个子节点的UID。
     * 注：k 是从0开始的
     * @param raw 节点的原始字节数组。
     * @param uid 要设置的UID。
     * @param kth 子节点的索引。
     *            raw.start是字节数组的起始位置，NODE_HEADER_SIZE是节点头部的大小，
     *            kth * (8 * 2)是第k个子节点或键的偏移量。所以，raw.start + NODE_HEADER_SIZE + kth * (8 * 2)
     *            就是第k个子节点或键在字节数组中的起始位置。
     */
    static void setRawKthSon(SubArray raw, long uid, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        System.arraycopy(Parser.long2Byte(uid), 0, raw.raw, offset, 8);
    }

    /**
     * 获取第k个子节点的UID。
     */
    static long getRawKthSon(SubArray raw, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset + 8));
    }

    /**
     * 设置第k个键的值
     */
    static void setRawKthKey(SubArray raw, long key, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        System.arraycopy(Parser.long2Byte(key), 0, raw.raw, offset, 8);
    }

    /**
     * 获取第k个键的值
     */
    static long getRawKthKey(SubArray raw, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset + 8));
    }

    /**
     * 从一个节点的原始字节数组中复制一部分数据到另一个节点的原始字节数组中
     */
    static void copyRawFromKth(SubArray from, SubArray to, int kth) {
        // 计算要复制的数据在源节点的原始字节数组中的起始位置
        int offset = from.start + NODE_HEADER_SIZE + kth * (8 * 2);
        // 将源节点的原始字节数组中的数据复制到目标节点的原始字节数组中
        // 复制的数据包括从起始位置到源节点的原始字节数组的末尾的所有数据
        System.arraycopy(from.raw, offset, to.raw, to.start + NODE_HEADER_SIZE, from.end - offset);
    }
}
```
### `newRootRaw()`
```latex
[LeafFlag: 0]
[KeyNumber: 2]
[SiblingUid: 0]
[Son0: left][Key0: key][Son1: right][Key1: MAX_VALUE]

注：一个简单的演示

        (key)
       /     \
      /       \
     /         \
  [left]     [right]
```
```java
/**
 * 创建一个新的根节点的原始字节数组。
 * 这个新的根节点包含两个子节点，它们的键分别是`key`和`Long.MAX_VALUE`，UID分别是`left`和`right`。
 */
static byte[] newRootRaw(long left, long right, long key) {
    // 创建一个新的字节数组，大小为节点的大小
    SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);

    // 设置节点为非叶子节点
    setRawIsLeaf(raw, false);
    // 设置节点的键的数量为2
    setRawNoKeys(raw, 2);
    // 设置节点的兄弟节点的UID为0
    setRawSibling(raw, 0);
    // 设置第0个子节点的UID为left
    setRawKthSon(raw, left, 0);
    // 设置第0个键的值为key
    setRawKthKey(raw, key, 0);
    // 设置第1个子节点的UID为right
    setRawKthSon(raw, right, 1);
    // 设置第1个键的值为Long.MAX_VALUE
    setRawKthKey(raw, Long.MAX_VALUE, 1);

    // 返回新创建的根节点的原始字节数组
    return raw.raw;
}

```
### `newNilRootRaw()`
```java
/**
 * 创建一个新的空根节点的原始字节数组，这个新的根节点没有子节点和键。
 */
static byte[] newNilRootRaw() {
    // 创建一个新的字节数组，大小为节点的大小
    SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);

    // 设置节点为叶子节点
    setRawIsLeaf(raw, true);
    // 设置节点的键的数量为0
    setRawNoKeys(raw, 0);
    // 设置节点的兄弟节点的UID为0
    setRawSibling(raw, 0);

    // 返回新创建的空根节点的原始字节数组
    return raw.raw;
}

```
### `searchNext()`
```java
class SearchNextRes {
    long uid;
    long siblingUid;
}

/**
 * 在B+树的节点中搜索下一个节点的方法
 */
public SearchNextRes searchNext(long key) {
    // 获取节点的读锁
    dataItem.rLock();
    try {
        // 创建一个SearchNextRes对象，用于存储搜索结果
        SearchNextRes res = new SearchNextRes();
        // 获取节点个数
        int noKeys = getRawNoKeys(raw);
        for (int i = 0; i < noKeys; i++) {
            // 获取第i个key的值
            long ik = getRawKthKey(raw, i);
            // 如果key小于ik，那么找到了下一个节点
            if (key < ik) {
                // 设置下一个节点的UID
                res.uid = getRawKthSon(raw, i);
                // 设置兄弟节点的UID为0
                res.siblingUid = 0;
                // 返回搜索结果
                return res;
            }
        }
        // 如果没有找到下一个节点，设置uid为0
        res.uid = 0;
        // 设置兄弟节点的UID为当前节点的兄弟节点的UID
        res.siblingUid = getRawSibling(raw);
        // 返回搜索结果
        return res;

    } finally {
        // 释放节点的读锁
        dataItem.rUnLock();
    }
}
```
### `LeafSearchRangeRes()`
```java
class LeafSearchRangeRes {
    List<Long> uids;
    long siblingUid;
}

/**
 * 在B+树的叶子节点中搜索一个键值范围的方法
 */
public LeafSearchRangeRes leafSearchRange(long leftKey, long rightKey) {
    // 获取数据项的读锁
    dataItem.rLock();
    try {
        // 获取节点中的键的数量
        int noKeys = getRawNoKeys(raw);
        int kth = 0;
        // 找到第一个大于或等于左键的键
        while (kth < noKeys) {
            long ik = getRawKthKey(raw, kth);
            if (ik >= leftKey) {
                break;
            }
            kth++;
        }
        // 创建一个列表，用于存储所有在键值范围内的子节点的UID
        List<Long> uids = new ArrayList<>();
        // 遍历所有的键，将所有小于或等于右键的键对应的子节点的UID添加到列表中
        while (kth < noKeys) {
            long ik = getRawKthKey(raw, kth);
            if (ik <= rightKey) {
                uids.add(getRawKthSon(raw, kth));
                kth++;
            } else {
                break;
            }
        }
        // 如果所有的键都被遍历过，获取兄弟节点的UID
        long siblingUid = 0;
        if (kth == noKeys) {
            siblingUid = getRawSibling(raw);
        }
        // 创建一个LeafSearchRangeRes对象，用于存储搜索结果
        LeafSearchRangeRes res = new LeafSearchRangeRes();
        res.uids = uids;
        res.siblingUid = siblingUid;
        // 返回搜索结果
        return res;
    } finally {
        // 释放数据项的读锁
        dataItem.rUnLock();
    }
}
```
### `insertAndSplit()`
```java
class InsertAndSplitRes {
    long siblingUid, newSon, newKey;
}

/**
 * 在B+树的节点中插入一个键值对，并在需要时分裂节点。
 */
public InsertAndSplitRes insertAndSplit(long uid, long key) throws Exception {
    // 创建一个标志位，用于标记插入操作是否成功
    boolean success = false;
    // 创建一个异常对象，用于存储在插入或分裂节点时发生的异常
    Exception err = null;
    // 创建一个InsertAndSplitRes对象，用于存储插入和分裂节点的结果
    InsertAndSplitRes res = new InsertAndSplitRes();

    // 在数据项上设置一个保存点
    dataItem.before();
    try {
        // 尝试在节点中插入键值对，并获取插入结果
        success = insert(uid, key);
        // 如果插入失败，设置兄弟节点的UID，并返回结果
        if (!success) {
            res.siblingUid = getRawSibling(raw);
            return res;
        }
        // 如果需要分裂节点
        if (needSplit()) {
            try {
                // 分裂节点，并获取分裂结果
                SplitRes r = split();
                // 设置新节点的UID和新键，并返回结果
                res.newSon = r.newSon;
                res.newKey = r.newKey;
                return res;
            } catch (Exception e) {
                // 如果在分裂节点时发生错误，保存异常并抛出
                err = e;
                throw e;
            }
        } else {
            // 如果不需要分裂节点，直接返回结果
            return res;
        }
    } finally {
        // 如果没有发生错误并且插入成功，提交数据项的修改
        if (err == null && success) {
            dataItem.after(TransactionManagerImpl.SUPER_XID);
        } else {
            // 如果发生错误或插入失败，回滚数据项的修改
            dataItem.unBefore();
        }
    }
}


```
### `insert()`
```java
/**
 * 在B+树的节点中插入一个键值对的方法
 */
private boolean insert(long uid, long key) {
    // 获取节点中的键的数量
    int noKeys = getRawNoKeys(raw);
    // 初始化插入位置的索引
    int kth = 0;
    // 找到第一个大于或等于要插入的键的键的位置
    while (kth < noKeys) {
        long ik = getRawKthKey(raw, kth);
        if (ik < key) {
            kth++;
        } else {
            break;
        }
    }
    // 如果所有的键都被遍历过，并且存在兄弟节点，插入失败
    if (kth == noKeys && getRawSibling(raw) != 0) return false;

    // 如果节点是叶子节点
    if (getRawIfLeaf(raw)) {
        // 在插入位置后的所有键和子节点向后移动一位
        shiftRawKth(raw, kth);
        // 在插入位置插入新的键和子节点的UID
        setRawKthKey(raw, key, kth);
        setRawKthSon(raw, uid, kth);
        // 更新节点中的键的数量
        setRawNoKeys(raw, noKeys + 1);
    } else {
        // 如果节点是非叶子节点
        // 获取插入位置的键
        long kk = getRawKthKey(raw, kth);
        // 在插入位置插入新的键
        setRawKthKey(raw, key, kth);
        // 在插入位置后的所有键和子节点向后移动一位
        shiftRawKth(raw, kth + 1);
        // 在插入位置的下一个位置插入原来的键和新的子节点的UID
        setRawKthKey(raw, kk, kth + 1);
        setRawKthSon(raw, uid, kth + 1);
        // 更新节点中的键的数量
        setRawNoKeys(raw, noKeys + 1);
    }
    // 插入成功
    return true;
}
```
### `split()`
```java
class SplitRes {
    long newSon, newKey;
}

/**
 * 分裂B+树的节点。
 * 当一个节点的键的数量达到 `BALANCE_NUMBER * 2` 时，就意味着这个节点已经满了，需要进行分裂操作。
 * 分裂操作的目的是将一个满的节点分裂成两个节点，每个节点包含一半的键。
 */
private SplitRes split() throws Exception {
    // 创建一个新的字节数组，用于存储新节点的原始数据
    SubArray nodeRaw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
    // 设置新节点的叶子节点标志，与原节点相同
    setRawIsLeaf(nodeRaw, getRawIfLeaf(raw));
    // 设置新节点的键的数量为BALANCE_NUMBER
    setRawNoKeys(nodeRaw, BALANCE_NUMBER);
    // 设置新节点的兄弟节点的UID，与原节点的兄弟节点的UID相同
    setRawSibling(nodeRaw, getRawSibling(raw));
    // 从原节点的原始字节数组中复制一部分数据到新节点的原始字节数组中
    copyRawFromKth(raw, nodeRaw, BALANCE_NUMBER);
    // 在数据管理器中插入新节点的原始数据，并获取新节点的UID
    long son = tree.dm.insert(TransactionManagerImpl.SUPER_XID, nodeRaw.raw);
    // 更新原节点的键的数量为BALANCE_NUMBER
    setRawNoKeys(raw, BALANCE_NUMBER);
    // 更新原节点的兄弟节点的UID为新节点的UID
    setRawSibling(raw, son);

    // 创建一个SplitRes对象，用于存储分裂结果
    SplitRes res = new SplitRes();
    // 设置新节点的UID
    res.newSon = son;
    // 设置新键为新节点的第一个键的值
    res.newKey = getRawKthKey(nodeRaw, 0);
    // 返回分裂结果
    return res;
}
```
