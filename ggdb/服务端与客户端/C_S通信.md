## 前言
在MYDB 中传输数据使用了一种特殊的二进制格式，用于客户端和通信端之间的通信。在数据的传输和接受之前，会通过`Package`进行数据的加密以及解密：

- `**[Flag] [Data]**`
- 若 flag 为 0，表示发送的是数据，那么 data 即为这份数据本身，err 就为空
- 若 flag 为 1，表示发送的是错误信息，那么 data 为空， err 为错误提示信息
```java
public class Package {
    byte[] data; // 存放数据信息
    Exception err; // 存放错误提示信息

    public Package(byte[] data, Exception err) {
        this.data = data;
        this.err = err;
    }
}
```
## Encoder
用于将数据加密成**十六进制**数据，这样可以避免特殊字符造成的问题，并在信息末尾加上换行符。这样在发送和接受数据时，可以简单使用 `BufferedReader` 和 `BufferedWrite`进行读写数据；
```java
public class Encoder {

    /**
     * 将Package对象编码为字节数组。
     * 如果Package对象中的错误信息不为空，将错误信息编码为字节数组，并在字节数组前添加一个字节1。
     * 如果Package对象中的错误信息为空，将数据编码为字节数组，并在字节数组前添加一个字节0。
     */
    public byte[] encode(Package pkg) {
        if (pkg.getErr() != null) {
            Exception err = pkg.getErr();
            String msg = "Intern server error!";
            if (err.getMessage() != null) {
                msg = err.getMessage();
            }
            return Bytes.concat(new byte[]{1}, msg.getBytes());
        } else {
            return Bytes.concat(new byte[]{0}, pkg.getData());
        }
    }

    /**
     * 将字节数组解码为Package对象。
     * 如果字节数组的长度小于1，抛出InvalidPkgDataException异常。
     * 如果字节数组的第一个字节为0，将字节数组的剩余部分解码为数据，创建一个新的Package对象，其中数据为解码后的数据，错误信息为null。
     * 如果字节数组的第一个字节为1，将字节数组的剩余部分解码为错误信息，创建一个新的Package对象，其中数据为null，错误信息为解码后的错误信息。
     * 如果字节数组的第一个字节既不是0也不是1，抛出InvalidPkgDataException异常。
     */
    public Package decode(byte[] data) throws Exception {
        if (data.length < 1) {
            throw Error.InvalidPkgDataException;
        }
        if (data[0] == 0) {
            return new Package(Arrays.copyOfRange(data, 1, data.length), null);
        } else if (data[0] == 1) {
            return new Package(null, new RuntimeException(new String(Arrays.copyOfRange(data, 1, data.length))));
        } else {
            throw Error.InvalidPkgDataException;
        }
    }

}

```
## Transporter
编码之后的信息会通过 `Transporter`类，写入输出流发送出去；
```java
public class Transporter {
    private Socket socket;
    private BufferedReader reader; // 字节缓冲流
    private BufferedWriter writer;

    public Transporter(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * 发送数据
     */
    public void send(byte[] data) throws Exception {
        String raw = hexEncode(data);
        writer.write(raw);
        writer.flush();
    }

    /**
     * 接受数据
     */
    public byte[] receive() throws Exception {
        String line = reader.readLine();
        if(line == null) {
            close();
        }
        return hexDecode(line);
    }

    public void close() throws IOException {
        writer.close();
        reader.close();
        socket.close();
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String hexEncode(byte[] buf) {
        return Hex.encodeHexString(buf, true)+"\n";
    }

    /**
     * 将十六进制字符串转换回字节数组
     */
    private byte[] hexDecode(String buf) throws DecoderException {
        return Hex.decodeHex(buf);
    }
}
```
## Packager
`Packager` 则是 `Encoder` 和 `Transporter` 的结合体，直接对外提供 `send` 和 `receive` 方法： bmn
```java
public class Packager {
    private Transporter transpoter;
    private Encoder encoder;

    public Packager(Transporter transpoter, Encoder encoder) {
        this.transpoter = transpoter;
        this.encoder = encoder;
    }

    /**
     * 将信息编码之后发送
     */
    public void send(Package pkg) throws Exception {
        byte[] data = encoder.encode(pkg);
        transpoter.send(data);
    }

    /**
     * 将数据接收之后解密
     */
    public Package receive() throws Exception {
        byte[] data = transpoter.receive();
        return encoder.decode(data);
    }

    public void close() throws Exception {
        transpoter.close();
    }
}

```
