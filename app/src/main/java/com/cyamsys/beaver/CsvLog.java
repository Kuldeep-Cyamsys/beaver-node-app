package com.cyamsys.beaver;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validates exports before marking them complete. Keeps only 100 rows in RAM. */
public final class CsvLog {
    public static final String HEADER="kind,sequence,stream,utc,node_id,age_ms,time_flags,F1,F2,F3,F4,F5,F6,F7,F8,R1,R2,R3,R4,R5,R6,R7,R8,S1,S2,S3,S4,S5,S6,S7,S8,config_revision";
    public long count; public String first="0",last="0",stream="";
    public final ArrayDeque<String[]> recent=new ArrayDeque<>();
    public static CsvLog copy(InputStream in, OutputStream out, String expectedStream,String from,String to)throws IOException {
        CsvLog log=new CsvLog();BigInteger prior=BigInteger.ZERO,lo=Protocol.u64(from),hi=Protocol.u64(to);
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
        String header=r.readLine();
        if(!HEADER.equals(header)&&!(HEADER+",BatteryPercentage,PcbTemperature").equals(header))throw new IOException("Unexpected CSV header; download was not saved");
        int columns=header.split(",",-1).length;w.write(header);w.write("\r\n");String line;
        try {
            while((line=r.readLine())!=null){
                if(Thread.currentThread().isInterrupted())throw new IOException("Download cancelled");
                if(line.startsWith("ERROR,"))throw new IOException("Incomplete export: "+line.substring(6));
                String[] a=line.split(",",-1);
                if(a.length!=columns||!a[0].equals("DATA"))throw new IOException("Malformed CSV row");
                BigInteger seq=Protocol.u64(a[1]);Protocol.u64(a[2]);
                if(!a[2].equals(expectedStream)||seq.compareTo(prior)<=0||seq.compareTo(lo)<0||seq.compareTo(hi)>0)throw new IOException("Unexpected stream or sequence order");
                for(int i=3;i<32;i++)Long.parseLong(a[i]);
                if(columns==34){
                    if(!a[32].isEmpty()){int b=Integer.parseInt(a[32]);if(b<0||b>100)throw new IOException("Invalid battery value");}
                    if(!a[33].isEmpty()){double t=Double.parseDouble(a[33]);if(!Double.isFinite(t)||t < -327.67||t>327.67)throw new IOException("Invalid PCB temperature");}
                }
                prior=seq;log.stream=a[2];if(log.count++==0)log.first=a[1];log.last=a[1];
                if(log.recent.size()==100)log.recent.removeFirst();log.recent.addLast(a);
                w.write(line);w.write("\r\n");
            }
            if(!hi.equals(prior))throw new IOException("Export ended before requested last sequence "+to);
            w.flush();return log;
        }catch(IllegalArgumentException ex){throw new IOException("Invalid numeric value in export",ex);}
    }
}
