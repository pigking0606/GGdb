package com.ggking.mydb.backend.server;

import com.ggking.mydb.backend.tbm.TableManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server {
    private int port;
    TableManager tbm;

    public Server(int port,TableManager tbm) {
        this.port = port;
        this.tbm = tbm;
    }

    public void start(){
        ServerSocket ss = null;

        try{
            ss = new ServerSocket(port);
        }catch (IOException e){
            e.printStackTrace();
            return;
        }

        System.out.println("Server listen to port: " + port);

        ThreadPoolExecutor tpe = new ThreadPoolExecutor(10, 20, 1L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
        try{
            while (true){
                Socket socket = ss.accept();
                Runnable worker = new HandleSocket(socket,tbm);
                tpe.execute(worker);
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try{
                ss.close();
            }catch (IOException ignored){
            }
        }
    }
}
