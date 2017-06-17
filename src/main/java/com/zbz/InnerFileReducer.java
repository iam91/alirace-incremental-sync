package com.zbz;

import com.alibaba.middleware.race.sync.Server;
import com.zbz.zwy.TimeTester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by bgk on 6/13/17.
 */
public class InnerFileReducer {
    private BinlogReducer binlogReducer;
    private String srcFilename;
    private Index index;
    private Persistence persistence;

    private long mem;
    private long per;
    private long read;

    public InnerFileReducer(String schema, String table, String srcFilename, String dstFilename) {
        this.binlogReducer = new BinlogReducer(schema, table);
        this.srcFilename = srcFilename;
        this.persistence = new Persistence(dstFilename);
        this.index = new HashIndex();

        mem = 0;
        per = 0;
        read = 0;
    }

    public void compute() {
        TimeTester.getInstance().setT1(System.currentTimeMillis());
        long t1 = System.currentTimeMillis();
        try {
            reduceDataFile(srcFilename);
        } catch (IOException e) {
            e.printStackTrace();
        }

        long t2 = System.currentTimeMillis();
        String p = "Server InnerFileReducer: " + (t2 - t1) + "ms";
//        System.out.println(p);
        Logger logger = LoggerFactory.getLogger(Server.class);
        logger.info(p);
        logger.info(Thread.currentThread().toString() + " Mem: " + mem + ", Per: " + per);
        logger.info(Thread.currentThread().toString() + " Parse: "
                + binlogReducer.getParseBinlog() + ", Read: " + read);
    }

    private void reduceDataFile(String filename) throws IOException{
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;

        while (true) {
            long t11 = System.currentTimeMillis();
            if((line = reader.readLine()) == null) break;
            long t12 = System.currentTimeMillis();

            read += (t12 - t11);

            long t1 = System.currentTimeMillis();
            binlogReducer.reduce(line);
            long t2 = System.currentTimeMillis();
            mem += (t2 - t1);
//            if (binlogReducer.isFull()) {
//                t1 = System.currentTimeMillis();
//                clearBinlogReducer();
//                t2 = System.currentTimeMillis();
//                per += (t2 - t1);
//            }
        }
        long t1 = System.currentTimeMillis();
        clearBinlogReducer();
        long t2 = System.currentTimeMillis();
        per += (t2 - t1);

        reader.close();

        Logger logger = LoggerFactory.getLogger(Server.class);
        logger.info(filename + " reduced...");
    }

    private void clearBinlogReducer() {
        for (Object binlog : binlogReducer.getBinlogHashMap().values()) {
            long indexOffset;
            Long primaryOldValue = ((Binlog)binlog).getPrimaryOldValue();
            Long primaryValue = ((Binlog)binlog).getPrimaryValue();
            if ((indexOffset = index.getOffset(primaryValue)) > 0) {
                // update other value
                String oldBinlogLine = new String(persistence.read(indexOffset));
                Binlog oldBinlog = BinlogFactory.parse(oldBinlogLine);
                Binlog newBinlog = BinlogReducer.updateOldBinlog(oldBinlog, ((Binlog)binlog));
                if (newBinlog != null) {
                    if (primaryValue != newBinlog.getPrimaryValue()) {
                        index.delete(primaryValue);
                    }
                    long offset = persistence.write(newBinlog.toBytes());
                    index.insert(newBinlog.getPrimaryValue(), offset);
                } else {
                    index.delete(primaryValue);
                }
            } else if ((indexOffset = index.getOffset(primaryOldValue)) > 0) {
                // update key value
                String oldBinlogLine = new String(persistence.read(indexOffset));
                Binlog oldBinlog = BinlogFactory.parse(oldBinlogLine);
                Binlog newBinlog = BinlogReducer.updateOldBinlog(oldBinlog, ((Binlog)binlog));
                if (newBinlog != null) {
                    long offset = persistence.write(newBinlog.toBytes());
                    index.delete(primaryOldValue);
                    index.insert(primaryValue, offset);
                } else {
                    index.delete(primaryOldValue);
                }
            } else {
                long offset = persistence.write(((Binlog)binlog).toBytes());
                index.insert(primaryValue, offset);
            }

        }
        binlogReducer.clearBinlogHashMap();
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Index getIndex() {
        return index;
    }
}
