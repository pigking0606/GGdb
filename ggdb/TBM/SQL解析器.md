## 前言
`**Parser**` 实现了对类 `**SQL**` 语句的结构化解析，将语句中包含的信息封装为对应语句的类，这些类可见 `**top.guoziyang.mydb.backend.parser.statement**` 包。
```sql
<begin statement>
    begin [isolation level (read committedrepeatable read)]
        begin isolation level read committed

<commit statement>
    commit

<abort statement>
    abort

<create statement>
    create table <table name>
    <field name> <field type>
    <field name> <field type>
    ...
    <field name> <field type>
    [(index <field name list>)]
        create table students
        id int32,
        name string,
        age int32,
        (index id name)

<drop statement>
    drop table <table name>
        drop table students

<select statement>
    select (*<field name list>) from <table name> [<where statement>]
        select * from student where id = 1
        select name from student where id > 1 and id < 4
        select name, age, id from student where id = 12

<insert statement>
    insert into <table name> values <value list>
        insert into student values 5 "Zhang Yuanjia" 22

<delete statement>
    delete from <table name> <where statement>
        delete from student where name = "Zhang Yuanjia"

<update statement>
    update <table name> set <field name>=<value> [<where statement>]
        update student set name = "ZYJ" where id = 5

<where statement>
    where <field name> (><=) <value> [(andor) <field name> (><=) <value>]
        where age > 10 or age < 3

<field name> <table name>
    [a-zA-Z][a-zA-Z0-9_]*

<field type>
    int32 int64 string

<value>
    .*
```
## Tokenizer类：

