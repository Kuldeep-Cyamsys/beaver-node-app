package com.cyamsys.beaver;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class ProtocolTest {
 static JSONObject defaults()throws Exception{return new JSONObject("{\"interval_s\":900,\"node_id\":1,\"target\":2,\"panid\":1,\"role\":0,\"channel\":0,\"rate\":2,\"power\":0,\"route_ms\":30000,\"link_ack_ms\":15000,\"app_ack_ms\":90000,\"radio_enabled\":false}");}
 @Test public void defaultsAndNegativePower()throws Exception{JSONObject c=defaults();c.put("power",-9);Protocol.validate(c);}
 @Test public void roleAndIdMustChangeTogether()throws Exception{
  JSONObject c=defaults();assertThrows(IllegalArgumentException.class,()->Protocol.merge(c,new JSONObject().put("role",1)));
  JSONObject next=Protocol.merge(c,new JSONObject().put("role",1).put("node_id",32768));assertEquals(32768,next.getInt("node_id"));assertEquals(1,c.getInt("node_id"));
 }
 @Test public void timeoutConstraint()throws Exception{JSONObject c=defaults();c.put("app_ack_ms",49999);assertThrows(IllegalArgumentException.class,()->Protocol.validate(c));c.put("app_ack_ms",50000);Protocol.validate(c);}
 @Test public void rejectsWrongTypesAndRanges()throws Exception{
  JSONObject c=defaults();c.put("interval_s",299);assertThrows(IllegalArgumentException.class,()->Protocol.validate(c));c.put("interval_s",900.5);assertThrows(IllegalArgumentException.class,()->Protocol.validate(c));c.put("interval_s","900");assertThrows(IllegalArgumentException.class,()->Protocol.validate(c));
 }
 @Test public void targetCannotBeSelf()throws Exception{JSONObject c=defaults();c.put("target",1);assertThrows(IllegalArgumentException.class,()->Protocol.validate(c));}
 @Test public void idsNeverUseFloatingPoint(){assertEquals("18446744073709551615",Protocol.u64("18446744073709551615").toString());assertThrows(IllegalArgumentException.class,()->Protocol.u64("18446744073709551616"));assertThrows(IllegalArgumentException.class,()->Protocol.u64("1e3"));}
 @Test public void flagsPreserveFaults(){assertEquals("OK",Protocol.flags(0));assertTrue(Protocol.flags(15).contains("no sample"));assertTrue(Protocol.flags(7).contains("stale"));}
}
