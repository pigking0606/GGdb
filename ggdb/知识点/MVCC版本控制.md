## 前提概要
### 什么是MVCC？
> **MVCC**
**MVCC**，全称 **Multi-Version Concurrency Control** ，即多版本并发控制。MVCC 是一种并发控制的方法，一般在数据库管理系统中，实现对数据库的并发访问，在编程语言中实现事务内存。

**MVCC** 在 **MySQL InnoDB** 中的实现主要是为了提高数据库并发性能，用更好的方式去处理读-写冲突，做到即使有读写冲突时，也能做到不加锁，非阻塞并发读。
### 什么是当前读和快照读？

- **当前读**
像 select lock in share mode (共享锁), select for update; update; insert; delete (排他锁)这些操作都是一种当前读，为什么叫当前读？就是它读取的是记录的最新版本，读取时还要保证其他并发事务不能修改当前记录，会对读取的记录进行加锁
- **快照读**
像不加锁的 select 操作就是快照读，即不加锁的非阻塞读；快照读的前提是隔离级别不是串行级别，串行级别下的快照读会退化成当前读；之所以出现快照读的情况，是基于提高并发性能的考虑，快照读的实现是基于多版本并发控制，即 MVCC ,可以认为 MVCC 是行锁的一个变种，但它在很多情况下，避免了加锁操作，降低了开销；既然是基于多版本，即快照读可能读到的并不一定是数据的最新版本，而有可能是之前的历史版本
> 说白了 MVCC 就是为了**实现读-写冲突不加锁**，而这个读指的就是**快照读**, 而非**当前读**，当前读实际上是一种加锁的操作，是悲观锁的实现

### 当前读，快照读和MVCC的关系

- MVCC 多版本并发控制是 **「维持一个数据的多个版本，使得读写操作没有冲突」** 的概念，只是一个抽象概念，并非实现
- 因为 MVCC 只是一个抽象概念，要实现这么一个概念，MySQL 就需要提供具体的功能去实现它，**「快照读就是 MySQL 实现 MVCC 理想模型的其中一个非阻塞读功能」**。而相对而言，当前读就是悲观锁的具体功能实现
- 要说的再细致一些，快照读本身也是一个抽象概念，再深入研究。MVCC 模型在 MySQL 中的具体实现则是由 **4 个隐式字段**，**undo 日志** ，** Read View** 等去完成的
### MVCC 能解决什么问题，好处是？
#### 数据库并发场景

- 读-读：不存在任何问题，也不需要并发控制
- 读-写：有线程安全问题，可能会造成事务隔离性问题，可能遇到脏读，幻读，不可重复读
- 写-写：有线程安全问题，可能会存在更新丢失问题，比如第一类更新丢失，第二类更新丢失
#### MVCC 带来的好处是？
多版本并发控制（MVCC）是一种用来解决读-写冲突的**无锁并发控制**，也就是为事务分配单向增长的时间戳，为每个修改保存一个版本，版本与事务时间戳关联，读操作只读该事务开始前的数据库的快照。 所以 MVCC 可以为数据库解决以下问题

- 在并发读写数据库时，可以做到在读操作时不用阻塞写操作，写操作也不用阻塞读操作，提高了数据库并发读写的性能
- 同时还可以解决脏读，幻读，不可重复读等事务隔离问题，但不能解决更新丢失问题
#### 小结一下
简而言之，MVCC 就是因为大佬们，不满意只让数据库采用悲观锁这样性能不佳的形式去解决读-写冲突问题，而提出的解决方案，**所以在数据库中，因为有了 MVCC，所以我们可以形成两个组合：**

- **MVCC + 悲观锁**
MVCC解决读写冲突，悲观锁解决写写冲突
- **MVCC + 乐观锁**
MVCC 解决读写冲突，乐观锁解决写写冲突

这种组合的方式就可以最大程度的提高数据库并发性能，并解决读写冲突，和写写冲突导致的问题
## MVCC的实现原理
MVCC的目的就是多版本并发控制，在数据库中的实现，就是为了解决读写冲突，它的实现原理主要是依赖记录中的 **4个隐式字段**，**undo日志** ，**Read View** 来实现的。
### 隐式字段
每行记录除了我们自定义的字段外，还有数据库隐式定义的DB_TRX_ID,DB_ROLL_PTR,DB_ROW_ID等字段