- Tokenizer类用于对语句进行逐字节解析，根据空白符或者特定的词法规则，将语句切割成多个token。
- 提供了peek()和pop()方法，方便取出Token进行解析。
- 具体的切割实现在内部，不在此段内容中赘述。
### `peek()`
![TBM.Tokenizer.peek( ).png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713747279827-2d050962-5b4e-4c86-97af-8ccb18c157cf.png#averageHue=%23f7f7f7&clientId=udaa88b1e-2473-4&from=paste&height=3021&id=u0d21e705&originHeight=3776&originWidth=7072&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=1637987&status=done&style=none&taskId=u27642483-da32-4663-8461-169af78c8fd&title=&width=5657.6)
```java
/**
 * 获取当前的标记，如果需要的话，会生成新的标记。
 */
public String peek() throws Exception {
    if (err != null) {
        throw err;
    }
    if (flushToken) {
        String token = null;
        try {
            token = next();
        } catch (Exception e) {
            err = e;
            throw e;
        }
        currentToken = token;
        flushToken = false;
    }
    return currentToken;
}

/**
 * 获取下一个标记。如果存在错误，将抛出异常。
 */
private String next() throws Exception {
    if (err != null) {
        throw err;  // 如果存在错误，抛出异常
    }
    return nextMetaState();  // 否则，获取下一个元状态
}

/**
 * 获取下一个元状态。元状态可以是一个符号、引号包围的字符串或者一个由字母、数字或下划线组成的标记。
 */
private String nextMetaState() throws Exception {
    while (true) {
        Byte b = peekByte();  // 获取下一个字节
        if (b == null) {
            return "";  // 如果没有下一个字节，返回空字符串
        }
        if (!isBlank(b)) {
            break;  // 如果下一个字节不是空白字符，跳出循环
        }
        popByte();  // 否则，跳过这个字节
    }
    byte b = peekByte();  // 获取下一个字节
    if (isSymbol(b)) {
        popByte();  // 如果这个字节是一个符号，跳过这个字节
        return new String(new byte[]{b});  // 并返回这个符号
    } else if (b == '"' || b == '\'') {
        return nextQuoteState();  // 如果这个字节是引号，获取下一个引号状态
    } else if (isAlphaBeta(b) || isDigit(b)) {
        return nextTokenState();  // 如果这个字节是字母、数字或下划线，获取下一个标记状态
    } else {
        err = Error.InvalidCommandException;  // 否则，设置错误状态为无效的命令异常
        throw err;  // 并抛出异常
    }
}

/**
 * 获取下一个标记。标记是由字母、数字或下划线组成的字符串。
 */
private String nextTokenState() throws Exception {
    StringBuilder sb = new StringBuilder();  // 创建一个StringBuilder，用于存储标记
    while (true) {
        Byte b = peekByte();  // 获取下一个字节
        // 如果没有下一个字节，或者下一个字节不是字母、数字或下划线，那么结束循环
        if (b == null || !(isAlphaBeta(b) || isDigit(b) || b == '_')) {
            // 如果下一个字节是空白字符，那么跳过这个字节
            if (b != null && isBlank(b)) {
                popByte();
            }
            // 返回标记
            return sb.toString();
        }
        // 如果下一个字节是字母、数字或下划线，那么将这个字节添加到StringBuilder中
        sb.append(new String(new byte[]{b}));
        popByte();  // 跳过这个字节
    }
}

/**
 * 处理引号状态，即处理被引号包围的字符串。
 */
private String nextQuoteState() throws Exception {
    byte quote = peekByte();  // 获取下一个字节，这应该是一个引号
    popByte();  // 跳过这个引号
    StringBuilder sb = new StringBuilder();  // 创建一个StringBuilder，用于存储被引号包围的字符串
    while (true) {
        Byte b = peekByte();  // 获取下一个字节
        if (b == null) {
            err = Error.InvalidCommandException;  // 如果没有下一个字节，设置错误状态为无效的命令异常
            throw err;  // 并抛出异常
        }
        if (b == quote) {
            popByte();  // 如果这个字节是引号，跳过这个字节，并跳出循环
            break;
        }
        sb.append(new String(new byte[]{b}));  // 如果这个字节不是引号，将这个字节添加到StringBuilder中
        popByte();  // 并跳过这个字节
    }
    return sb.toString();  // 返回被引号包围的字符串
}
```
### `pop()`
```java
/**
 * 将当前的标记设置为需要刷新，这样下次调用peek()时会生成新的标记。
 */
public void pop() {
    flushToken = true;
}

/**
* 跳过该字节，指向下一个字节
*/
private void popByte() {
    pos++;
    if (pos > stat.length) {
        pos = stat.length;
    }
}
```
## Parser类

- Parser类直接对外提供了Parse(byte[] statement)方法，用于解析语句。
- 解析过程核心是调用Tokenizer类来分割Token，并根据词法规则将Token包装成具体的Statement类，并返回。
- 解析过程相对简单，仅根据第一个Token来区分语句类型，并分别处理。
- 解析过程自己查看几遍源码即可，这里不多赘述
### `parse()`
![TMB.Praser.prase( ).png](https://cdn.nlark.com/yuque/0/2024/png/22796888/1713748712667-0e0521ab-7e63-4e23-a70c-ddbd6dd802a6.png#averageHue=%23f5f5f5&clientId=udaa88b1e-2473-4&from=paste&height=1165&id=ufb80ffad&originHeight=1456&originWidth=2024&originalType=binary&ratio=1.25&rotation=0&showTitle=false&size=214518&status=done&style=none&taskId=u53d654f3-ee54-493b-9812-6f655f16c1b&title=&width=1619.2)
```java
/**
 * 解析输入的字节流，根据不同的标记（token）调用不同的解析方法，生成对应的语句对象。
 */
public static Object Parse(byte[] statement) throws Exception {
    Tokenizer tokenizer = new Tokenizer(statement);  // 创建一个Tokenizer对象，用于获取标记
    String token = tokenizer.peek();  // 获取下一个标记
    tokenizer.pop();  // 跳过这个标记

    Object stat = null;  // 用于存储生成的语句对象
    Exception statErr = null;  // 用于存储错误信息
    try {
        // 根据标记的值，调用对应的解析方法
        switch (token) {
            case "begin":
                stat = parseBegin(tokenizer);
                break;
            case "commit":
                stat = parseCommit(tokenizer);
                break;
            case "abort":
                stat = parseAbort(tokenizer);
                break;
            case "create":
                stat = parseCreate(tokenizer);
                break;
            case "drop":
                stat = parseDrop(tokenizer);
                break;
            case "select":
                stat = parseSelect(tokenizer);
                break;
            case "insert":
                stat = parseInsert(tokenizer);
                break;
            case "delete":
                stat = parseDelete(tokenizer);
                break;
            case "update":
                stat = parseUpdate(tokenizer);
                break;
            case "show":
                stat = parseShow(tokenizer);
                break;
            default:
                throw Error.InvalidCommandException;  // 如果标记的值不符合预期，抛出异常
        }
    } catch (Exception e) {
        statErr = e;  // 如果在解析过程中出现错误，保存错误信息
    }
    try {
        String next = tokenizer.peek();  // 获取下一个标记
        // 如果还有未处理的标记，那么抛出异常
        if (!"".equals(next)) {
            byte[] errStat = tokenizer.errStat();
            statErr = new RuntimeException("Invalid statement: " + new String(errStat));
        }
    } catch (Exception e) {
        e.printStackTrace();
        byte[] errStat = tokenizer.errStat();
        statErr = new RuntimeException("Invalid statement: " + new String(errStat));
    }
    // 如果存在错误，抛出异常
    if (statErr != null) {
        throw statErr;
    }
    // 返回生成的语句对象
    return stat;
}
```
