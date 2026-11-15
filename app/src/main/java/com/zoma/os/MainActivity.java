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
    int centerMode=0; // 0 cockpit, 1 analog clock, 2 digital clock
    int theme=0; // 0 cyan, 1 amber, 2 emerald
    final Handler ui=new Handler();
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override public void onCreate(Bundle b){super.onCreate(b);centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);dashboard=new Dashboard();setContentView(dashboard);}
    void resetReadings(){speed=-1;rpm=-1;coolant=-999;load=-1;fuel=-1;lastSample=0;}
    void stop(){running=false;simulated=false;try{if(btSocket!=null)btSocket.close();}catch(Exception ignored){}try{if(tcpSocket!=null)tcpSocket.close();}catch(Exception ignored){}btSocket=null;tcpSocket=null;}
    void setStatus(String s){status=s;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void setSample(int s,int r,int t,int l,int f){long now=System.currentTimeMillis();if(!simulated && speed>=0 && lastSample>0 && now-lastSample<5000)tripKm+=(speed+s)/2.0*(now-lastSample)/3600000.0;lastSample=now;speed=s;rpm=r;coolant=t;load=l;fuel=f;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void chooseCenterMode(){final String[] modes={"3D ROAD / DRIVING","ANALOG CLOCK","DIGITAL CLOCK"};new AlertDialog.Builder(this).setTitle("Center display style").setSingleChoiceItems(modes,centerMode,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){centerMode=which;getPreferences(MODE_PRIVATE).edit().putInt("centerMode",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    int accent(){return Color.parseColor(theme==1?"#FFBD69":theme==2?"#60E8BA":"#60D7FF");}
    int mutedAccent(){return Color.parseColor(theme==1?"#A66A2C":theme==2?"#17785D":"#17658B");}
    void chooseTheme(){final String[] themes={"ICE BLUE","AMBER NIGHT","EMERALD"};new AlertDialog.Builder(this).setTitle("ZomaOS Theme Studio").setSingleChoiceItems(themes,theme,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){theme=which;getPreferences(MODE_PRIVATE).edit().putInt("theme",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    void openMusic(){launch(new String[]{"com.google.android.music","com.spotify.music","com.soundcloud.android","com.android.music"},"");}
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
    class Dashboard extends View{final Paint p=new Paint(3);Canvas c;Dashboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}int col(String s){return Color.parseColor(s);}void fill(int v){p.setColor(v);p.setStyle(Paint.Style.FILL);}void rect(float x,float y,float xx,float yy,int v){fill(v);c.drawRect(x,y,xx,yy,p);}void txt(String s,float x,float y,int size,int color){fill(color);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(size);c.drawText(s,x,y,p);}void line(float x,float y,float xx,float yy,int color,float width){fill(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}String fmt(int value){return value<0?"--":String.valueOf(value);}protected void onDraw(Canvas canvas){c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);c.drawColor(col("#081321"));rect(0,0,800,55,col("#0D2035"));txt("ZOMA OS  V3",18,35,24,accent());txt(new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),700,35,22,Color.WHITE);txt(simulated?"DEMO - SIMULATED":status,235,34,15,simulated?col("#FFC46C"):col("#9AB6CA"));
    // Center widget: road view or a real local clock; no OBD/GPS required for clock.
    if(centerMode==0){
      Path road=new Path();road.moveTo(310,372);road.lineTo(381,125);road.lineTo(419,125);road.lineTo(490,372);road.close();fill(col("#11273B"));c.drawPath(road,p);line(308,374,381,125,accent(),3);line(492,374,419,125,accent(),3);for(int i=0;i<5;i++){float y=170+i*i*9;line(396,y,404,y,col("#78B1D3"),2);}fill(col("#C4D5E4"));c.drawRoundRect(new RectF(374,282,426,351),12,12,p);fill(col("#25455F"));c.drawRoundRect(new RectF(380,293,420,319),7,7,p);
    }else if(centerMode==1){
      Calendar cal=Calendar.getInstance();float cx=400,cy=237,r=110;
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(accent());c.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.FILL);
      for(int i=0;i<12;i++){double theta=i*Math.PI/6-Math.PI/2;float x1=cx+(float)Math.cos(theta)*92,y1=cy+(float)Math.sin(theta)*92;float x2=cx+(float)Math.cos(theta)*103,y2=cy+(float)Math.sin(theta)*103;line(x1,y1,x2,y2,col("#9ADCF4"),i%3==0?4:2);}
      float sec=cal.get(Calendar.SECOND),min=cal.get(Calendar.MINUTE)+sec/60f,hour=cal.get(Calendar.HOUR)+min/60f;
      hand(cx,cy,(float)(hour*Math.PI/6-Math.PI/2),58,6,Color.WHITE);hand(cx,cy,(float)(min*Math.PI/30-Math.PI/2),82,4,accent());hand(cx,cy,(float)(sec*Math.PI/30-Math.PI/2),87,2,col("#FFB56B"));fill(Color.WHITE);c.drawCircle(cx,cy,5,p);
      txt("LOCAL TIME",351,374,16,col("#8BA6BD"));
    }else{
      String clock=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
      p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(76);p.setColor(Color.WHITE);float w=p.measureText(clock);c.drawText(clock,400-w/2,251,p);
      String date=new SimpleDateFormat("EEE, dd MMM yyyy",Locale.getDefault()).format(new Date());p.setTextSize(19);p.setColor(col("#80CDE9"));float dw=p.measureText(date);c.drawText(date,400-dw/2,297,p);
      txt("LOCAL TIME",350,350,16,col("#8BA6BD"));
    }
    txt("SPEED",34,102,17,col("#8BA6BD"));txt(fmt(speed),34,164,55,Color.WHITE);txt("km/h",38,189,16,accent());txt("ENGINE RPM",574,102,17,col("#8BA6BD"));txt(fmt(rpm),574,164,48,Color.WHITE);txt("rpm",578,189,16,accent());txt("COOLANT",34,250,17,col("#8BA6BD"));txt(coolant==-999?"--":String.valueOf(coolant),34,298,43,Color.WHITE);txt("C",107,298,19,accent());txt("ENGINE LOAD",574,250,17,col("#8BA6BD"));txt(fmt(load)+"%",574,298,40,Color.WHITE);txt("FUEL",574,341,15,col("#8BA6BD"));txt(fmt(fuel)+"%",650,341,23,Color.WHITE);txt(String.format(Locale.US,"TRIP %.2f km",tripKm),32,356,19,col("#A8C9DE"));
    rect(0,391,800,480,col("#0D2035"));String[] names={"OBD / DEMO","OPENAUTO","CLOCK / VIEW","MUSIC","THEME"};for(int i=0;i<5;i++){float left=10+i*159;rect(left,402,left+150,468,i==0?mutedAccent():col("#19334D"));txt(names[i],left+9,441,i==0?16:17,Color.WHITE);}if(simulated){rect(286,368,514,390,col("#8E521C"));txt("SIMULATED - NOT LIVE",299,385,14,Color.WHITE);}c.restore();postInvalidateDelayed(1000);}
    void hand(float cx,float cy,float radians,float length,float width,int color){line(cx,cy,cx+(float)Math.cos(radians)*length,cy+(float)Math.sin(radians)*length,color,width);}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();if(y>391){switch((int)(x/159)){case 0:showMode();break;case 1:launch(new String[]{"me.ri3d.openauto"},"");break;case 2:chooseCenterMode();break;case 3:openMusic();break;case 4:chooseTheme();break;}}return true;}}
}