- **DB_ROW_ID** 6byte, 隐含的自增ID（隐藏主键），如果数据表没有主键，InnoDB会自动以DB_ROW_ID产生一个聚簇索引
- **DB_TRX_ID** 6byte, 最近修改(修改/插入)事务ID：记录创建这条记录/最后一次修改该记录的事务ID
- **DB_ROLL_PTR** 7byte, 回滚指针，指向这条记录的上一个版本（存储于rollback segment里）
- **DELETED_BIT** 1byte, 记录被更新或删除并不代表真的删除，而是删除flag变了

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713059027939-e3dc553d-72ea-461f-bfdf-3f2b544510de.png#averageHue=%23f8f8f8&clientId=uff32e7c5-e15e-4&from=paste&id=uedb34a9c&originHeight=206&originWidth=927&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=ued191dee-8c10-4855-9196-473b7c06664&title=)如上图，DB_ROW_ID是数据库默认为该行记录生成的唯一隐式主键；DB_TRX_ID是当前操作该记录的事务ID； 而DB_ROLL_PTR是一个回滚指针，用于配合undo日志，指向上一个旧版本；delete flag没有展示出来。
### undo日志
InnoDB把这些为了回滚而记录的这些东西称之为undo log。这里需要注意的一点是，由于查询操作（SELECT）并不会修改任何用户记录，所以在查询操作执行时，并不需要记录相应的undo log。undo log主要分为3种：

- **Insert undo log** ：插入一条记录时，至少要把这条记录的主键值记下来，之后回滚的时候只需要把这个主键值对应的记录删掉就好了。
- **Update undo log**：修改一条记录时，至少要把修改这条记录前的旧值都记录下来，这样之后回滚时再把这条记录更新为旧值就好了。
- **Delete undo log**：删除一条记录时，至少要把这条记录中的内容都记下来，这样之后回滚时再把由这些内容组成的记录插入到表中就好了。 
:::tips
删除操作都只是设置一下老记录的DELETED_BIT，并不真正将过时的记录删除。
为了节省磁盘空间，InnoDB有专门的purge线程来清理DELETED_BIT为true的记录。为了不影响MVCC的正常工作，purge线程自己也维护了一个read view（这个read view相当于系统中最老活跃事务的read view）;如果某个记录的DELETED_BIT为true，并且DB_TRX_ID相对于purge线程的read view可见，那么这条记录一定是可以被安全清除的。
:::

1. **比如一个有个事务插入persion表插入了一条新记录，记录如下，name为Jerry, age为24岁，隐式主键是1，事务ID和回滚指针，我们假设为NULL**

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713059270996-f216677f-a405-4d21-a5f9-f8e7c92f7eae.png#averageHue=%23f5f5f5&clientId=uff32e7c5-e15e-4&from=paste&id=ub74ba809&originHeight=168&originWidth=833&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=uefba0102-ef37-4878-b106-2466e0abd35&title=)

2. **现在来了一个事务1对该记录的name做出了修改，改为Tom**
   1. 在事务1修改该行(记录)数据时，数据库会先对该行加排他锁
   2. 然后把该行数据拷贝到undo log中，作为旧记录，即在undo log中有当前行的拷贝副本
   3. 拷贝完毕后，修改该行name为Tom，并且修改隐藏字段的事务ID为当前事务1的ID, 我们默认从1开始，之后递增，回滚指针指向拷贝到undo log的副本记录，即表示我的上一个版本就是它
   4. 事务提交后，释放锁

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713059286360-141bc5e2-1333-485a-961c-3cb573d560d4.png#averageHue=%23f0dcd2&clientId=uff32e7c5-e15e-4&from=paste&id=u941e80ed&originHeight=361&originWidth=843&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=uc89f59f2-690d-465c-aebf-cb6e0e3485d&title=)

