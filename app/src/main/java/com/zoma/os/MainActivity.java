package com.zoma.os;

import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    volatile boolean running=false, simulated=false;
    volatile int speed=-1,rpm=-1,coolant=-999,load=-1,fuel=-1;
    volatile String status="DISCONNECTED";
    volatile double tripKm=0;
    long lastSample=0;
    Thread worker;
    BluetoothSocket btSocket;
    Socket tcpSocket;
    Dashboard dashboard;
    int centerMode=0; // 0 road, 1 analog, 2 digital
    int theme=0; // 0 cyan, 1 amber, 2 emerald
    final Handler ui=new Handler();
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override public void onCreate(Bundle b){super.onCreate(b);centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);dashboard=new Dashboard();setContentView(dashboard);}
    void resetReadings(){speed=-1;rpm=-1;coolant=-999;load=-1;fuel=-1;lastSample=0;}
    void stop(){running=false;simulated=false;try{if(btSocket!=null)btSocket.close();}catch(Exception ignored){}try{if(tcpSocket!=null)tcpSocket.close();}catch(Exception ignored){}btSocket=null;tcpSocket=null;}
    void setStatus(String s){status=s;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void setSample(int s,int r,int t,int l,int f){long now=System.currentTimeMillis();if(!simulated && speed>=0 && lastSample>0 && now-lastSample<5000)tripKm+=(speed+s)/2.0*(now-lastSample)/3600000.0;lastSample=now;speed=s;rpm=r;coolant=t;load=l;fuel=f;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void chooseCenterMode(){final String[] modes={"ROAD COCKPIT","ANALOG CLOCK","DIGITAL CLOCK"};new AlertDialog.Builder(this).setTitle("Center display style").setSingleChoiceItems(modes,centerMode,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){centerMode=which;getPreferences(MODE_PRIVATE).edit().putInt("centerMode",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    int accent(){return Color.parseColor(theme==1?"#FFBD69":theme==2?"#60E8BA":"#60D7FF");}
    int mutedAccent(){return Color.parseColor(theme==1?"#A66A2C":theme==2?"#17785D":"#17658B");}
    void chooseTheme(){final String[] themes={"ICE BLUE","AMBER NIGHT","EMERALD"};new AlertDialog.Builder(this).setTitle("ZomaOS Theme Studio").setSingleChoiceItems(themes,theme,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){theme=which;getPreferences(MODE_PRIVATE).edit().putInt("theme",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    void openMusic(){launch(new String[]{"com.soundcloud.android","com.google.android.music","com.android.music","com.spotify.music"},"");}
    void openMaps(){launch(new String[]{"com.google.android.apps.maps","com.sygic.aura","com.mapswithme.maps.pro"},"");}
    void showMode(){final String[] modes={"DEMO (clearly simulated)","Bluetooth ELM327 (paired)","Phone bridge over Wi-Fi", "Disconnect / live blank", "Reset trip"};new AlertDialog.Builder(this).setTitle("ZomaOS Telemetry Source").setItems(modes,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)demo();if(which==1)chooseBluetooth();if(which==2)bridgeDialog();if(which==3){stop();simulated=false;resetReadings();setStatus("DISCONNECTED");}if(which==4){tripKm=0;dashboard.invalidate();}}}).show();}
    void demo(){stop();resetReadings();simulated=true;setStatus("DEMO / NOT REAL DATA");new Thread(new Runnable(){public void run(){int i=0;while(simulated){int s=(int)(35+25*Math.sin(i/7.0));setSample(s,800+s*35,85+(i%7),25+(i%35),60);i++;try{Thread.sleep(900);}catch(Exception e){break;}}}}).start();}
    void chooseBluetooth(){BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null){toast("Android Bluetooth API unavailable; try phone bridge");return;}if(!adapter.isEnabled()){toast("Enable Bluetooth in Android settings first");return;}Set<BluetoothDevice> devices=adapter.getBondedDevices();if(devices==null||devices.isEmpty()){toast("Pair ELM327 in Android Bluetooth settings first");return;}final ArrayList<BluetoothDevice> list=new ArrayList<BluetoothDevice>(devices);String[] labels=new String[list.size()];for(int i=0;i<list.size();i++){BluetoothDevice dev=list.get(i);labels[i]=dev.getName()+"  ("+dev.getAddress()+")";}new AlertDialog.Builder(this).setTitle("Select paired OBD adapter").setItems(labels,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){connectBluetooth(list.get(i));}}).show();}
    void connectBluetooth(final BluetoothDevice device){stop();simulated=false;resetReadings();running=true;setStatus("BT CONNECTING...");worker=new Thread(new Runnable(){public void run(){try{BluetoothAdapter.getDefaultAdapter().cancelDiscovery();btSocket=device.createRfcommSocketToServiceRecord(SPP);btSocket.connect();setStatus("BT ELM327 LIVE");pollElm(btSocket.getInputStream(),btSocket.getOutputStream());}catch(Exception e){if(running)setStatus("BT ERROR: "+e.getClass().getSimpleName());}finally{stop();}}});worker.start();}
    void bridgeDialog(){final EditText field=new EditText(this);field.setSingleLine(true);field.setText("10.126.178.235:8765");new AlertDialog.Builder(this).setTitle("Phone bridge IP:port").setMessage("Requires a phone bridge server. This V2 includes client only; do not confuse this with OpenAuto port 5277.").setView(field).setPositiveButton("Connect",new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){connectBridge(field.getText().toString().trim());}}).setNegativeButton("Cancel",null).show();}
    void connectBridge(final String address){stop();simulated=false;resetReadings();running=true;setStatus("BRIDGE CONNECTING...");worker=new Thread(new Runnable(){public void run(){try{String[] parts=address.split(":");if(parts.length!=2)throw new IOException("Bad address");tcpSocket=new Socket();tcpSocket.connect(new InetSocketAddress(parts[0],Integer.parseInt(parts[1])),4000);tcpSocket.setSoTimeout(5000);setStatus("PHONE BRIDGE LIVE");BufferedReader in=new BufferedReader(new InputStreamReader(tcpSocket.getInputStream(),"US-ASCII"));String line;while(running&&(line=in.readLine())!=null){int[] v=parseBridge(line);if(v!=null)setSample(v[0],v[1],v[2],v[3],v[4]);}}catch(Exception e){if(running)setStatus("BRIDGE ERROR: "+e.getClass().getSimpleName());}finally{stop();}}});worker.start();}
    // Bridge protocol: SPEED=42;RPM=1700;COOLANT=88;LOAD=30;FUEL=-1\n
    static int[] parseBridge(String line){int[] vals={-1,-1,-999,-1,-1};String[] keys={"SPEED","RPM","COOLANT","LOAD","FUEL"};String[] parts=line.trim().split(";");for(String part:parts){String[] kv=part.split("=",2);if(kv.length!=2)continue;for(int i=0;i<keys.length;i++)if(keys[i].equalsIgnoreCase(kv[0].trim()))try{vals[i]=Integer.parseInt(kv[1].trim());}catch(Exception ignored){}}return vals;}
    // ELM327 standard read-only OBD mode 01 PIDs; never send reset/clear DTC or manufacturer commands.
    void pollElm(InputStream in,OutputStream out)throws IOException{command(in,out,"ATZ",3500);command(in,out,"ATE0",1500);command(in,out,"ATL0",1500);command(in,out,"ATS0",1500);command(in,out,"ATH0",1500);command(in,out,"ATSP0",2000);int misses=0;while(running){int s=readPid(in,out,"010D",1),r=readPid(in,out,"010C",2),t=readPid(in,out,"0105",1),l=readPid(in,out,"0104",1),f=readPid(in,out,"012F",1);if(s<0&&r<0){misses++;if(misses>3)setStatus("OBD NO ECU RESPONSE");}else{misses=0;setStatus("BT ELM327 LIVE");}setSample(s,r,t,l,f);try{Thread.sleep(350);}catch(InterruptedException e){break;}}}
    String command(InputStream in,OutputStream out,String cmd,int timeout)throws IOException{out.write((cmd+"\r").getBytes("US-ASCII"));out.flush();StringBuilder sb=new StringBuilder();long until=System.currentTimeMillis()+timeout;while(running&&System.currentTimeMillis()<until){if(in.available()>0){int b=in.read();if(b<0)throw new EOFException();if(b=='>')break;if(sb.length()<1500)sb.append((char)b);}else try{Thread.sleep(15);}catch(InterruptedException e){throw new IOException(e);}}return sb.toString().toUpperCase(Locale.US);}
    int readPid(InputStream in,OutputStream out,String cmd,int bytes)throws IOException{String response=command(in,out,cmd,1800).replaceAll("[^0-9A-F]"," ");String hex=response.replace(" ","");String prefix="41"+cmd.substring(2);int pos=hex.indexOf(prefix);if(pos<0||hex.length()<pos+prefix.length()+bytes*2)return -1;try{int a=Integer.parseInt(hex.substring(pos+4,pos+6),16);int b=bytes>1?Integer.parseInt(hex.substring(pos+6,pos+8),16):0;if(cmd.equals("010D"))return a;if(cmd.equals("010C"))return (a*256+b)/4;if(cmd.equals("0105"))return a-40;if(cmd.equals("0104")||cmd.equals("012F"))return a*100/255;}catch(Exception ignored){}return -1;}
    void launch(String[] packages,String fallback){for(String pkg:packages){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent!=null){startActivity(intent);return;}}if("settings".equals(fallback)){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));return;}if("apps".equals(fallback)){startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS));return;}toast("App not found");}
    void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){stop();simulated=false;super.onDestroy();}
    class Dashboard extends View {
        final Paint p=new Paint(3); Canvas c;
        Dashboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        int col(String v){return Color.parseColor(v);}
        void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setShader(null);}
        void box(float l,float t,float r,float b,int color){fill(color);c.drawRoundRect(new RectF(l,t,r,b),13,13,p);}
        void text(String v,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);c.drawText(v,x,y,p);}
        void centerText(String v,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);c.drawText(v,x-p.measureText(v)/2,y,p);}
        void line(float x,float y,float xx,float yy,int color,float width){fill(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
        void arc(float cx,float cy,float radius,float from,float sweep,int color,float width){fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(width);c.drawArc(new RectF(cx-radius,cy-radius,cx+radius,cy+radius),from,sweep,false,p);p.setStyle(Paint.Style.FILL);}
        String value(int n){return n<0?"--":String.valueOf(n);}
        void info(float x,float y,String name,String val,String unit){box(x,y,x+177,y+76,col("#12283B"));text(name,x+13,y+22,13,col("#91AFC4"),true);text(val,x+13,y+58,29,Color.WHITE,true);text(unit,x+115,y+57,13,accent(),true);}
        void analog(){Calendar t=Calendar.getInstance();float cx=400,cy=220;arc(cx,cy,116,0,360,col("#183B54"),7);arc(cx,cy,116,215,110,accent(),7);
            for(int i=0;i<12;i++){double a=i*Math.PI/6-Math.PI/2;float x=(float)Math.cos(a),y=(float)Math.sin(a);line(cx+x*94,cy+y*94,cx+x*106,cy+y*106,i%3==0?Color.WHITE:col("#527B95"),i%3==0?3:2);}
            float sec=t.get(Calendar.SECOND),min=t.get(Calendar.MINUTE)+sec/60f,h=t.get(Calendar.HOUR)+min/60f;
            hand(cx,cy,(float)(h*Math.PI/6-Math.PI/2),56,6,Color.WHITE);hand(cx,cy,(float)(min*Math.PI/30-Math.PI/2),80,4,accent());hand(cx,cy,(float)(sec*Math.PI/30-Math.PI/2),88,2,col("#FFAD65"));fill(Color.WHITE);c.drawCircle(cx,cy,5,p);centerText("ANALOG TIME",cx,365,15,col("#8BA6BD"),true);}
        void hand(float cx,float cy,float a,float len,float width,int color){line(cx,cy,cx+(float)Math.cos(a)*len,cy+(float)Math.sin(a)*len,color,width);}
        void digital(){String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());centerText(time,400,239,87,Color.WHITE,true);String date=new SimpleDateFormat("EEEE, dd MMM yyyy",Locale.getDefault()).format(new Date());centerText(date,400,284,20,accent(),false);centerText("LOCAL TIME  /  NO OBD REQUIRED",400,346,13,col("#91AFC4"),true);}
        void road(){Path path=new Path();path.moveTo(278,378);path.lineTo(373,116);path.lineTo(427,116);path.lineTo(522,378);path.close();fill(col("#132B3D"));c.drawPath(path,p);
            line(277,378,373,116,accent(),3);line(523,378,427,116,accent(),3);
            for(int i=0;i<5;i++){float y=150+i*i*13;float half=2+i*2;line(400-half,y,400+half,y,col("#83B5D3"),2);}
            // Stylized lightweight 2D vehicle silhouette; no 3D engine.
            fill(col("#7BDAFF"));c.drawRoundRect(new RectF(370,268,430,346),17,17,p);
            fill(col("#17354B"));c.drawRoundRect(new RectF(379,281,421,308),8,8,p);
            box(371,321,381,338,col("#FF7D65"));box(419,321,429,338,col("#FF7D65"));
            centerText("ROAD VIEW",400,102,16,accent(),true);centerText("VISUAL ONLY - NOT NAVIGATION",400,370,11,col("#91AFC4"),false);}
        @Override protected void onDraw(Canvas canvas){c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);c.drawColor(col("#07121F"));
            box(9,9,791,56,col("#10283D"));text("ZOMA OS",24,38,25,accent(),true);text("V4  SMART COCKPIT",180,37,14,col("#B3D1E5"),true);
            text(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),700,37,23,Color.WHITE,true);
            String state=simulated?"DEMO / SIMULATED":status;centerText(state,400,77,13,simulated?col("#FFBC68"):col("#9DB7C8"),true);
            box(11,90,203,385,col("#0D2031"));box(597,90,789,385,col("#0D2031"));
            info(19,100,"SPEED",value(speed),"km/h");info(19,185,"COOLANT",coolant==-999?"--":String.valueOf(coolant),"C");
            info(19,270,"TRIP",String.format(Locale.US,"%.1f",tripKm),"km");
            info(605,100,"ENGINE RPM",value(rpm),"rpm");info(605,185,"ENGINE LOAD",value(load),"%");info(605,270,"FUEL LEVEL",value(fuel),"%");
            if(centerMode==0)road();else if(centerMode==1)analog();else digital();
            box(9,394,791,472,col("#10283D"));String[] buttons={"OBD","AUTO","VIEW","MUSIC","MAPS","APPS","THEME"};
            for(int i=0;i<buttons.length;i++){float l=16+i*111;box(l,405,l+103,462,i==1?col("#235779"):col("#1A374E"));centerText(buttons[i],l+51,438,15,Color.WHITE,true);}
            if(simulated){box(300,365,500,390,col("#74461E"));centerText("SIMULATED / NOT LIVE",400,383,13,Color.WHITE,true);}
            c.restore();postInvalidateDelayed(1000);}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();if(y>=394){int i=(int)((x-16)/111);switch(i){case 0:showMode();break;case 1:launch(new String[]{"me.ri3d.openauto"},"");break;case 2:chooseCenterMode();break;case 3:openMusic();break;case 4:openMaps();break;case 5:launch(new String[]{},"apps");break;case 6:chooseTheme();break;}}return true;}
    }
}
