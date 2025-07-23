package com.ggking.mydb.client;

import com.ggking.mydb.transport.Package;
import com.ggking.mydb.transport.Packager;

public class RoundTripper {

    private Packager packager;
    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    public Package roundTrip(Package pkg)throws Exception {
        packager.send(pkg);
        return packager.receive();
    }

    public void close() throws Exception{
        packager.close();
    }
}
