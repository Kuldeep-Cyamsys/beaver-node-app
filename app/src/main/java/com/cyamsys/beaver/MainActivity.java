package com.cyamsys.beaver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    final int GREEN=Color.rgb(23,107,89),INK=Color.rgb(28,44,40),MUTED=Color.rgb(91,108,102),BG=Color.rgb(244,246,243),RED=Color.rgb(163,49,39);
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final DeviceApi api=new DeviceApi();
    ConnectivityManager connectivity;ConnectivityManager.NetworkCallback requested,watch;
    LinearLayout root,content,tabs; TextView connection,notice,rtcValue;ProgressBar progress;ScrollView scroll;
    JSONObject status,config;String screen="Live",message="Press INPUT_1 on the node, then connect to Beaver.";
    boolean busy,foreground,alive=true,connected; long statusAt;
    final Map<String,EditText> fields=new LinkedHashMap<>();Switch radioSwitch;
    File selectedLog,pendingSave;CsvLog selectedData;
    int chartChannel=1;
    final Runnable poll=new Runnable(){public void run(){if(foreground){if(!busy&&api.network!=null&&screen.equals("Live"))refresh(false);ui.postDelayed(this,5000);}}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        buildShell();
        watch=new ConnectivityManager.NetworkCallback(){
            @Override public void onLost(Network n){ui.post(()->{if(n.equals(api.network)){api.network=null;connected=false;status=null;statusAt=0;message="Wi-Fi disconnected. Previously saved downloads are still available.";render();}});}
        };
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),watch);
        render();
    }
    @Override protected void onResume(){super.onResume();foreground=true;ui.removeCallbacks(poll);ui.post(poll);}
    @Override protected void onPause(){foreground=false;ui.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){alive=false;ui.removeCallbacksAndMessages(null);worker.shutdownNow();releaseRequest();if(watch!=null)connectivity.unregisterNetworkCallback(watch);super.onDestroy();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(3),0,dp(5));return v;}
    private void buildShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(18),dp(10),dp(18),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());root.setPadding(dp(18)+i.left,dp(8)+i.top,dp(18)+i.right,i.bottom);}
            else root.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title=text("Beaver",30,INK);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("VIBRATING WIRE NODE",11,MUTED));
        connection=text("Not connected",13,GREEN);root.addView(connection);
        LinearLayout buttons=new LinearLayout(this);Button connect=button("Connect",this::connect);Button manual=button("Wi-Fi settings",()->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));Button use=button("Use Wi-Fi",this::useWifi);
        for(Button b:new Button[]{connect,manual,use})buttons.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));root.addView(buttons);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        notice=text(message,13,MUTED);root.addView(notice);
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,dp(8),0,dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        tabs=new LinearLayout(this);for(String name:new String[]{"Live","Device","Radio","Logs","Health"}){Button b=button(name,()->navigate(name));b.setTextSize(11);b.setMinWidth(0);b.setPadding(0,0,0,0);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(tabs);
        setContentView(root);root.requestApplyInsets();
    }
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(GREEN);b.setTextSize(13);b.setOnClickListener(v->action.run());return b;}
    private void action(String label,Runnable r){content.addView(button(label,r),new LinearLayout.LayoutParams(-1,dp(48)));}
    private void heading(String label){TextView t=text(label,23,INK);t.setTypeface(null,Typeface.BOLD);content.addView(t);}
    private void para(String s){content.addView(text(s,14,MUTED));}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(10),dp(14),dp(10));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),Color.rgb(223,230,223));c.setBackground(bg);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-1,-2);l.bottomMargin=dp(10);content.addView(c,l);return c;}
    private void metric(String name,String value){LinearLayout c=card();c.addView(text(name.toUpperCase(Locale.ROOT),11,MUTED));c.addView(text(value,20,INK));}
    private boolean editing(){return !fields.isEmpty();}
    private void navigate(String next){if(screen.equals(next))return;if(busy){setMessage("Please wait for the current operation to finish.");return;}if(editing()){new AlertDialog.Builder(this).setTitle("Leave settings?").setMessage("Unsaved edits will be discarded.").setNegativeButton("Stay",null).setPositiveButton("Leave",(d,w)->{screen=next;scroll.scrollTo(0,0);render();}).show();}else{screen=next;scroll.scrollTo(0,0);render();}}
    private void render(){
        if(!alive)return;int oldY=scroll.getScrollY();content.removeAllViews();fields.clear();radioSwitch=null;rtcValue=null;
        connection.setText(connected&&status!=null?"Node "+status.optString("device_id")+" · Wi-Fi connected":"Not connected to a verified Beaver node");
        connection.setTextColor(connected?GREEN:MUTED);notice.setText(message);progress.setVisibility(busy?View.VISIBLE:View.INVISIBLE);
        for(int i=0;i<tabs.getChildCount();i++){Button b=(Button)tabs.getChildAt(i);b.setTypeface(null,b.getText().toString().equals(screen)?Typeface.BOLD:Typeface.NORMAL);}
        switch(screen){case "Live":live();break;case "Device":device();break;case "Radio":radio();break;case "Logs":logs();break;default:health();}
        scroll.post(()->scroll.scrollTo(0,oldY));
    }
    private void setMessage(String s){message=s;notice.setText(s);}
    interface Work {void run()throws Exception;}
    private void task(String label,Work job,Runnable done){
        if(busy){setMessage("Please wait for the current operation to finish.");return;}
        busy=true;progress.setVisibility(View.VISIBLE);setMessage(label);
        worker.execute(()->{String error=null;try{job.run();}catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();}final String failure=error;
            ui.post(()->{if(!alive)return;busy=false;progress.setVisibility(View.INVISIBLE);if(failure!=null){setMessage(failure);new AlertDialog.Builder(this).setTitle("Operation did not complete").setMessage(failure+"\n\nIf a settings write timed out, read settings again before retrying; the device may have saved it.").setPositiveButton("OK",null).show();}else done.run();});});
    }
    private void releaseRequest(){if(requested!=null){try{connectivity.unregisterNetworkCallback(requested);}catch(Exception ignored){}requested=null;}}
    private void connect(){
        if(busy)return;
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{permission}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}
        releaseRequest();connected=false;api.network=null;status=null;config=null;
        setMessage("Select Beaver in Android's connection dialog. The node has no internet access.");
        WifiNetworkSpecifier spec=new WifiNetworkSpecifier.Builder().setSsid("Beaver").setWpa2Passphrase("12345678").build();
        requested=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){ui.post(()->{api.network=n;refresh(true);});}
            @Override public void onUnavailable(){ui.post(()->{connected=false;setMessage("Connection cancelled or unavailable. Enable the node AP, or connect through Wi-Fi settings and tap Use Wi-Fi.");});}
        };
        try{connectivity.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build(),requested);}
        catch(Exception e){releaseRequest();setMessage("Cannot request Wi-Fi: "+e.getMessage()+". Use Wi-Fi settings instead.");}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)connect();else setMessage("Wi-Fi permission denied. Connect to Beaver in Android settings, then tap Use Wi-Fi.");}}
    private void useWifi(){
        if(busy)return;Network wifi=null;
        for(Network n:connectivity.getAllNetworks()){NetworkCapabilities c=connectivity.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=n;break;}}
        if(wifi==null){setMessage("No Wi-Fi connection. Join Beaver in Android Wi-Fi settings first.");return;}
        api.network=wifi;status=null;config=null;connected=false;refresh(true);
    }
    private void refresh(boolean full){
        if(busy)return;busy=true;progress.setVisibility(View.VISIBLE);
        worker.execute(()->{JSONObject st=null,cf=null;String error=null;
            try{st=api.request("status","GET",null);if(!st.has("device_id")||st.getJSONArray("channels").length()!=8)throw new IOException("Unexpected device response");if(full)cf=api.request("config","GET",null);}
            catch(Exception e){error=e.getMessage();}
            JSONObject finalSt=st,finalCf=cf;String failure=error;
            ui.post(()->{if(!alive)return;busy=false;progress.setVisibility(View.INVISIBLE);if(failure!=null){connected=false;status=null;statusAt=0;message="Device unavailable: "+failure;}
                else{status=finalSt;if(finalCf!=null)config=finalCf;connected=true;statusAt=System.currentTimeMillis();message="Updated "+DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now())+" · Phone local time";}
                if(!editing())render();else{notice.setText(message);connection.setText(connected?"Device connected":"Device unavailable");if(rtcValue!=null)rtcValue.setText(status!=null&&status.optBoolean("time_valid")?timestamp(status.optLong("utc"))+" (last read)":"RTC not yet verified");}});
        });
    }
    private String auxiliary(String key,String unit){return status.isNull(key)||!status.has(key)?"Unavailable":status.optString(key)+unit;}
    private static String timestamp(long utc){try{return Instant.ofEpochSecond(utc).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z"));}catch(Exception e){return "Invalid timestamp";}}
    private void live(){
        heading("Latest readings");para("Live frames update about every 13 seconds. This screen refreshes every 5 seconds while open.");
        action("Refresh now",()->refresh(false));if(status==null){para("Connect to the node to see live measurements.");return;}
        long age=status.optLong("sample_age_ms",-1);metric("Frame age",age<0?"No frame received":String.format(Locale.US,"%.2f seconds%s",age/1000.0,status.optBoolean("stale")?" · STALE":""));
        metric("Battery",auxiliary("BatteryPercentage"," %"));metric("PCB temperature",auxiliary("PcbTemperature"," °C"));
        JSONArray channels=status.optJSONArray("channels");for(int i=1;i<=8;i++){JSONObject ch=channels.optJSONObject(i-1);LinearLayout c=card();c.addView(text("CHANNEL "+i,12,GREEN));c.addView(text(ch.optString("frequency_hz"+i)+" Hz    ·    "+ch.optString("resistance_ohm"+i)+" Ω",20,INK));int flags=ch.optInt("status"+i,15);c.addView(text(Protocol.flags(flags),12,flags==0?MUTED:RED));}
        para("NTC readings are resistance in ohms. Faulty or stale readings are flagged, not converted to temperature.");
    }
    private void field(String key,String label,String help){
        content.addView(text(label,15,INK));if(!help.isEmpty())para(help);EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(config.optString(key));e.setContentDescription(label);fields.put(key,e);content.addView(e,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private boolean needConfig(){if(config==null){para("Read the device configuration before editing.");action("Read configuration",()->refresh(true));return true;}return false;}
    private void device(){
        heading("Device settings");if(needConfig())return;
        para("Loaded revision "+config.optString("revision")+". Only changed fields are sent when you save.");
        field("interval_s","Logging interval · seconds","300–604800. Default 900 = 15 minutes. Saving a changed interval restarts the countdown.");
        field("node_id","This node ID","Routing: 0–32767. Terminal: 32768–65534.");
        field("target","Gateway target ID","0–32767. Must differ from this node's ID.");
        action("Save device settings",this::saveConfig);
        heading("RTC time");rtcValue=text(status!=null&&status.optBoolean("time_valid")?timestamp(status.optLong("utc"))+" (last read)":"RTC not yet verified",14,MUTED);content.addView(rtcValue);
        para("Phone time zone: "+ZoneId.systemDefault()+". The device stores UTC seconds.");
        action("Synchronize with phone",()->confirm("Set device clock?","Use the phone's current clock. Logging cadence is unchanged.",()->setTime(Instant.now().getEpochSecond())));
        action("Set date and time manually",this::manualTime);
    }
    private void radio(){
        heading("Radio settings");if(needConfig())return;
        para("These are firmware-accepted ranges, not an approved regional RF preset. Channel 0 is a placeholder. Confirm deployment frequency and power before enabling transmission.");
        field("node_id","This node ID","Change ID and role together when needed.");field("target","Gateway target ID","Gateway-connected routing node, 0–32767.");
        field("panid","Mesh PANID","0–65534. Must match the gateway and other mesh nodes.");
        field("role","Node role","0 = routing; 1 = terminal.");field("channel","Channel index","0–79. Frequency = 850.125 + channel MHz.");
        field("rate","Air rate","0 = 62.5K; 1 = 21.825K; 2 = 7K.");field("power","Transmit power · dBm","-9 to +22. Use the deployment-approved value.");
        field("route_ms","Route timeout · milliseconds","1000–65535; default 30000.");field("link_ack_ms","Link ACK timeout · milliseconds","1000–65535; default 15000.");
        field("app_ack_ms","Gateway ACK timeout · milliseconds","5000–300000; at least route + link + 5000. Default 90000.");
        radioSwitch=new Switch(this);radioSwitch.setText("Enable radio delivery");radioSwitch.setChecked(config.optBoolean("radio_enabled"));radioSwitch.setPadding(0,dp(15),0,dp(15));content.addView(radioSwitch);
        action("Save radio settings",this::saveConfig);para("Changing target or PANID replays retained history. Settings are applied asynchronously: read Health to check radio_ready and delivery progress.");
    }
    private void saveConfig(){
        if(busy)return;
        try{JSONObject patch=new JSONObject();for(Map.Entry<String,EditText> f:fields.entrySet()){String raw=f.getValue().getText().toString().trim();if(!raw.matches("-?[0-9]+"))throw new IllegalArgumentException(f.getKey()+" must be an integer");long v=Long.parseLong(raw);if(v!=config.getLong(f.getKey()))patch.put(f.getKey(),v);}
            if(radioSwitch!=null&&radioSwitch.isChecked()!=config.getBoolean("radio_enabled"))patch.put("radio_enabled",radioSwitch.isChecked());
            Protocol.merge(config,patch);if(patch.length()==0){setMessage("No changes to save.");return;}
            boolean radio=screen.equals("Radio");String info="Save these changes?\n"+patch.toString(2);
            if(patch.has("target")||patch.has("panid"))info+="\nRetained logs will be replayed to the selected destination/network.";
            if(radio)info+="\nConfirm that the selected channel and transmit power are approved for your deployment.";
            confirm("Apply settings",info,()->{final JSONObject[] saved={null};task("Saving settings…",()->{JSONObject fresh=api.request("config","GET",null);Protocol.merge(fresh,patch);saved[0]=api.request("config","PUT",patch);},()->{config=saved[0];message="Settings saved, revision "+config.optString("revision")+". Radio application is asynchronous.";render();});});
        }catch(Exception e){setMessage(e.getMessage());new AlertDialog.Builder(this).setTitle("Check settings").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void confirm(String title,String body,Runnable r){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->r.run()).show();}
    private void setTime(long epoch){
        if(epoch<1704067200L||epoch>4102444799L){setMessage("Choose a time within 2024–2099 UTC.");return;}
        task("Setting RTC…",()->api.request("time","PUT",new JSONObject().put("utc",epoch)),()->{setMessage("RTC updated. Reading back…");refresh(false);});
    }
    private void manualTime(){LocalDateTime now=LocalDateTime.now();new DatePickerDialog(this,(v,y,m,day)->new TimePickerDialog(this,(t,hr,min)->{
        LocalDateTime local=LocalDateTime.of(y,m+1,day,hr,min);ZoneId zone=ZoneId.systemDefault();
        if(zone.getRules().getValidOffsets(local).size()!=1){setMessage("This local time is ambiguous or skipped by daylight saving. Choose another time or sync the phone.");return;}
        long utc=local.atZone(zone).toEpochSecond();confirm("Set device time?",timestamp(utc)+"\nUTC seconds: "+utc,()->setTime(utc));
    },now.getHour(),now.getMinute(),true).show(),now.getYear(),now.getMonthValue()-1,now.getDayOfMonth()).show();}
    private void logs(){
        heading("Stored logs");para("Download the full retained history, or select an inclusive sequence range. Downloads stay on this phone; use Save or Share to export them.");
        action("Download all retained logs",()->download(null,null));
        action("Download sequence range",this::rangeDialog);
        File[] files=getFilesDir().listFiles((dir,name)->name.startsWith("beaver-")&&name.endsWith(".csv"));
        if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(File f:files){action(f.getName()+" · "+(f.length()/1024)+" KB",()->loadLog(f));}}
        if(selectedLog==null||selectedData==null){para("Completed downloads will appear here. Sequence bounds alone are not an exact log count.");return;}
        heading("Selected download");para(selectedLog.getName());metric("Records in this file",Long.toString(selectedData.count));para("Sequence "+selectedData.first+" to "+selectedData.last+" · stream "+selectedData.stream);
        action("Save CSV to phone",()->{pendingSave=selectedLog;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,selectedLog.getName());startActivityForResult(i,20);});
        action("Share CSV",()->{Uri u=Uri.parse("content://com.cyamsys.beaver.logs/"+selectedLog.getName());Intent i=new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Beaver log",u));startActivity(Intent.createChooser(i,"Share completed CSV"));});
        action("Delete this phone copy",()->confirm("Delete downloaded copy?","This removes only the selected phone file. Device logs are unchanged.",()->{if(selectedLog.delete()){selectedLog=null;selectedData=null;render();}else setMessage("Could not delete file.");}));
        heading("Recent frequency trend");para("Last 100 records in this file. Stale/faulty values are excluded. Horizontal position follows UTC time; gaps are not filled.");
        Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Channel 1","Channel 2","Channel 3","Channel 4","Channel 5","Channel 6","Channel 7","Channel 8"}));sp.setSelection(chartChannel-1);content.addView(sp);
        GraphView graph=new GraphView(this,new ArrayList<>(selectedData.recent),chartChannel);content.addView(graph,new LinearLayout.LayoutParams(-1,dp(180)));
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){chartChannel=pos+1;graph.channel=chartChannel;graph.invalidate();}});
        heading("Recent records");para("Tap a row for all eight channel readings and quality flags.");
        Iterator<String[]> rows=selectedData.recent.descendingIterator();while(rows.hasNext()){String[] a=rows.next();action("#"+a[1]+" · "+("0".equals(a[6])?timestamp(Long.parseLong(a[3])):"Invalid RTC time"),()->showRecord(a));}
    }
    private void rangeDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);EditText from=new EditText(this),to=new EditText(this);from.setHint("First sequence");to.setHint("Last sequence");from.setInputType(2);to.setInputType(2);box.addView(from);box.addView(to);new AlertDialog.Builder(this).setTitle("Inclusive sequence range").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->download(from.getText().toString().trim(),to.getText().toString().trim())).show();}
    private void download(String from,String to){
        final File[] output={null};final CsvLog[] data={null};
        task("Downloading and checking CSV…",()->{
            JSONObject st=api.request("status","GET",null);String first=st.getString("first_sequence"),last=st.getString("last_sequence"),stream=st.getString("stream_id");
            String lo=from==null?first:from,hi=to==null?last:to;
            if(Protocol.u64(lo).compareTo(Protocol.u64(hi))>0||Protocol.u64(lo).compareTo(Protocol.u64(first))<0||Protocol.u64(hi).compareTo(Protocol.u64(last))>0||(from!=null&&Protocol.u64(lo).signum()==0))throw new IOException("Range is outside retained bounds "+first+"–"+last);
            String path=Protocol.u64(last).signum()==0?"logs.csv":"logs.csv?from="+lo+"&to="+hi;
            File partial=File.createTempFile("download-",".partial",getCacheDir());
            HttpURLConnection c=api.open(path,"GET");
            try{DeviceApi.check(c);try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(partial)){data[0]=CsvLog.copy(in,out,stream,lo,hi);}
                File dest=new File(getFilesDir(),"beaver-"+st.getString("device_id")+"-"+System.currentTimeMillis()+".csv");
                if(!partial.renameTo(dest))throw new IOException("Could not finalize download");output[0]=dest;
            }finally{c.disconnect();if(partial.exists())partial.delete();}
        },()->{selectedLog=output[0];selectedData=data[0];message="Complete download: "+selectedData.count+" records. Device delivery cursor is unchanged.";render();});
    }
    private void loadLog(File f){final CsvLog[] data={null};task("Opening saved log…",()->{
        CsvLog a=new CsvLog();try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){r.readLine();String line;while((line=r.readLine())!=null){String[] v=line.split(",",-1);if(v.length<32||!v[0].equals("DATA"))throw new IOException("Invalid saved CSV");if(a.count++==0)a.first=v[1];a.last=v[1];a.stream=v[2];if(a.recent.size()==100)a.recent.removeFirst();a.recent.addLast(v);}}data[0]=a;
    },()->{selectedLog=f;selectedData=data[0];message="Showing saved phone copy.";render();});}
    private void showRecord(String[] a){StringBuilder b=new StringBuilder("Sequence "+a[1]+"\nStream "+a[2]+"\nNode "+a[4]+" · revision "+a[31]+"\n"+(a[6].equals("0")?timestamp(Long.parseLong(a[3])):"Invalid UTC")+"\nAge: "+a[5]+" ms\n\n");for(int i=1;i<=8;i++)b.append("CH ").append(i).append(": ").append(a[6+i]).append(" Hz · ").append(a[14+i]).append(" Ω\n").append(Protocol.flags(Integer.parseInt(a[22+i]))).append("\n");if(a.length==34)b.append("\nBattery: ").append(a[32].isEmpty()?"unavailable":a[32]+" %").append("\nPCB: ").append(a[33].isEmpty()?"unavailable":a[33]+" °C");new AlertDialog.Builder(this).setTitle("Stored record").setMessage(b).setPositiveButton("Close",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==20&&result==RESULT_OK&&data!=null&&pendingSave!=null){File source=pendingSave;Uri uri=data.getData();task("Saving CSV…",()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot open destination");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}},()->setMessage("CSV saved to the selected location."));}}
    private void health(){
        heading("Diagnostics");action("Refresh status and settings",()->refresh(true));
        if(status!=null){for(String key:new String[]{"device_id","time_valid","utc","ap_on","radio_ready","radio_error","frames","bad_frames","uart_overflows","missed_logs","radio_retries","stream_id","first_sequence","last_sequence","delivery_cursor","record_capacity","storage_fault"}){LinearLayout c=card();c.addView(text(key,12,MUTED));c.addView(text(status.optString(key),17,INK));}}
        para("Radio errors: 0 none; 1 configuration failed; 2 log read failed; 3 cursor save failed; 4 gateway ACK timeout. radio_ready confirms setup, not end-to-end delivery.");
        action("Disable node Wi-Fi",()->confirm("Turn off Beaver Wi-Fi?","The app will disconnect. Press the physical node button to enable Wi-Fi again.",()->task("Disabling node AP…",()->api.request("ap/disable","POST",null),()->{releaseRequest();api.network=null;connected=false;status=null;config=null;message="AP shutdown requested. Press the node button to reconnect.";render();})));
        para("Beaver Node 0.1.0 · Firmware API v1\nAll node communication uses the selected Wi-Fi network. No cloud service or analytics.");
    }
    private final class GraphView extends View {
        final List<String[]> rows;int channel;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GraphView(Context c,List<String[]> rows,int channel){super(c);this.rows=rows;this.channel=channel;setContentDescription("Frequency history plot");}
        protected void onDraw(Canvas c){super.onDraw(c);float left=dp(55),right=getWidth()-dp(12),top=dp(26),bottom=getHeight()-dp(27);double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;long begin=Long.MAX_VALUE,end=Long.MIN_VALUE;
            for(String[] a:rows)if(a[6].equals("0")&&a[22+channel].equals("0")){double f=Double.parseDouble(a[6+channel]);min=Math.min(min,f);max=Math.max(max,f);long t=Long.parseLong(a[3]);begin=Math.min(begin,t);end=Math.max(end,t);}
            paint.setColor(MUTED);paint.setTextSize(dp(11));if(!Double.isFinite(min)){c.drawText("No valid readings to plot",dp(12),dp(40),paint);return;}
            if(min==max){min-=1;max+=1;}
            c.drawText(String.format(Locale.US,"%.0f Hz",max),0,top,paint);c.drawText(String.format(Locale.US,"%.0f Hz",min),0,bottom,paint);c.drawText("Earlier",left,bottom+dp(20),paint);c.drawText("Later",right-dp(30),bottom+dp(20),paint);
            paint.setStrokeWidth(dp(1));c.drawLine(left,top,left,bottom,paint);c.drawLine(left,bottom,right,bottom,paint);paint.setColor(GREEN);
            for(String[] a:rows)if(a[6].equals("0")&&a[22+channel].equals("0")){double f=Double.parseDouble(a[6+channel]);long t=Long.parseLong(a[3]);float x=left+(right-left)*(end==begin?.5f:(float)(t-begin)/(end-begin));float y=bottom-(bottom-top)*(float)((f-min)/(max-min));c.drawCircle(x,y,dp(3),paint);}
        }
    }
}
