package com.ggking.mydb.backend;

import com.ggking.mydb.backend.dm.DataManager;
import com.ggking.mydb.backend.server.Server;
import com.ggking.mydb.backend.tbm.TableManager;
import com.ggking.mydb.backend.tm.TransactionManager;
import com.ggking.mydb.backend.tm.TransactionManagerImpl;
import com.ggking.mydb.common.Error;
import com.ggking.mydb.backend.utils.Panic;
import com.ggking.mydb.backend.vm.VersionManager;
import com.ggking.mydb.backend.vm.VersionManagerImpl;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;


public class Launcher {

    public static final int port = 9999;
    public static final long DEFAULT_MEM = (1 << 20) * 64;
    public static final long KB = 1 << 10;
    public static final long MB = 1 << 20;
    public static final long GB = 1 << 30;

    public static void main(String[] args) throws Exception{
        Options options = new Options();
        options.addOption("open", true, "-open DBPath");
        options.addOption("create", true, "-create DBPath");
        options.addOption("mem", true, "-mem 64MB");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("open")){
            openDB(cmd.getOptionValue("open"),
            parseMem(cmd.getOptionValue("mem")));
            return;
        }
        if (cmd.hasOption("create")) {
            createDB(cmd.getOptionValue("create"));
            return;
        }
        System.out.println("Usage: launcher (open|create) DBPath");
    }

    private static long parseMem(String memStr) {
        if (memStr == null || "".equals(memStr)) {
            return DEFAULT_MEM;
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
        return DEFAULT_MEM;
    }


    private static void createDB(String path){
        TransactionManagerImpl tm = TransactionManager.create(path);
        DataManager dm = DataManager.create(path, DEFAULT_MEM, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager.create(path, vm, dm);
        tm.close();
        dm.close();
    }
    
    private static void openDB(String path,long mem){
        TransactionManagerImpl tm = TransactionManager.open(path);
        DataManager dm = DataManager.open(path, mem, tm);
        VersionManagerImpl vm = new VersionManagerImpl(tm, dm);
        TableManager tbm = TableManager.open(path, vm, dm);
        new Server(port,tbm).start();
    }
}
