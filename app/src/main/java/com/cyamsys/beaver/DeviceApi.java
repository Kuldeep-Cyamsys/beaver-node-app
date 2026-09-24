package com.cyamsys.beaver;

import android.net.Network;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class DeviceApi {
    public volatile Network network;
    public static final String BASE="http://192.168.4.1/api/v1/";
    public HttpURLConnection open(String path,String method)throws IOException {
        Network n=network;if(n==null)throw new IOException("Connect to Beaver Wi-Fi first");
        HttpURLConnection c=(HttpURLConnection)n.openConnection(new URL(BASE+path));
        c.setRequestMethod(method);c.setConnectTimeout(7000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept",path.startsWith("logs")?"text/csv":"application/json");return c;
    }
    public JSONObject request(String path,String method,JSONObject body)throws Exception {
        HttpURLConnection c=open(path,method);
        try{
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream o=c.getOutputStream()){o.write(bytes);}}
            check(c);try(InputStream i=c.getInputStream()){return new JSONObject(read(i,32768));}
        }finally{c.disconnect();}
    }
    public static void check(HttpURLConnection c)throws IOException {
        int status=c.getResponseCode();if(status!=200){String body="";try(InputStream in=c.getErrorStream()){if(in!=null)body=read(in,2048);}throw new IOException("HTTP "+status+": "+body);}
    }
    private static String read(InputStream i,int limit)throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;
        while((n=i.read(buf))!=-1){if(b.size()+n>limit)throw new IOException("Unexpected response size");b.write(buf,0,n);}return b.toString(StandardCharsets.UTF_8.name());
    }
}
