package com.ggking.mydb.backend.dm.page;

import com.ggking.mydb.backend.dm.pageCache.PageCache;
import com.ggking.mydb.backend.utils.RandomUtil;

import java.util.Arrays;

public class PageOne {

    private static final int OF_VC = 100;
    private static final int LEN_VC = 8;

    public static byte[] initRaw(){
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setVcOpen(raw);
        return raw;
    }

    public static void setVcOpen(Page pg){
        pg.setDirty(true);
        setVcOpen(pg.getData());
    }

    public static void setVcOpen(byte[] raw){
        System.arraycopy(RandomUtil.randomBytes(LEN_VC),0,raw,OF_VC,LEN_VC);
    }

    public static void setVcClose(Page pg){
        pg.setDirty(true);
        setVcClose(pg.getData());
    }

    public static void setVcClose(byte[] raw){
        System.arraycopy(raw,OF_VC,raw,OF_VC + LEN_VC ,LEN_VC);
    }

    public static boolean checkVc(Page pg){
        return checkVc(pg.getData());
    }

    public static boolean checkVc(byte[] raw){
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC+LEN_VC), Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
    }

}