3. **又来了个事务2修改person表的同一个记录，将age修改为30岁**
   1. 在事务2修改该行数据时，数据库也先为该行加锁
   2. 然后把该行数据拷贝到undo log中，作为旧记录，发现该行记录已经有undo log了，那么最新的旧数据作为链表的表头，插在该行记录的undo log最前面
   3. 修改该行age为30岁，并且修改隐藏字段的事务ID为当前事务2的ID, 那就是2，回滚指针指向刚刚拷贝到undo log的副本记录
   4. 事务提交，释放锁

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713059301323-bcec37c1-a44f-470e-8eb5-8e74b92b919d.png#averageHue=%23f1decf&clientId=uff32e7c5-e15e-4&from=paste&id=u3470f7de&originHeight=513&originWidth=838&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=uf91ee119-40ac-4d97-adc7-8569f97ac4b&title=)
从上面，我们就可以看出，不同事务或者相同事务的对同一记录的修改，会导致该记录的undo log成为一条记录版本线性表，即链表，undo log的链首就是最新的旧记录，链尾就是最早的旧记录（当然就像之前说的该undo log的节点可能是会purge线程清除掉，向图中的第一条insert undo log，其实在事务提交之后可能就被删除丢失了，不过这里为了演示，所以还放在这里）
### Read View 是如何在MVCC中工作的
> 说白了Read View就是事务进行快照读操作的时候生产的读视图(Read View)，在该事务执行的快照读的那一刻，会生成数据库系统当前的一个快照，记录并维护系统当前活跃事务的ID(当每个事务开启时，都会被分配一个ID, 这个ID是递增的，所以最新的事务，ID值越大)

#### Read View 四个重要的字段

