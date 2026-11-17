package com.zoma.os;

import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.content.*;
import android.graphics.*;
import android.view.KeyEvent;
import android.view.*;
import android.widget.*;
import android.media.MediaPlayer;
import android.provider.MediaStore;
import android.database.Cursor;
import android.net.Uri;
import android.content.pm.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import android.media.MediaMetadataRetriever;

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
    Bitmap nexusRoad; Bitmap signatureClock; Bitmap signatureDigital;
    String trackTitle="", trackArtist="", metadataSource="";
    long metadataTime=0;
    BroadcastReceiver musicInfoReceiver;
    MediaPlayer localPlayer;
    ArrayList<String> localTitles=new ArrayList<String>();
    ArrayList<String> localArtists=new ArrayList<String>();
    ArrayList<Long> localIds=new ArrayList<Long>();
    ArrayList<File> usbTracks=new ArrayList<File>();
    int usbIndex=-1; boolean usbMode=false;
    Bitmap concept;
    int localIndex=-1;
    boolean localMode=false;


    int centerMode=0; // 0 road, 1 analog, 2 digital
    int theme=0; // 0 cyan, 1 amber, 2 emerald
    final Handler ui=new Handler();
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override public void onCreate(Bundle b){super.onCreate(b);centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);nexusRoad=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.nexus_road);signatureClock=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.signature_clock);signatureDigital=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.signature_digital);concept=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.concept_v73);dashboard=new Dashboard();setContentView(dashboard);startMusicDiscovery();}
    // Discovery only: some players publish public metadata broadcasts, many factory players do not.
    void startMusicDiscovery(){
        IntentFilter filter=new IntentFilter();
        String[] actions={"com.android.music.metachanged","com.android.music.playstatechanged",
            "com.htc.music.metachanged","com.miui.player.metachanged",
            "com.sonyericsson.music.metachanged","com.spotify.music.metadatachanged"};
        for(String a:actions)filter.addAction(a);
        musicInfoReceiver=new BroadcastReceiver(){@Override public void onReceive(Context ctx,Intent intent){
            try{
                String title=intent.getStringExtra("track");
                if(title==null)title=intent.getStringExtra("title");
                String artist=intent.getStringExtra("artist");
                if(title!=null && title.trim().length()>0){
                    trackTitle=title.trim();trackArtist=artist==null?"":artist.trim();
                    metadataSource=intent.getAction();metadataTime=System.currentTimeMillis();
                    if(dashboard!=null)dashboard.invalidate();
                }
            }catch(Throwable err){android.util.Log.w("ZomaOS","Metadata probe ignored",err);}
        }};
        try{registerReceiver(musicInfoReceiver,filter);}catch(Throwable err){
            android.util.Log.w("ZomaOS","Metadata receiver unavailable",err);musicInfoReceiver=null;
        }
    }
    @Override protected void onDestroy(){
        if(musicInfoReceiver!=null){try{unregisterReceiver(musicInfoReceiver);}catch(Exception ignored){}}
        stop();releaseLocalPlayer();super.onDestroy();
    }
    void showMediaDiagnostics(){
        String selected=getPreferences(MODE_PRIVATE).getString("musicPackage","Not selected");
        String msg="Selected player: "+selected+"\nMetadata: "+
            (trackTitle.length()>0?trackTitle+" / "+trackArtist:"No compatible metadata broadcast detected")+
            "\nSource: "+(metadataSource.length()>0?metadataSource:"none")+
            "\n\nThe factory player may keep metadata private. This is a read-only test.";
        new AlertDialog.Builder(this).setTitle("Media diagnostics - V7").setMessage(msg)
            .setPositiveButton("OK",null).setNeutralButton("Choose player",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){chooseAnyMusicApp();}}).show();
    }
    void resetReadings(){speed=-1;rpm=-1;coolant=-999;load=-1;fuel=-1;lastSample=0;}
    void stop(){running=false;simulated=false;try{if(btSocket!=null)btSocket.close();}catch(Exception ignored){}try{if(tcpSocket!=null)tcpSocket.close();}catch(Exception ignored){}btSocket=null;tcpSocket=null;}
    void setStatus(String s){status=s;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void setSample(int s,int r,int t,int l,int f){long now=System.currentTimeMillis();if(!simulated && speed>=0 && lastSample>0 && now-lastSample<5000)tripKm+=(speed+s)/2.0*(now-lastSample)/3600000.0;lastSample=now;speed=s;rpm=r;coolant=t;load=l;fuel=f;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void chooseCenterMode(){final String[] modes={"ROAD COCKPIT","ANALOG CLOCK","DIGITAL CLOCK"};new AlertDialog.Builder(this).setTitle("Center display style").setSingleChoiceItems(modes,centerMode,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){centerMode=which;getPreferences(MODE_PRIVATE).edit().putInt("centerMode",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    int accent(){return Color.parseColor(theme==1?"#FFBD69":theme==2?"#60E8BA":"#60D7FF");}
    int mutedAccent(){return Color.parseColor(theme==1?"#A66A2C":theme==2?"#17785D":"#17658B");}
    void chooseTheme(){final String[] themes={"ICE BLUE","AMBER NIGHT","EMERALD"};new AlertDialog.Builder(this).setTitle("ZomaOS Theme Studio").setSingleChoiceItems(themes,theme,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){theme=which;getPreferences(MODE_PRIVATE).edit().putInt("theme",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    void openMusic(){
        String selected=getPreferences(MODE_PRIVATE).getString("musicPackage", "");
        if(selected.length()>0){Intent intent=getPackageManager().getLaunchIntentForPackage(selected);
            if(intent!=null){startActivity(intent);return;}}
        chooseMusicApp();
    }
    void chooseMusicApp(){
        final Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<ResolveInfo> all=getPackageManager().queryIntentActivities(probe,0);
        final ArrayList<ResolveInfo> candidates=new ArrayList<ResolveInfo>();
        for(ResolveInfo info:all){String label=String.valueOf(info.loadLabel(getPackageManager())).toLowerCase(Locale.US);
            String pkg=info.activityInfo.packageName.toLowerCase(Locale.US);
            if(label.contains("music")||label.contains("audio")||label.contains("player")||pkg.contains("music"))candidates.add(info);}
        // Some factory players have non-obvious package names: allow selection from every app.
        if(candidates.size()==0)candidates.addAll(all);
        if(candidates.size()==0){toast("No launchable applications found");return;}
        final String[] labels=new String[candidates.size()];
        for(int i=0;i<candidates.size();i++)labels[i]=String.valueOf(candidates.get(i).loadLabel(getPackageManager()))+"  ["+candidates.get(i).activityInfo.packageName+"]";
        new AlertDialog.Builder(this).setTitle("Select original Music app").setItems(labels,new android.content.DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int index){String pkg=candidates.get(index).activityInfo.packageName;
                getPreferences(MODE_PRIVATE).edit().putString("musicPackage",pkg).apply();openMusic();}
        }).setNeutralButton("All apps",new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseAnyMusicApp();}})
          .setNegativeButton("Cancel",null).show();
    }
    void chooseAnyMusicApp(){
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final ArrayList<ResolveInfo> all=new ArrayList<ResolveInfo>(getPackageManager().queryIntentActivities(probe,0));
        String[] labels=new String[all.size()];for(int i=0;i<all.size();i++)labels[i]=String.valueOf(all.get(i).loadLabel(getPackageManager()))+" ["+all.get(i).activityInfo.packageName+"]";
        new AlertDialog.Builder(this).setTitle("Choose Music player").setItems(labels,new android.content.DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int index){getPreferences(MODE_PRIVATE).edit().putString("musicPackage",all.get(index).activityInfo.packageName).apply();openMusic();}
        }).setNegativeButton("Cancel",null).show();
    }
    // Android 4.1 (API 16) compatible. AudioManager.dispatchMediaKeyEvent was added in API 19.
    // Media-button broadcasts are best-effort: factory MCU players may ignore them.
    void mediaKey(int key){
        try {
            long t=android.os.SystemClock.uptimeMillis();
            sendMediaButton(key,KeyEvent.ACTION_DOWN,t);
            sendMediaButton(key,KeyEvent.ACTION_UP,t);
            toast("Media key sent; original player support unverified");
        } catch(Throwable e) {
            android.util.Log.e("ZomaOS","Media key failed",e);
            toast("Media control unsupported on this player");
        }
    }
    void sendMediaButton(int key,int action,long when){
        Intent intent=new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(when,when,action,key,0));
        sendOrderedBroadcast(intent,null);
    }
    void openRadio(){
        try {
            Intent i=getPackageManager().getLaunchIntentForPackage("com.spd.radio");
            if(i!=null){startActivity(i);return;}
            i=new Intent();i.setClassName("com.spd.radio","com.spd.radio.MainActivity");startActivity(i);
        } catch(Throwable e){toast("Factory FM Radio unavailable - check installed Radio app");}
    }
    void releaseLocalPlayer(){
        try{if(localPlayer!=null){localPlayer.reset();localPlayer.release();}}catch(Throwable ignored){}
        localPlayer=null;
    }
    boolean audioFile(File f){String n=f.getName().toLowerCase(Locale.US);return n.endsWith(".mp3")||n.endsWith(".wav")||n.endsWith(".ogg")||n.endsWith(".m4a")||n.endsWith(".aac")||n.endsWith(".flac");}
    void collectMusic(File folder,int depth,int limit){
        if(depth>9||usbTracks.size()>=limit||folder==null||!folder.canRead())return;
        File[] files=folder.listFiles();if(files==null)return;
        Arrays.sort(files,new Comparator<File>(){public int compare(File a,File b){return a.getName().compareToIgnoreCase(b.getName());}});
        for(File f:files){if(usbTracks.size()>=limit)break;if(f.isDirectory())collectMusic(f,depth+1,limit);else if(audioFile(f))usbTracks.add(f);}
    }
    void playUsb(int index){
        if(index<0||index>=usbTracks.size())return;
        releaseLocalPlayer();
        try{File f=usbTracks.get(index);localPlayer=new MediaPlayer();localPlayer.setDataSource(f.getAbsolutePath());
            localPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);localPlayer.prepare();localPlayer.start();
            usbIndex=index;usbMode=true;localMode=false;trackTitle=f.getName();trackArtist="USB / "+f.getParentFile().getName();metadataSource="Zoma USB player";
            try{MediaMetadataRetriever m=new MediaMetadataRetriever();m.setDataSource(f.getAbsolutePath());String t=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);String a=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);if(t!=null&&t.length()>0)trackTitle=t;if(a!=null&&a.length()>0)trackArtist=a;m.release();}catch(Exception ignored){}
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){nextUsb(1);}});dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play this USB audio file");}
    }
    void nextUsb(int direction){if(usbTracks.size()>0)playUsb((usbIndex+direction+usbTracks.size())%usbTracks.size());}
    void togglePlayback(){if(localPlayer==null){chooseUsbRoot();return;}try{if(localPlayer.isPlaying())localPlayer.pause();else localPlayer.start();dashboard.invalidate();}catch(Exception e){toast("Player error");}}
    void showUsbTracks(){
        if(usbTracks.isEmpty()){toast("No supported audio found. Choose a different folder.");return;}
        final String[] labels=new String[usbTracks.size()];for(int i=0;i<labels.length;i++)labels[i]=usbTracks.get(i).getName();
        new AlertDialog.Builder(this).setTitle("USB MUSIC  •  "+labels.length+" files").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){playUsb(i);}})
            .setNeutralButton("Choose folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseUsbRoot();}}).setNegativeButton("Close",null).show();
    }
    void chooseUsbRoot(){
        LinkedHashSet<String> paths=new LinkedHashSet<String>();
        String[] roots={"/mnt/usb_storage","/mnt/usb","/mnt/udisk","/mnt/udisk1","/mnt/usbhost1","/mnt/usbhost2","/mnt/media_rw","/storage","/storage/usb0","/storage/usb1","/storage/udisk0","/storage/usbdisk","/mnt/sdcard","/sdcard","/mnt"};
        for(String path:roots){File f=new File(path);if(f.exists()&&f.isDirectory()&&f.canRead())paths.add(f.getAbsolutePath());}
        String env=System.getenv("SECONDARY_STORAGE");if(env!=null)for(String p:env.split(":")){File f=new File(p);if(f.isDirectory()&&f.canRead())paths.add(p);}
        if(paths.isEmpty()){toast("No readable storage. Check USB mounting.");return;}
        final String[] options=paths.toArray(new String[paths.size()]);
        new AlertDialog.Builder(this).setTitle("Choose USB / storage folder").setItems(options,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){browseFolder(new File(options[n]));}}).setNegativeButton("Cancel",null).show();
    }
    void browseFolder(final File folder){
        File[] entries=folder.listFiles();if(entries==null){toast("Folder not readable");return;}
        final ArrayList<File> children=new ArrayList<File>();for(File f:entries)if(f.isDirectory()||audioFile(f))children.add(f);
        Collections.sort(children,new Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});
        String[] names=new String[children.size()];for(int i=0;i<names.length;i++)names[i]=(children.get(i).isDirectory()?"[DIR]  ":"♪  ")+children.get(i).getName();
        new AlertDialog.Builder(this).setTitle(folder.getAbsolutePath()).setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){File f=children.get(n);if(f.isDirectory())browseFolder(f);else{usbTracks.clear();collectMusic(folder,0,2000);int pos=usbTracks.indexOf(f);playUsb(pos<0?0:pos);}}})
            .setPositiveButton("Play all in folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){usbTracks.clear();collectMusic(folder,0,2000);if(!usbTracks.isEmpty()){playUsb(0);showUsbTracks();}else toast("No supported audio files found");}})
            .setNeutralButton("Up",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){File parent=folder.getParentFile();if(parent!=null&&parent.canRead())browseFolder(parent);else chooseUsbRoot();}})
            .setNegativeButton("Storage roots",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseUsbRoot();}}).show();
    }
    void loadLocalLibrary(){
        localTitles.clear();localArtists.clear();localIds.clear();
        Cursor cursor=null;
        try{
            cursor=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST},
                MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" ASC");
            if(cursor!=null)while(cursor.moveToNext()){
                localIds.add(cursor.getLong(0));localTitles.add(cursor.getString(1));localArtists.add(cursor.getString(2));
            }
        }catch(Throwable e){android.util.Log.w("ZomaOS","Music library unavailable",e);}
        finally{if(cursor!=null)cursor.close();}
    }
    void playLocal(int index){
        if(index<0||index>=localIds.size())return;
        releaseLocalPlayer();
        try{
            Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,localIds.get(index));
            localPlayer=new MediaPlayer();localPlayer.setDataSource(this,uri);
            localPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            localPlayer.prepare();localPlayer.start();localIndex=index;localMode=true;usbMode=false;
            trackTitle=localTitles.get(index);trackArtist=localArtists.get(index);metadataSource="ZomaOS local player";
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){nextLocal(1);}});
            dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play selected audio file");}
    }
    void nextLocal(int step){if(localIds.size()>0)playLocal((localIndex+step+localIds.size())%localIds.size());}
    void toggleLocal(){if(localPlayer==null){openLocalMusic();return;}if(localPlayer.isPlaying())localPlayer.pause();else localPlayer.start();dashboard.invalidate();}
    void openLocalMusic(){
        loadLocalLibrary();
        if(localTitles.size()==0){new AlertDialog.Builder(this).setTitle("Zoma Music")
            .setMessage("No local songs found in Android MediaStore. Copy MP3 files to Music, then rescan media or reboot.")
            .setPositiveButton("OK",null).show();return;}
        String[] names=new String[localTitles.size()];
        for(int i=0;i<names.length;i++)names[i]=localTitles.get(i)+"  -  "+localArtists.get(i);
        new AlertDialog.Builder(this).setTitle("ZOMA MUSIC  •  "+names.length+" tracks")
            .setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){playLocal(which);}})
            .setNeutralButton("Factory Music",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){openMusic();}})
            .setNegativeButton("Close",null).show();
    }
    void musicHub(){
        final String[] choices={"USB MUSIC - browse flash drive", "ZOMA MUSIC - indexed library", "GEELY MUSIC - original player", "FM RADIO - original tuner", "ANDROID AUTO - OpenAuto"};
        new AlertDialog.Builder(this).setTitle("ZomaOS Entertainment Hub").setItems(choices,new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int n){if(n==0)chooseUsbRoot();else if(n==1)openLocalMusic();else if(n==2)openMusic();else if(n==3)openRadio();else launch(new String[]{"me.ri3d.openauto"},"");}
        }).setNegativeButton("Close",null).show();
    }
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
    class Dashboard extends View {
        final Paint p=new Paint(3); Canvas c;
        Dashboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        int col(String v){return Color.parseColor(v);}
        void gradient(float l,float t,float r,float b,int top,int bottom){
            fill(top);p.setShader(new LinearGradient(l,t,l,b,top,bottom,Shader.TileMode.CLAMP));
            c.drawRect(l,t,r,b,p);p.setShader(null);
        }
        void glow(float x,float y,float radius,int color){
            fill(color);p.setShader(new RadialGradient(x,y,radius,new int[]{color,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            c.drawCircle(x,y,radius,p);p.setShader(null);
        }
        void polygon(float[] pts,int color){fill(color);Path path=new Path();path.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);path.close();c.drawPath(path,p);}
        void fill(int color){p.setColor(color);p.setStyle(Paint.Style.FILL);p.setShader(null);}
        void box(float l,float t,float r,float b,int color){fill(color);c.drawRoundRect(new RectF(l,t,r,b),13,13,p);}
        void text(String v,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);c.drawText(v,x,y,p);}
        void centerText(String v,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(size);c.drawText(v,x-p.measureText(v)/2,y,p);}
        void line(float x,float y,float xx,float yy,int color,float width){fill(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
        void arc(float cx,float cy,float radius,float from,float sweep,int color,float width){fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(width);c.drawArc(new RectF(cx-radius,cy-radius,cx+radius,cy+radius),from,sweep,false,p);p.setStyle(Paint.Style.FILL);}
        String value(int n){return n<0?"--":String.valueOf(n);}
        void info(float x,float y,String name,String val,String unit){box(x,y,x+177,y+76,col("#12283B"));text(name,x+13,y+22,13,col("#91AFC4"),true);text(val,x+13,y+58,29,Color.WHITE,true);text(unit,x+115,y+57,13,accent(),true);}
        void analog(){
            // High-resolution watch face asset. Only hands are drawn at runtime.
            float cx=400,cy=224;
            if(signatureClock!=null){fill(Color.WHITE);c.drawBitmap(signatureClock,null,new RectF(cx-155,cy-155,cx+155,cy+155),p);}
            Calendar t=Calendar.getInstance();
            float sec=t.get(Calendar.SECOND)+t.get(Calendar.MILLISECOND)/1000f;
            float min=t.get(Calendar.MINUTE)+sec/60f;
            float hour=t.get(Calendar.HOUR)+min/60f;
            hand(cx+2,cy+3,(float)(hour*Math.PI/6-Math.PI/2),69,9,Color.argb(150,0,0,0));
            hand(cx,cy,(float)(hour*Math.PI/6-Math.PI/2),69,7,col("#E9F4F8"));
            hand(cx+2,cy+3,(float)(min*Math.PI/30-Math.PI/2),104,7,Color.argb(150,0,0,0));
            hand(cx,cy,(float)(min*Math.PI/30-Math.PI/2),104,4,accent());
            hand(cx,cy,(float)(sec*Math.PI/30-Math.PI/2),113,2,col("#FFAD65"));
            fill(col("#CDECF4"));c.drawCircle(cx,cy,7,p);fill(col("#092638"));c.drawCircle(cx,cy,3,p);
            centerText("SIGNATURE CHRONOGRAPH",cx,391,12,col("#A2C9D8"),true);
        }
        void hand(float cx,float cy,float a,float len,float width,int color){line(cx,cy,cx+(float)Math.cos(a)*len,cy+(float)Math.sin(a)*len,color,width);}
        void digital(){
            if(signatureDigital!=null){fill(Color.WHITE);c.drawBitmap(signatureDigital,null,new RectF(190,104,567,333),p);}
            String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
            centerText(time,378,247,79,Color.WHITE,true);
            String date=new SimpleDateFormat("EEE, dd MMM yyyy",Locale.getDefault()).format(new Date());
            centerText(date,378,285,18,accent(),false);
            centerText("ZOMA  /  SIGNATURE TIME",378,147,12,col("#9BBFD1"),true);
        }
        void road(){
            // Full-bleed cinematic hero, image is a lightweight local asset (not a map).
            if(nexusRoad!=null){fill(Color.WHITE);c.drawBitmap(nexusRoad,null,new RectF(170,62,790,393),p);}
            gradient(170,62,790,133,Color.argb(205,3,11,24),Color.argb(0,3,11,24));
            gradient(170,331,790,394,Color.argb(0,2,9,21),Color.argb(225,2,9,21));
            fill(Color.argb(38,2,10,23));c.drawRect(170,62,790,393,p);
            box(195,82,390,109,Color.argb(175,5,18,31));
            text("NEXUS  /  DRIVE SCENE",208,101,12,Color.WHITE,true);
            box(342,351,546,379,Color.argb(210,4,14,27));
            centerText("VISUAL ONLY  \u2022  NOT LIVE MAP",444,369,11,col("#B9DDEB"),true);
        }
        void mediaCard(){
            // Floating glass-like media panel, visually distinct from legacy rectangles.
            box(557,169,783,370,Color.argb(225,6,18,32));
            line(567,173,773,173,Color.argb(160,65,204,239),2);
            text("ENTERTAINMENT",572,193,12,accent(),true);
            box(571,205,620,252,col("#173E56"));
            centerText("\u266b",595,238,30,accent(),true);
            text(localMode?"ZOMA MUSIC":"GEELY MUSIC",629,220,13,Color.WHITE,true);
            String title=trackTitle.length()>0?trackTitle:"No track data";
            if(title.length()>22)title=title.substring(0,21)+"...";
            text(title,629,240,11,col("#C4D8E4"),false);
            if(trackTitle.length()>0){String a=trackArtist;
                if(a.length()>25)a=a.substring(0,24)+"...";
                text(a,629,252,10,col("#9DB8C7"),false);}
            String[] controls={"|<","II",">|"};
            for(int i=0;i<3;i++){float l=565+i*72;box(l,280,l+66,319,i==1?col("#1776A2"):col("#193A52"));centerText(controls[i],l+33,306,17,Color.WHITE,true);}
            box(565,329,776,360,col("#184C67"));centerText("MUSIC  /  RADIO  /  AUTO",670,350,12,Color.WHITE,true);
        }
        @Override protected void onDraw(Canvas canvas){
            c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);
            if(concept!=null){fill(Color.WHITE);c.drawBitmap(concept,null,new RectF(0,0,800,480),p);}else gradient(0,0,800,480,col("#03111F"),col("#071F34"));
            // Keep the actual concept artwork. Replace baked-in time and track data with live, readable layers.
            box(674,0,799,41,col("#061321"));
            String now=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
            centerText(now,738,23,26,Color.WHITE,true);
            centerText(new SimpleDateFormat("EEE, MMM d, yyyy",Locale.ENGLISH).format(new Date()),737,37,9,col("#C4E3F5"),false);
            box(108,62,375,155,Color.argb(246,4,17,30));
            centerText(now,240,115,51,Color.WHITE,true);
            centerText(new SimpleDateFormat("EEE, MMM d, yyyy",Locale.ENGLISH).format(new Date()),240,137,14,col("#9ACDE5"),false);
            // Media display: no fabricated song title, artwork, elapsed time, or USB count.
            box(101,194,467,335,Color.argb(250,5,21,38));
            line(103,195,463,195,col("#2F9DC8"),1);
            String title=usbMode||localMode?trackTitle:"Select USB Music or Geely Music";
            if(title.length()>38)title=title.substring(0,35)+"...";
            text(title,122,229,18,Color.WHITE,true);
            String artist=usbMode||localMode?trackArtist:"Original Geely player data unavailable";
            if(artist.length()>48)artist=artist.substring(0,45)+"...";
            text(artist,122,249,12,col("#A5CDE0"),false);
            text(usbMode?"SOURCE: USB":localMode?"SOURCE: LOCAL":"SOURCE: NOT CONNECTED",122,270,11,col("#64D8FF"),true);
            if(localPlayer!=null){try{int duration=localPlayer.getDuration();int position=localPlayer.getCurrentPosition();if(duration>0){box(121,280,445,283,col("#29495D"));box(121,280,121+324f*position/duration,283,col("#00C8FF"));text(String.format(Locale.US,"%d:%02d / %d:%02d",position/60000,(position/1000)%60,duration/60000,(duration/1000)%60),121,300,11,Color.WHITE,false);}}catch(Exception ignored){}}
            centerText("|<",190,323,22,Color.WHITE,true);centerText(localPlayer!=null&&localPlayer.isPlaying()?"II":">",296,323,22,col("#56DFFF"),true);centerText(">|",402,323,22,Color.WHITE,true);
            box(478,208,619,292,Color.argb(248,5,22,39));
            text("MEDIA SOURCE",491,230,12,col("#A6D7EB"),true);
            text(usbMode?"USB AUDIO":localMode?"LOCAL MUSIC":"USB / FM RADIO",491,253,14,Color.WHITE,true);
            text("TAP TO BROWSE",491,272,11,col("#51D3FF"),true);
            // Keep analog watch face from reference as an illustrative decorative element only.
            // No fake weather, climate controls or vehicle readings are wired to the car.
            box(9,424,798,472,Color.argb(245,5,17,30));
            String[] actions={"USB MUSIC","FM RADIO","ANDROID AUTO","GEELY MUSIC","OBD","APPS"};
            for(int i=0;i<actions.length;i++){float left=13+i*131;box(left,431,left+123,464,i==0?col("#115F86"):col("#102D45"));centerText(actions[i],left+61,451,11,Color.WHITE,true);}
            c.restore();postInvalidateDelayed(1000);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();
            if(y>=423){int i=(int)((x-13)/131);switch(i){case 0:chooseUsbRoot();break;case 1:openRadio();break;case 2:launch(new String[]{"me.ri3d.openauto"},"");break;case 3:openMusic();break;case 4:showMode();break;case 5:launch(new String[]{},"apps");break;}return true;}
            if(y>290&&y<337&&x>=135&&x<465){if(x<242){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else musicHub();}else if(x<350)togglePlayback();else{if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else musicHub();}return true;}
            if(x>=475&&x<=625&&y>=195&&y<=310){chooseUsbRoot();return true;}
            if(x<94&&y>=90&&y<405){if(y<174)musicHub();else if(y<240)chooseUsbRoot();else if(y<310)openRadio();else if(y<365)launch(new String[]{"me.ri3d.openauto"},"");else showMode();return true;}
            if(y>=190&&y<=290&&x>=100&&x<=465){if(usbMode)showUsbTracks();else musicHub();return true;}
            return true;
        }

    }
}
