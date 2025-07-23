package com.ggking.mydb.backend.server;

import com.ggking.mydb.backend.tbm.TableManager;
import com.ggking.mydb.transport.Encoder;
import com.ggking.mydb.transport.Package;
import com.ggking.mydb.transport.Packager;
import com.ggking.mydb.transport.Transporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class HandleSocket implements Runnable{

    private Socket socket;
    private TableManager tbm;

    public HandleSocket(Socket socket,TableManager tbm){
        this.socket = socket;
        this.tbm = tbm;
    }
    @Override
    public void run() {
        InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
        System.out.println("Establish connection: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        Packager packager = null;
        try{
            Transporter t = new Transporter(socket);
            Encoder e = new Encoder();
            packager = new Packager(t,e);
        }catch (IOException e){
            e.printStackTrace();
            try {
                socket.close();
            }catch (IOException e1){
                e.printStackTrace();
            }
            return;
        }
        Executor exe = new Executor(tbm);
        while (true){
            Package pkg = null;
            try{
                pkg = packager.receive();
            }catch (Exception e){
                break;
            }

            byte[] sql = pkg.getData();
            byte[] result = null;
            Exception e = null;
            try{
                result = exe.execute(sql);
            }catch (Exception e1){
                e = e1;
                e.printStackTrace();
            }

            pkg = new Package(result,e);

            try{
                packager.send(pkg);
            }catch (Exception e1){
                e1.printStackTrace();
                break;
            }
        }
        exe.close();
        try{
            packager.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
