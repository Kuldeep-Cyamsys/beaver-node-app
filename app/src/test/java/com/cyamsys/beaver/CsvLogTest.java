package com.cyamsys.beaver;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class CsvLogTest {
 static String row(String seq,String stream,String aux){return "DATA,"+seq+","+stream+",1790148066,1,1000,0,"+"1000,".repeat(8)+"3000,".repeat(8)+"0,".repeat(8)+"2"+aux+"\r\n";}
 static CsvLog parse(String s,String stream,String from,String to)throws Exception{return CsvLog.copy(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),new ByteArrayOutputStream(),stream,from,to);}
 @Test public void v2NullAndZero()throws Exception{CsvLog c=parse(CsvLog.HEADER+",BatteryPercentage,PcbTemperature\r\n"+row("1","7",",,")+row("2","7",",0,0.00"),"7","1","2");assertEquals(2,c.count);assertEquals("",c.recent.getFirst()[32]);assertEquals("0",c.recent.getLast()[32]);}
 @Test public void v1Supported()throws Exception{assertEquals(1,parse(CsvLog.HEADER+"\r\n"+row("1","7",""),"7","1","1").count);}
 @Test public void rejectHttp200ErrorRow(){assertThrows(IOException.class,()->parse(CsvLog.HEADER+"\r\n"+row("1","7","")+"ERROR,range_overwritten_during_export\r\n","7","1","2"));}
 @Test public void truncatedExportNotComplete(){assertThrows(IOException.class,()->parse(CsvLog.HEADER+"\r\n"+row("1","7",""),"7","1","2"));}
 @Test public void wrongStreamRejected(){assertThrows(IOException.class,()->parse(CsvLog.HEADER+"\r\n"+row("1","8",""),"7","1","1"));}
 @Test public void duplicateRejected(){assertThrows(IOException.class,()->parse(CsvLog.HEADER+"\r\n"+row("1","7","")+row("1","7",""),"7","1","1"));}
 @Test public void holesAllowedAndBigIdsExact()throws Exception{CsvLog c=parse(CsvLog.HEADER+"\r\n"+row("18446744073709551613","18446744073709551615","")+row("18446744073709551615","18446744073709551615",""),"18446744073709551615","18446744073709551613","18446744073709551615");assertEquals(2,c.count);assertEquals("18446744073709551615",c.last);}
 @Test public void boundedPreview()throws Exception{StringBuilder b=new StringBuilder(CsvLog.HEADER+"\r\n");for(int i=1;i<=150;i++)b.append(row(""+i,"7",""));CsvLog c=parse(b.toString(),"7","1","150");assertEquals(150,c.count);assertEquals(100,c.recent.size());assertEquals("51",c.recent.getFirst()[1]);}
 @Test public void emptyStore()throws Exception{assertEquals(0,parse(CsvLog.HEADER+"\r\n","7","0","0").count);}
 @Test public void badAuxiliaryRejected(){assertThrows(IOException.class,()->parse(CsvLog.HEADER+",BatteryPercentage,PcbTemperature\r\n"+row("1","7",",101,32.45"),"7","1","1"));}
}