- m_ids ：指的是在创建 Read View 时，当前数据库中「活跃事务」的**事务 id 列表**，注意是一个列表，**“活跃事务”指的就是，启动了但还没提交的事务**。
- min_trx_id ：指的是在创建 Read View 时，当前数据库中「活跃事务」中事务 **id 最小的事务**，也就是 m_ids 的最小值。
- max_trx_id ：这个并不是 m_ids 的最大值，而是**创建 Read View 时当前数据库中应该给下一个事务的 id 值**，也就是全局事务中最大的事务 id 值 + 1；
- creator_trx_id ：指的是**创建该 Read View 的事务的事务 id**。

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713059637334-e8b53d00-b5b3-4588-a4a3-f9545cc28cd8.png#averageHue=%23ccd1b8&clientId=uff32e7c5-e15e-4&from=paste&id=KgId0&originHeight=437&originWidth=900&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=u40a3efa9-95eb-4975-b634-80e6e43ef1c&title=)
知道了 Read View 的字段，我们还需要了解聚簇索引记录中的两个隐藏列。
假设在账户余额表插入一条小林余额为 100 万的记录，然后我把这两个隐藏列也画出来，该记录的整个示意图如下：
![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713060043656-e745a0a7-52b0-40db-9124-2c720c6f67c3.png#averageHue=%23f8f7f6&clientId=uff32e7c5-e15e-4&from=paste&id=u469ac811&originHeight=302&originWidth=812&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=u41a2576b-3ec6-449c-b897-79ea374da96&title=)
对于使用 InnoDB 存储引擎的数据库表，它的聚簇索引记录中都包含下面两个隐藏列：

- trx_id，当一个事务对某条聚簇索引记录进行改动时，就会**把该事务的事务 id 记录在 trx_id 隐藏列里**；
- roll_pointer，每次对某条聚簇索引记录进行改动时，都会把旧版本的记录写入到 undo 日志中，然后**这个隐藏列是个指针，指向每一个旧版本记录**，于是就可以通过它找到修改前的记录。

在创建 Read View 后，我们可以将记录中的 trx_id 划分这三种情况：
![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713060043439-b0246ebb-9f03-4a3b-a725-5b3c1983366f.png#averageHue=%23f7e5ce&clientId=uff32e7c5-e15e-4&from=paste&id=ue073c1f5&originHeight=332&originWidth=707&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=ucc2a8333-e5f6-40d1-8613-3b934c60159&title=)
一个事务去访问记录的时候，除了自己的更新记录总是可见之外，还有这几种情况：

- 如果记录的 trx_id 值小于 Read View 中的 min_trx_id 值，表示这个版本的记录是在创建 Read View **前**已经提交的事务生成的，所以该版本的记录对当前事务**可见**。
- 如果记录的 trx_id 值大于等于 Read View 中的 max_trx_id 值，表示这个版本的记录是在创建 Read View **后**才启动的事务生成的，所以该版本的记录对当前事务**不可见**。
- 如果记录的 trx_id 值在 Read View 的 min_trx_id 和 max_trx_id 之间，需要判断 trx_id 是否在 m_ids 列表中：
   - 如果记录的 trx_id **在** m_ids 列表中，表示生成该版本记录的活跃事务依然活跃着（还没提交事务），所以该版本的记录对当前事务**不可见**。
   - 如果记录的 trx_id **不在** m_ids列表中，表示生成该版本记录的活跃事务已经被提交，所以该版本的记录对当前事务**可见**。

**这种通过「版本链」来控制并发事务访问同一个记录时的行为就叫 MVCC（多版本并发控制）。**
## 整体流程
我们在了解了**隐式字段**，**undo log**， 以及**Read View**的概念之后，就可以来看看MVCC实现的整体流程是怎么样了
当`事务2`对某行数据执行了`快照读`，数据库为该行数据生成一个`Read View`读视图，假设当前事务ID为`2`，此时还有`事务1和事务3`在活跃中，`事务4在事务2`快照读前一刻提交更新了，所以Read View记录了系统当前活跃事务1，3的ID，维护在一个列表上，假设我们称为`trx_list`

| 事务1 | 事务2 | 事务3 | 事务4 |
| --- | --- | --- | --- |
| 事务开始 | 事务开始 | 事务开始 | 事务开始 |
| … | … | … | 修改且已提交 |
| 进行中 | 快照读 | 进行中 |  |
| … | … | … |  |

- Read View 不仅仅会通过一个列表 trx_list 来维护事务 2执行快照读那刻系统正活跃的事务 ID 列表，还会有两个属性 up_limit_id（ **trx_list 列表中事务 ID 最小的 ID** ），low_limit_id ( **快照读时刻系统尚未分配的下一个事务 ID ，也就是目前已出现过的事务ID的最大值 + 1**) 。所以在这里例子中 up_limit_id 就是1，low_limit_id 就是 4 + 1 = 5，trx_list 集合的值是 1, 3，Read View 如下图

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713060362056-28cba7a4-30e6-47c5-8916-2ada76e42032.png#averageHue=%23f3e7d0&clientId=uff32e7c5-e15e-4&from=paste&id=udbbf73f7&originHeight=151&originWidth=696&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=uafe62c8a-76dd-412f-9b48-d1cb92a3f30&title=)

- 我们的例子中，只有事务 4 修改过该行记录，并在事务 2 执行快照读前，就提交了事务，所以当前该行当前数据的 undo log 如下图所示；我们的事务 2 在快照读该行记录的时候，就会拿该行记录的 DB_TRX_ID 去跟 up_limit_id , low_limit_id 和活跃事务 ID 列表( trx_list )进行比较，判断当前事务 2能看到该记录的版本是哪个。

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713060362074-c113035b-bf9c-406b-8a22-5134125897a2.png#averageHue=%23edd6cb&clientId=uff32e7c5-e15e-4&from=paste&id=u45ec67e4&originHeight=338&originWidth=761&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=u2884051c-e3df-4ab0-8115-f08cb740c57&title=)

- 所以先拿该记录 DB_TRX_ID 字段记录的事务 ID 4 去跟 Read View 的 up_limit_id 比较，看 4 是否小于 up_limit_id( 1 )，所以不符合条件，继续判断 4 是否大于等于 low_limit_id( 5 )，也不符合条件，最后判断 4 是否处于 trx_list 中的活跃事务, 最后发现事务 ID 为 4 的事务不在当前活跃事务列表中, 符合可见性条件，所以事务 4修改后提交的最新结果对事务 2 快照读时是可见的，所以事务 2 能读到的最新数据记录是事务4所提交的版本，而事务4提交的版本也是全局角度上最新的版本

![](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713060362300-caab286f-15af-4f57-8de4-d5d17dc7bb14.png#averageHue=%23f8f5f5&clientId=uff32e7c5-e15e-4&from=paste&id=u1476326d&originHeight=1141&originWidth=1647&originalType=url&ratio=1.25&rotation=0&showTitle=false&status=done&style=none&taskId=u89dbf10a-1188-42d6-a9a7-6ca2a8f8d6b&title=)

- 也正是 Read View 生成时机的不同，从而造成 RC , RR 级别下快照读的结果的不同
## MVCC 相关问题
### RR 是如何在 RC 级的基础上解决不可重复读的？
#### 当前读和快照读在 RR 级别下的区别：
表1:

| **事务A** | **事务B** |
| --- | --- |
| 开启事务 | 开启事务 |
| 快照读(无影响)查询金额为500 | 快照读查询金额为500 |
| 更新金额为400 |  |
| 提交事务 |  |
|  | select 快照读金额为500 |
|  | select lock in share mode当前读金额为400 |

在上表的顺序下，事务 B 的在事务 A 提交修改后的快照读是旧版本数据，而当前读是实时新数据 400
表2:

| **事务A** | **事务B** |
| --- | --- |
| 开启事务 | 开启事务 |
| 快照读（无影响）查询金额为500 |  |
| 更新金额为400 |  |
| 提交事务 |  |
|  | select 快照读金额为400 |
|  | select lock in share mode当前读金额为400 |
| 而在表 2这里的顺序中，事务 B 在事务 A 提交后的快照读和当前读都是实时的新数据 400，这是为什么呢？ |  |

- 这里与上表的唯一区别仅仅是表 1的事务 B 在事务 A 修改金额前快照读过一次金额数据，而表 2的事务B在事务A修改金额前没有进行过快照读。

**所以我们知道事务中快照读的结果是非常依赖该事务首次出现快照读的地方，即某个事务中首次出现快照读的地方非常关键，它有决定该事务后续快照读结果的能力**
**我们这里测试的是更新，同时删除和更新也是一样的，如果事务B的快照读是在事务A操作之后进行的，事务B的快照读也是能读取到最新的数据的**

---

### RC , RR 级别下的 InnoDB 快照读有什么不同？
正是 Read View 生成时机的不同，从而造成 RC , RR 级别下快照读的结果的不同

- 在 RR 级别下的某个事务的对某条记录的第一次快照读会创建一个快照及 Read View, 将当前系统活跃的其他事务记录起来，此后在调用快照读的时候，还是使用的是同一个 Read View，所以只要当前事务在其他事务提交更新之前使用过快照读，那么之后的快照读使用的都是同一个 Read View，所以对之后的修改不可见；
- 即 RR 级别下，快照读生成 Read View 时，Read View 会记录此时所有其他活动事务的快照，这些事务的修改对于当前事务都是不可见的。而早于Read View创建的事务所做的修改均是可见
- 而在 RC 级别下的，事务中，每次快照读都会新生成一个快照和 Read View , 这就是我们在 RC 级别下的事务中可以看到别的事务提交的更新的原因

**总之在 RC 隔离级别下，是每个快照读都会生成并获取最新的 Read View；而在 RR 隔离级别下，则是同一个事务中的第一个快照读才会创建 Read View, 之后的快照读获取的都是同一个 Read View。**
> **转载：**
> - [**SnailMann**](https://blog.csdn.net/SnailMann/article/details/94724197)
> - [**Java知识体系**](https://pdai.tech/md/db/sql-mysql/sql-mysql-mvcc.html#%E5%89%8D%E6%8F%90%E6%A6%82%E8%A6%81)
> - [**小林coding**](https://www.xiaolincoding.com/mysql/transaction/mvcc.html#read-view-%E5%9C%A8-mvcc-%E9%87%8C%E5%A6%82%E4%BD%95%E5%B7%A5%E4%BD%9C%E7%9A%84)

