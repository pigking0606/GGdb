package com.ggking.mydb.transport;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.net.Socket;

public class Transporter {

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Transporter(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    public void send(byte[] data) throws Exception{
        String raw = hexEncode(data);
        writer.write(raw);
        writer.flush();
    }

    public byte[] receive() throws Exception{
        String line = reader.readLine();
        if (line == null){
            close();
        }
        return hexDecode(line);
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

    public void close() throws Exception{
        writer.close();
        reader.close();
        socket.close();
    }
}
