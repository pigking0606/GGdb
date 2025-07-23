package com.ggking.mydb.client;

import java.util.Scanner;

public class Shell {
    private Client client;
    public Shell(Client client) {
        this.client = client;
    }

    public void run(){
        Scanner sc = new Scanner(System.in);
        try {
            while (true){
                System.out.println(":>");
                String statStr = sc.nextLine();

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
