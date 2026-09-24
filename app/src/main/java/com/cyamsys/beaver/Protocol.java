package com.cyamsys.beaver;

import org.json.JSONObject;
import java.math.BigInteger;
import java.util.*;

/** Firmware API v1 constraints, independent of Android views. */
public final class Protocol {
    private Protocol() {}
    public static final String[] KEYS={"interval_s","node_id","target","panid","role","channel","rate","power","route_ms","link_ack_ms","app_ack_ms"};
    private static final int[] MIN={300,0,0,0,0,0,0,-9,1000,1000,5000};
    private static final int[] MAX={604800,65534,32767,65534,1,79,2,22,65535,65535,300000};
    public static final BigInteger U64_MAX=new BigInteger("18446744073709551615");
    public static BigInteger u64(String value) {
        if(value==null||!value.matches("[0-9]+")) throw new IllegalArgumentException("Invalid sequence or stream ID");
        BigInteger n=new BigInteger(value);
        if(n.compareTo(U64_MAX)>0)throw new IllegalArgumentException("ID exceeds uint64 range");
        return n;
    }
    public static void validate(JSONObject c) throws Exception {
        for(int i=0;i<KEYS.length;i++) {
            Object value=c.get(KEYS[i]);
            if(!(value instanceof Number)) throw new IllegalArgumentException(KEYS[i]+" must be an integer");
            double v=((Number)value).doubleValue();
            if(!Double.isFinite(v)||v!=Math.rint(v)||v<MIN[i]||v>MAX[i])
                throw new IllegalArgumentException(KEYS[i]+" must be "+MIN[i]+" to "+MAX[i]);
        }
        if(!(c.get("radio_enabled") instanceof Boolean))throw new IllegalArgumentException("radio_enabled must be true or false");
        int node=c.getInt("node_id"),role=c.getInt("role");
        if((role==0&&node>32767)||(role==1&&node<32768))throw new IllegalArgumentException("Routing IDs: 0–32767. Terminal IDs: 32768–65534. Change role and ID together.");
        if(node==c.getInt("target"))throw new IllegalArgumentException("Node ID and gateway target must differ");
        if(c.getInt("app_ack_ms")<c.getInt("route_ms")+c.getInt("link_ack_ms")+5000)
            throw new IllegalArgumentException("App ACK timeout must be at least route timeout + link timeout + 5000 ms");
    }
    public static JSONObject merge(JSONObject base,JSONObject patch)throws Exception {
        JSONObject next=new JSONObject(base.toString());
        Iterator<String> keys=patch.keys();while(keys.hasNext()){String k=keys.next();next.put(k,patch.get(k));}
        validate(next);return next;
    }
    public static String flags(int s) {
        if(s==0)return "OK";
        List<String> f=new ArrayList<>();
        if((s&1)!=0)f.add("frequency fault");if((s&2)!=0)f.add("NTC fault");
        if((s&4)!=0)f.add("stale");if((s&8)!=0)f.add("no sample");
        if((s&~15)!=0)f.add("unknown flag");return String.join(" · ",f);
    }
}
