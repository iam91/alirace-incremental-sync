package com.zbz.bgk;

import com.zbz.*;

import java.io.IOException;

//import com.zbz.BinlogPool;
//import com.zbz.SendPool;

/**
 * Created by bgk on 6/9/17.
 */
public class ParseTest {
    public static void main(String[] args) throws IOException {
//        BinlogPool binlogPool = BinlogPool.getInstance();
//        SendPool sendPool = SendPool.getInstance();
        long start = 600;
        long end = 700;
//
//        Thread t1 = new Thread(new ReadDataWorker(binlogPool, Constants.DATA_HOME, "", "student"));
//        t1.start();
//
//        Thread t2 = new Thread(new DatabaseWorker(binlogPool, sendPool, start, end));
//        t2.start();
        InnerFileReducer innerFileReducer = new InnerFileReducer("middleware3", "student",
                "/home/zwy/work/test/canal.txt", "/home/zwy/work/middlewareTester/middle/database");
        innerFileReducer.compute();
        Index index = innerFileReducer.getIndex();
        Persistence persistence = innerFileReducer.getPersistence();
//        for (long value : index.getIndexHashMap().keySet()) {
//            System.out.println(value);
//        }
//        System.out.println("---");
        for (long value = start; value < end; value++) {
            long offset = index.getOffset(value);
            if (offset >= 0) {
                String binlogLine = new String(persistence.read(offset));
                Binlog binlog = BinlogFactory.parse(binlogLine);
                System.out.println("result :" + binlog);
            }

        }
    }
}
