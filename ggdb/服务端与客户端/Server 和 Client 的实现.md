Server 和 Client，都是使用了Java 的 socket；这一块内容属于 Java 网络编程的，可以通过 [二哥的进阶之路](https://www.javabetter.cn/socket/network-base.html) 学习；
## Server
`**Server**`是一个服务器类，主要作用是监听指定的端口号，接受客户端的连接请求，并为每个连接请求创建一个新的线程来处理；
```java
public class Server {
    private int port;
    TableManager tbm;

    public Server(int port, TableManager tbm) {
        this.port = port;
        this.tbm = tbm;
    }

    public void start() {
        // 创建一个ServerSocket对象，用于监听指定的端口
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Server listen to port: " + port);
        // 创建一个线程池，用于管理处理客户端连接请求的线程
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(10, 20, 1L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            // 无限循环，等待并处理客户端的连接请求
            while (true) {
                // 接收一个客户端的连接请求
                Socket socket = ss.accept();
                // 创建一个新的HandleSocket对象，用于处理这个连接请求
                Runnable worker = new HandleSocket(socket, tbm);
                // 将这个HandleSocket对象提交给线程池，由线程池中的一个线程来执行
                tpe.execute(worker);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 在最后，无论是否发生异常，都要关闭ServerSocket
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }
}
```
### HandleSocket 
HandleSocket 类实现了 `**Runnable**`** **接口，在建立连接后初始化 `**Packager**`，随后就循环接收来自客户端的数据并处理；主要通过 `**Executor**`** **对象来执行 `**SQL**`语句，在接受、执行SQL语句的过程中发生异常的话，将会结束循环，并关闭 `**Executor**`** **和 `**Package**`;
```java
class HandleSocket implements Runnable {
    private Socket socket;
    private TableManager tbm;

    public HandleSocket(Socket socket, TableManager tbm) {
        this.socket = socket;
        this.tbm = tbm;
    }

    @Override
    public void run() {
        // 获取远程客户端的地址信息
        InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
        // 打印客户端的IP地址和端口号
        System.out.println("Establish connection: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        Packager packager = null;
        try {
            // 创建一个Transporter对象，用于处理网络传输
            Transporter t = new Transporter(socket);
            // 创建一个Encoder对象，用于处理数据的编码和解码
            Encoder e = new Encoder();
            // 创建一个Packager对象，用于处理数据的打包和解包
            packager = new Packager(t, e);
        } catch (IOException e) {
            // 如果在创建Transporter或Encoder时发生异常，打印异常信息并关闭socket
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }
        // 创建一个Executor对象，用于执行SQL语句
        Executor exe = new Executor(tbm);
        while (true) {
            Package pkg = null;
            try {
                // 从客户端接收数据包
                pkg = packager.receive();
            } catch (Exception e) {
                // 如果在接收数据包时发生异常，结束循环
                break;
            }
            // 获取数据包中的SQL语句
            byte[] sql = pkg.getData();
            byte[] result = null;
            Exception e = null;
            try {
                // 执行SQL语句，并获取结果
                result = exe.execute(sql);
            } catch (Exception e1) {
                // 如果在执行SQL语句时发生异常，保存异常信息
                e = e1;
                e.printStackTrace();
            }
            // 创建一个新的数据包，包含执行结果和可能的异常信息
            pkg = new Package(result, e);
            try {
                // 将数据包发送回客户端
                packager.send(pkg);
            } catch (Exception e1) {
                // 如果在发送数据包时发生异常，打印异常信息并结束循环
                e1.printStackTrace();
                break;
            }
        }
        // 关闭Executor
        exe.close();
        try {
            // 关闭Packager
            packager.close();
        } catch (Exception e) {
            // 如果在关闭Packager时发生异常，打印异常信息
            e.printStackTrace();
        }
    }

}
```
### Launcher
这个类是服务器的启动入口，这个类解析了命令行参数。很重要的参数就是`-open`或者`-create`。`Launcher`根据这两个参数，来决定是创建数据库文件，还是启动一个已有的数据库；
```java
public class Launcher {
    // 定义服务器监听的端口号
    public static final int port = 9999;
    // 定义默认的内存大小，这里是64MB，用于数据管理器
    public static final long DEFALUT_MEM = (1 << 20) * 64;
    // 定义一些内存单位，用于解析命令行参数中的内存大小
    public static final long KB = 1 << 10; // 1KB
    public static final long MB = 1 << 20; // 1MB
    public static final long GB = 1 << 30; // 1GB

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("open", true, "-open DBPath");
        options.addOption("create", true, "-create DBPath");
        options.addOption("mem", true, "-mem 64MB");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("open")) {
            openDB(cmd.getOptionValue("open"), parseMem(cmd.getOptionValue("mem")));
            return;
        }
        if (cmd.hasOption("create")) {
            createDB(cmd.getOptionValue("create"));
            return;
        }
        System.out.println("Usage: launcher (open|create) DBPath");
    }

    /**
     * 创建新的数据库
     *
     * @param path 数据库路径
     */
    private static void createDB(String path) {
        // 创建事务管理器
        TransactionManager tm = TransactionManager.create(path);
        // 创建数据管理器
        DataManager dm = DataManager.create(path, DEFALUT_MEM, tm);
        // 创建版本管理器
        VersionManager vm = new VersionManagerImpl(tm, dm);
        // 创建表管理器
        TableManager.create(path, vm, dm);
        tm.close();
        dm.close();
    }

    /**
     * 启动已有的数据库
     */
    private static void openDB(String path, long mem) {
        // 打开事务管理器
        TransactionManager tm = TransactionManager.open(path);
        // 打开数据管理器，传入路径、内存大小和事务管理器
        DataManager dm = DataManager.open(path, mem, tm);
        // 创建版本管理器，传入事务管理器和数据管理器
        VersionManager vm = new VersionManagerImpl(tm, dm);
        // 打开表管理器，传入路径、版本管理器和数据管理器
        TableManager tbm = TableManager.open(path, vm, dm);
        // 创建服务器对象，并启动服务器
        new Server(port, tbm).start();
    }

    // 定义一个方法，用于解析命令行参数中的内存大小
    private static long parseMem(String memStr) {
        // 如果内存大小为空或者为空字符串，那么返回默认的内存大小
        if (memStr == null || "".equals(memStr)) {
            return DEFALUT_MEM;
        }
        // 如果内存大小的字符串长度小于2，那么抛出异常
        if (memStr.length() < 2) {
            Panic.panic(Error.InvalidMemException);
        }
        // 获取内存大小的单位，即字符串的后两个字符
        String unit = memStr.substring(memStr.length() - 2);
        // 获取内存大小的数值部分，即字符串的前部分，并转换为数字
        long memNum = Long.parseLong(memStr.substring(0, memStr.length() - 2));
        // 根据内存单位，计算并返回最终的内存大小
        switch (unit) {
            case "KB":
                return memNum * KB;
            case "MB":
                return memNum * MB;
            case "GB":
                return memNum * GB;
            // 如果内存单位不是KB、MB或GB，那么抛出异常
            default:
                Panic.panic(Error.InvalidMemException);
        }
        // 如果没有匹配到任何情况，那么返回默认的内存大小
        return DEFALUT_MEM;
    }
}

```
## Client
解析客户输入的内容；
```java
public class Client {
    // RoundTripper实例，用于处理请求的往返传输
    private RoundTripper rt;

    // 构造函数，接收一个Packager对象作为参数，并创建一个新的RoundTripper实例
    public Client(Packager packager) {
        this.rt = new RoundTripper(packager);
    }

    // execute方法，接收一个字节数组作为参数，将其封装为一个Package对象，并通过RoundTripper发送
    // 如果响应的Package对象中包含错误，那么抛出这个错误
    // 否则，返回响应的Package对象中的数据
    public byte[] execute(byte[] stat) throws Exception {
        Package pkg = new Package(stat, null);
        Package resPkg = rt.roundTrip(pkg);
        if(resPkg.getErr() != null) {
            throw resPkg.getErr();
        }
        return resPkg.getData();
    }

    // close方法，关闭RoundTripper
    public void close() {
        try {
            rt.close();
        } catch (Exception e) {
        }
    }
}
```
### RoundTripper
用于发送请求并接受响应
```java
public class RoundTripper {
    private Packager packager;

    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    // 定义一个方法，用于处理请求的往返传输
    public Package roundTrip(Package pkg) throws Exception {
        // 发送请求包
        packager.send(pkg);
        // 接收响应包，并返回
        return packager.receive();
    }

    public void close() throws Exception {
        packager.close();
    }
}
```
### Shell
用于接受用户的输入，并调用`Client.execute()`
```java
public class Shell {
    private Client client;

    public Shell(Client client) {
        this.client = client;
    }

    // 定义一个运行方法，用于启动客户端的交互式命令行界面
    public void run() {
        // 创建一个Scanner对象，用于读取用户的输入
        Scanner sc = new Scanner(System.in);
        try {
            // 循环接收用户的输入，直到用户输入"exit"或"quit"
            while (true) {
                // 打印提示符
                System.out.print(":> ");
                // 读取用户的输入
                String statStr = sc.nextLine();
                // 如果用户输入"exit"或"quit"，则退出循环
                if ("exit".equals(statStr) || "quit".equals(statStr)) {
                    break;
                }
                // 尝试执行用户的输入命令，并打印执行结果
                try {
                    // 将用户的输入转换为字节数组，并执行
                    byte[] res = client.execute(statStr.getBytes());
                    // 将执行结果转换为字符串，并打印
                    System.out.println(new String(res));
                    // 如果在执行过程中发生异常，打印异常信息
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            // 无论是否发生异常，都要关闭Scanner和Client
        } finally {
            // 关闭Scanner
            sc.close();
            // 关闭Client
            client.close();
        }
    }
}
```
### Launcher
启动客户端并连接服务器；
```java
public class Launcher {
    public static void main(String[] args) throws UnknownHostException, IOException {
        Socket socket = new Socket("127.0.0.1", 9999);
        Encoder e = new Encoder();
        Transporter t = new Transporter(socket);
        Packager packager = new Packager(t, e);

        Client client = new Client(packager);
        Shell shell = new Shell(client);
        shell.run();
    }
}
```
