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
import android.media.AudioManager;
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
    Bitmap nexusRoad; Bitmap signatureClock; Bitmap signatureDigital; Bitmap daySuv;
    String trackTitle="", trackArtist="", metadataSource="";
    long metadataTime=0;
    BroadcastReceiver musicInfoReceiver;
    MediaPlayer localPlayer;
    ArrayList<String> localTitles=new ArrayList<String>();
    ArrayList<String> localArtists=new ArrayList<String>();
    ArrayList<Long> localIds=new ArrayList<Long>();
    ArrayList<File> usbTracks=new ArrayList<File>();
    int usbIndex=-1; boolean usbMode=false;
    Bitmap albumArt; boolean draggingSeek=false; boolean showAnalog=true; int selectedPage=0;
    AudioManager audioManager; boolean hasAudioFocus=false; boolean clock12=false; int appearance=0;
    AudioManager.OnAudioFocusChangeListener focusListener=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){pauseAudio();}}};
    // User-configurable dock. Empty preference means the original built-in action.
    final String[] dockDefaults={"USB FILES","FM RADIO","ANDROID AUTO","OBD","APPS"};
    final int[] dockEdges={103,239,372,509,640,793};
    boolean dockLongPressed=false;
    final Handler dockHandler=new Handler();
    Runnable pendingDockLongPress;
    int pressedDock=-1;
    String dockLabel(int index){
        String pkg=getPreferences(MODE_PRIVATE).getString("dock_pkg_"+index,"");
        if(pkg.length()==0)return dockDefaults[index];
        try{return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)));}
        catch(Exception e){return dockDefaults[index];}
    }
    void activateDock(int index){
        String pkg=getPreferences(MODE_PRIVATE).getString("dock_pkg_"+index,"");
        if(pkg.length()>0){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent!=null){stopAudio();startActivity(intent);return;}
            toast("App unavailable. Restore shortcut in long press.");return;
        }
        if(index==0)openRememberedUsb();else if(index==1)openRadio();
        else if(index==2)launch(new String[]{"me.ri3d.openauto"},"");
        else if(index==3)showMode();else showAppCenter();
    }
    void editDock(final int index){
        new AlertDialog.Builder(this).setTitle("Shortcut: "+dockLabel(index))
            .setItems(new String[]{"Set Program","Restore Default"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int choice){
                if(choice==1){getPreferences(MODE_PRIVATE).edit().remove("dock_pkg_"+index).apply();dashboard.invalidate();}
                else chooseDockProgram(index);
            }}).show();
    }
    void chooseDockProgram(final int index){
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final ArrayList<ResolveInfo> apps=new ArrayList<ResolveInfo>(getPackageManager().queryIntentActivities(probe,0));
        Collections.sort(apps,new Comparator<ResolveInfo>(){public int compare(ResolveInfo a,ResolveInfo b){return String.valueOf(a.loadLabel(getPackageManager())).compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager())));}});
        final String[] names=new String[apps.size()];
        for(int i=0;i<apps.size();i++)names[i]=String.valueOf(apps.get(i).loadLabel(getPackageManager()));
        new AlertDialog.Builder(this).setTitle("Set Program • "+dockDefaults[index]).setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            String pkg=apps.get(which).activityInfo.packageName;
            getPreferences(MODE_PRIVATE).edit().putString("dock_pkg_"+index,pkg).apply();dashboard.invalidate();
        }}).setNegativeButton("Cancel",null).show();
    }
    int dockIndex(float x){for(int i=0;i<5;i++)if(x>=dockEdges[i]&&x<dockEdges[i+1])return i;return -1;}
    String externalSource=""; long lastResumeSave=0;
    void setExternalSource(String name){externalSource=name;getPreferences(MODE_PRIVATE).edit().putString("last_external_source",name).apply();if(dashboard!=null)dashboard.invalidate();}
    void chooseMediaSource(){
        new AlertDialog.Builder(this).setTitle("Media source • manual selection")
          .setMessage("Factory FM/Bluetooth playback cannot be verified by this launcher on Android 4.1. Select a source to display it as SELECTED, not confirmed playing.")
          .setItems(new String[]{"USB music","FM Radio • launch factory app","Bluetooth audio • launch factory app","Clear external selection"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){
              if(n==0){setExternalSource("");openRememberedUsb();}
              else if(n==1)openRadio();
              else if(n==2){stopAudio();setExternalSource("BLUETOOTH");launch(new String[]{"com.spd.bluetooth","com.android.bluetooth","com.bt.music","com.spd.bt","com.mediatek.bluetooth"},"");}
              else setExternalSource("");
          }}).show();
    }
    void saveUsbResume(){
        if(!usbMode||usbIndex<0||usbIndex>=usbTracks.size()||localPlayer==null)return;
        try{File f=usbTracks.get(usbIndex);getPreferences(MODE_PRIVATE).edit().putString("resume_file",f.getAbsolutePath())
          .putInt("resume_position",localPlayer.getCurrentPosition()).putBoolean("resume_playing",localPlayer.isPlaying()).apply();}catch(Exception ignored){}
    }
    void restoreUsbResume(){
        if(!getPreferences(MODE_PRIVATE).getBoolean("resume_enabled",true))return;
        if(!getPreferences(MODE_PRIVATE).getBoolean("resume_playing",false))return;
        if(getPreferences(MODE_PRIVATE).getString("last_external_source","").length()>0)return;
        String path=getPreferences(MODE_PRIVATE).getString("resume_file","");if(path.length()==0)return;
        File f=new File(path);if(!f.isFile()||!f.canRead())return;
        if(usbTracks.isEmpty())restoreUsbFolder();
        for(int i=0;i<usbTracks.size();i++)if(path.equals(usbTracks.get(i).getAbsolutePath())){
            playUsb(i);if(localPlayer!=null)try{localPlayer.seekTo(Math.max(0,getPreferences(MODE_PRIVATE).getInt("resume_position",0)-1000));}catch(Exception ignored){}break;
        }
    }
    void chooseResumeSetting(){final boolean enabled=getPreferences(MODE_PRIVATE).getBoolean("resume_enabled",true);
      new AlertDialog.Builder(this).setTitle("USB startup playback").setSingleChoiceItems(new String[]{"Resume last playing USB track","Manual playback only"},enabled?0:1,
        new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){getPreferences(MODE_PRIVATE).edit().putBoolean("resume_enabled",n==0).apply();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    int localIndex=-1;
    boolean localMode=false;


    int centerMode=0; // 0 road, 1 analog, 2 digital
    int theme=0; // 0 cyan, 1 amber, 2 emerald
    final Handler ui=new Handler();
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override public void onCreate(Bundle b){super.onCreate(b);centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);nexusRoad=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.nexus_road);signatureClock=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.signature_clock);signatureDigital=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.signature_digital);daySuv=BitmapFactory.decodeResource(getResources(),com.zoma.os.R.drawable.day_suv);showAnalog=getPreferences(MODE_PRIVATE).getBoolean("showAnalog",true);clock12=getPreferences(MODE_PRIVATE).getBoolean("clock12",false);appearance=getPreferences(MODE_PRIVATE).getInt("appearance",0);audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);dashboard=new Dashboard();setContentView(dashboard);startMusicDiscovery();restoreUsbFolder();ui.postDelayed(new Runnable(){public void run(){restoreUsbResume();}},1800);}
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
    @Override protected void onPause(){saveUsbResume();super.onPause();pauseAudio();}
    @Override protected void onDestroy(){
        if(musicInfoReceiver!=null){try{unregisterReceiver(musicInfoReceiver);}catch(Exception ignored){}}
        stop();stopAudio();super.onDestroy();
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
    int accent(){return Color.parseColor(theme==1?"#FFBD69":theme==2?"#60E8BA":theme==3?"#C58AFF":theme==4?"#FF6D80":"#60D7FF");}
    int mutedAccent(){return Color.parseColor(theme==1?"#A66A2C":theme==2?"#17785D":theme==3?"#6C3992":theme==4?"#92374B":"#17658B");}
    void chooseTheme(){final String[] themes={"ICE BLUE","AMBER NIGHT","EMERALD","ROYAL PURPLE","CRIMSON RED"};new AlertDialog.Builder(this).setTitle("ZomaOS Theme Studio").setSingleChoiceItems(themes,theme,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){theme=which;getPreferences(MODE_PRIVATE).edit().putInt("theme",which).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    void openMusic(){
        stopAudio();
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
        stopAudio();setExternalSource("FM RADIO");
        try {
            Intent i=getPackageManager().getLaunchIntentForPackage("com.spd.radio");
            if(i!=null){startActivity(i);return;}
            i=new Intent();i.setClassName("com.spd.radio","com.spd.radio.MainActivity");startActivity(i);
        } catch(Throwable e){toast("Factory FM Radio unavailable - check installed Radio app");}
    }
    boolean requestMusicFocus(){
        if(audioManager==null)return true;
        int result=audioManager.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        hasAudioFocus=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!hasAudioFocus)toast("Audio output busy — stop other source first");
        return hasAudioFocus;
    }
    void pauseAudio(){try{if(localPlayer!=null&&localPlayer.isPlaying())localPlayer.pause();}catch(Exception ignored){}if(dashboard!=null)dashboard.invalidate();}
    void stopAudio(){releaseLocalPlayer();usbMode=false;localMode=false;trackTitle="";trackArtist="";if(audioManager!=null&&hasAudioFocus){audioManager.abandonAudioFocus(focusListener);hasAudioFocus=false;}if(dashboard!=null)dashboard.invalidate();}
    void releaseLocalPlayer(){
        try{if(localPlayer!=null){localPlayer.reset();localPlayer.release();}}catch(Throwable ignored){}
        localPlayer=null;albumArt=null;
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
        if(!requestMusicFocus())return;
        try{setExternalSource("");File f=usbTracks.get(index);albumArt=null;localPlayer=new MediaPlayer();localPlayer.setDataSource(f.getAbsolutePath());
            localPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);localPlayer.prepare();localPlayer.start();
            usbIndex=index;usbMode=true;localMode=false;trackTitle=f.getName();trackArtist="USB / "+f.getParentFile().getName();metadataSource="Zoma USB player";
            try{MediaMetadataRetriever m=new MediaMetadataRetriever();m.setDataSource(f.getAbsolutePath());String t=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);String a=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);if(t!=null&&t.length()>0)trackTitle=t;if(a!=null&&a.length()>0)trackArtist=a;byte[] art=m.getEmbeddedPicture();if(art!=null)albumArt=BitmapFactory.decodeByteArray(art,0,art.length);m.release();}catch(Exception ignored){}
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){nextUsb(1);}});saveUsbResume();dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play this USB audio file");}
    }
    void nextUsb(int direction){if(usbTracks.size()>0)playUsb((usbIndex+direction+usbTracks.size())%usbTracks.size());}
    void togglePlayback(){if(localPlayer==null){openRememberedUsb();return;}try{if(localPlayer.isPlaying())localPlayer.pause();else if(requestMusicFocus())localPlayer.start();saveUsbResume();dashboard.invalidate();}catch(Exception e){toast("Player error");}}
    void showUsbTracks(){
        if(usbTracks.isEmpty()){toast("No supported audio found. Choose a different folder.");return;}
        final String[] labels=new String[usbTracks.size()];for(int i=0;i<labels.length;i++)labels[i]=usbTracks.get(i).getName();
        new AlertDialog.Builder(this).setTitle("USB MUSIC  •  "+labels.length+" files").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){playUsb(i);}})
            .setNeutralButton("Choose folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseUsbRoot();}}).setNegativeButton("Close",null).show();
    }
    // Remember the chosen music directory across launcher restarts. Never auto-play on boot.
    void rememberUsbFolder(File folder){
        if(folder!=null&&folder.isDirectory())getPreferences(MODE_PRIVATE).edit().putString("usb_folder",folder.getAbsolutePath()).apply();
    }
    void restoreUsbFolder(){
        String saved=getPreferences(MODE_PRIVATE).getString("usb_folder","");
        if(saved.length()==0)return;
        File dir=new File(saved);
        if(!dir.isDirectory()||!dir.canRead())return; // USB unplugged: preserve path for next insertion.
        usbTracks.clear();collectMusic(dir,0,2000);
        if(!usbTracks.isEmpty()){usbMode=true;usbIndex=-1;trackTitle="USB ready • "+usbTracks.size()+" tracks";trackArtist=dir.getName();}
        if(dashboard!=null)dashboard.invalidate();
    }
    void openRememberedUsb(){
        if(!usbTracks.isEmpty()){showUsbTracks();return;}
        restoreUsbFolder();
        if(!usbTracks.isEmpty())showUsbTracks();else chooseUsbRoot();
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
        File[] entries=folder.listFiles();if(entries==null){toast("Cannot access folder");return;}
        final ArrayList<File> children=new ArrayList<File>();for(File f:entries){if(f.isDirectory()||audioFile(f))children.add(f);}
        Collections.sort(children,new Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});
        final Dialog dialog=new Dialog(this);dialog.setTitle("USB FILES • "+folder.getName());
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(12,8,12,8);panel.setBackgroundColor(Color.rgb(16,39,59));
        TextView heading=new TextView(this);heading.setText(folder.getAbsolutePath());heading.setTextColor(Color.WHITE);heading.setTextSize(16);heading.setPadding(8,8,8,12);panel.addView(heading);
        final String[] names=new String[children.size()];for(int i=0;i<names.length;i++)names[i]=(children.get(i).isDirectory()?"📁  ":"♫  ")+children.get(i).getName();
        ListView list=new ListView(this);list.setCacheColorHint(Color.TRANSPARENT);
        list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){@Override public View getView(int pos,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(pos,convert,parent);v.setTextColor(Color.WHITE);v.setTextSize(16);v.setPadding(12,9,8,9);return v;}});
        panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);Button up=new Button(this);up.setText("UP");buttons.addView(up,new LinearLayout.LayoutParams(0,-2,1));Button play=new Button(this);play.setText("PLAY ALL");buttons.addView(play,new LinearLayout.LayoutParams(0,-2,1));Button roots=new Button(this);roots.setText("ROOTS");buttons.addView(roots,new LinearLayout.LayoutParams(0,-2,1));Button home=new Button(this);home.setText("DASHBOARD");buttons.addView(home,new LinearLayout.LayoutParams(0,-2,1));panel.addView(buttons);
        home.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        up.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();File parent=folder.getParentFile();if(parent!=null&&parent.canRead())browseFolder(parent);else openRememberedUsb();}});
        roots.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();chooseUsbRoot();}});
        play.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();rememberUsbFolder(folder);usbTracks.clear();collectMusic(folder,0,2000);if(!usbTracks.isEmpty())playUsb(0);else toast("No supported audio found");}});
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int n,long id){File f=children.get(n);dialog.dismiss();if(f.isDirectory())browseFolder(f);else{rememberUsbFolder(folder);usbTracks.clear();collectMusic(folder,0,2000);int pos=usbTracks.indexOf(f);playUsb(pos<0?0:pos);}}});
        dialog.setContentView(panel);dialog.show();dialog.getWindow().setLayout(-1,-1);
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
            if(!requestMusicFocus())return;localPlayer=new MediaPlayer();localPlayer.setDataSource(this,uri);
            localPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            localPlayer.prepare();localPlayer.start();localIndex=index;localMode=true;usbMode=false;
            trackTitle=localTitles.get(index);trackArtist=localArtists.get(index);metadataSource="ZomaOS local player";
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){nextLocal(1);}});
            try{MediaMetadataRetriever mr=new MediaMetadataRetriever();mr.setDataSource(this,uri);byte[] art=mr.getEmbeddedPicture();if(art!=null)albumArt=BitmapFactory.decodeByteArray(art,0,art.length);mr.release();}catch(Exception ignored){}dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play selected audio file");}
    }
    void nextLocal(int step){if(localIds.size()>0)playLocal((localIndex+step+localIds.size())%localIds.size());}
    void toggleLocal(){togglePlayback();}
    void openLocalMusic(){
        loadLocalLibrary();
        if(localTitles.size()==0){openRememberedUsb();return;}
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
            public void onClick(DialogInterface d,int n){if(n==0)openRememberedUsb();else if(n==1)openLocalMusic();else if(n==2)openMusic();else if(n==3)openRadio();else{stopAudio();launch(new String[]{"me.ri3d.openauto"},"");}}
        }).setNegativeButton("Close",null).show();
    }
    // Best-effort media keys. Steering wheel may be routed privately by the factory MCU.
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_MEDIA_NEXT){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else return super.onKeyDown(keyCode,event);return true;}
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PREVIOUS){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else return super.onKeyDown(keyCode,event);return true;}
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||keyCode==KeyEvent.KEYCODE_HEADSETHOOK){if(localPlayer!=null){togglePlayback();return true;}}
        return super.onKeyDown(keyCode,event);
    }
    void chooseAppearance(){final String[] choices={"NEXUS Night • Calm Navy", "NEXUS Daylight • Pearl Titanium"};new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(choices,appearance,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){appearance=n;getPreferences(MODE_PRIVATE).edit().putInt("appearance",n).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    void chooseClockFormat(){final String[] choices={"24-hour", "12-hour AM/PM"};new AlertDialog.Builder(this).setTitle("Clock format").setSingleChoiceItems(choices,clock12?1:0,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){clock12=n==1;getPreferences(MODE_PRIVATE).edit().putBoolean("clock12",clock12).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    String clockText(){return new SimpleDateFormat(clock12?"hh:mm a":"HH:mm",Locale.ENGLISH).format(new Date());}
    void showZomaSettings(){
        final String[] items={"Appearance • Night / Daylight", "Accent colors", "Clock • 12 / 24 hour", "Analog clock • Show / hide", "USB music folder", "Music player selection", "App Center", "Maintenance Center", "Oil reminder • Show / hide", "Oil interval (km)", "OBD connection", "Android system settings", "Restore ZomaOS defaults", "USB startup playback", "External audio source"};
        final Dialog dialog=new Dialog(this);dialog.setTitle("ZomaOS V10 • Settings");
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(16,10,16,10);panel.setBackgroundColor(Color.rgb(16,39,59));
        TextView heading=new TextView(this);heading.setText("ZOMAOS V10   /   SETTINGS");heading.setTextSize(22);heading.setTextColor(Color.WHITE);heading.setPadding(12,8,8,14);panel.addView(heading);
        ListView list=new ListView(this);list.setCacheColorHint(Color.TRANSPARENT);list.setDividerHeight(1);list.setScrollingCacheEnabled(false);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,items){@Override public View getView(int pos,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(pos,convert,parent);v.setTextColor(Color.WHITE);v.setTextSize(17);v.setPadding(14,10,8,10);return v;}};
        list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(this);close.setText("BACK TO DASHBOARD");panel.addView(close);close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int n,long id){dialog.dismiss();switch(n){
            case 13:chooseResumeSetting();break;case 14:chooseMediaSource();break;
            case 0:chooseAppearance();break;case 1:chooseTheme();break;case 2:chooseClockFormat();break;
            case 3:showAnalog=!showAnalog;getPreferences(MODE_PRIVATE).edit().putBoolean("showAnalog",showAnalog).apply();dashboard.invalidate();break;
            case 4:chooseUsbRoot();break;case 5:chooseAnyMusicApp();break;case 6:showAppCenter();break;
            case 7:maintenanceCenter();break;case 8:toggleOilReminder();break;
            case 9:maintenanceInput("Oil change interval (km)","oil_interval",getPreferences(MODE_PRIVATE).getInt("oil_interval",7000));break;
            case 10:showMode();break;case 11:launch(new String[]{},"settings");break;
            case 12:new AlertDialog.Builder(MainActivity.this).setTitle("Restore defaults?").setPositiveButton("Reset",new DialogInterface.OnClickListener(){public void onClick(DialogInterface a,int b){getPreferences(MODE_PRIVATE).edit().clear().apply();theme=0;showAnalog=true;centerMode=0;appearance=0;clock12=false;dashboard.invalidate();}}).setNegativeButton("Cancel",null).show();break;
        }}});
        dialog.setContentView(panel);dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }
    void showAppCenter(){
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final ArrayList<ResolveInfo> apps=new ArrayList<ResolveInfo>(getPackageManager().queryIntentActivities(probe,0));
        Collections.sort(apps,new Comparator<ResolveInfo>(){public int compare(ResolveInfo a,ResolveInfo b){return String.valueOf(a.loadLabel(getPackageManager())).compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager())));}});
        final Dialog dialog=new Dialog(this);dialog.setTitle("ZomaOS V9 • Apps");
        GridView grid=new GridView(this);grid.setNumColumns(4);grid.setVerticalSpacing(12);grid.setHorizontalSpacing(8);grid.setPadding(12,14,12,14);grid.setBackgroundColor(Color.rgb(14,38,58));
        grid.setAdapter(new android.widget.BaseAdapter(){public int getCount(){return apps.size();}public Object getItem(int i){return apps.get(i);}public long getItemId(int i){return i;}
            public View getView(int i,View reuse,android.view.ViewGroup parent){LinearLayout cell=reuse instanceof LinearLayout?(LinearLayout)reuse:new LinearLayout(MainActivity.this);cell.removeAllViews();cell.setOrientation(1);cell.setGravity(Gravity.CENTER);cell.setPadding(4,10,4,10);
                ImageView icon=new ImageView(MainActivity.this);icon.setImageDrawable(apps.get(i).loadIcon(getPackageManager()));cell.addView(icon,new LinearLayout.LayoutParams(45,45));
                TextView name=new TextView(MainActivity.this);name.setText(String.valueOf(apps.get(i).loadLabel(getPackageManager())));name.setTextColor(Color.WHITE);name.setTextSize(12);name.setGravity(Gravity.CENTER);name.setMaxLines(2);cell.addView(name);return cell;}});
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int n,long id){try{ResolveInfo a=apps.get(n);Intent i=new Intent(Intent.ACTION_MAIN);i.setClassName(a.activityInfo.packageName,a.activityInfo.name);startActivity(i);dialog.dismiss();}catch(Exception e){toast("Unable to open app");}}});
        LinearLayout appPanel=new LinearLayout(this);appPanel.setOrientation(1);appPanel.setBackgroundColor(Color.rgb(14,38,58));
        appPanel.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        Button dashboardButton=new Button(this);dashboardButton.setText("BACK TO DASHBOARD");appPanel.addView(dashboardButton);
        dashboardButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        dialog.setContentView(appPanel);dialog.show();dialog.getWindow().setLayout(-1,-1);
    }
    void toggleOilReminder(){boolean v=!getPreferences(MODE_PRIVATE).getBoolean("oil_visible",true);getPreferences(MODE_PRIVATE).edit().putBoolean("oil_visible",v).apply();dashboard.invalidate();toast(v?"Oil reminder shown":"Oil reminder hidden; service data kept");}
    void oilReminderActions(){
        new AlertDialog.Builder(this).setTitle("Oil maintenance").setItems(new String[]{"Oil changed now (use current odometer)","Hide reminder (keep service data)","Maintenance details"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){
            if(n==0){final int odo=getPreferences(MODE_PRIVATE).getInt("odo",-1);if(odo<0){maintenanceInput("Current odometer (km)","odo",odo);return;}
                new AlertDialog.Builder(MainActivity.this).setTitle("Confirm oil change").setMessage("Record oil change at "+odo+" km? Only confirm after actually changing oil.").setPositiveButton("Confirm",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){getPreferences(MODE_PRIVATE).edit().putInt("oil_last",odo).putBoolean("oil_visible",true).apply();dashboard.invalidate();toast("Oil service recorded");}}).setNegativeButton("Cancel",null).show();
            }else if(n==1){getPreferences(MODE_PRIVATE).edit().putBoolean("oil_visible",false).apply();dashboard.invalidate();}else maintenanceCenter();
        }}).show();
    }
    void maintenanceCenter(){
        final android.content.SharedPreferences pref=getPreferences(MODE_PRIVATE);
        final int odo=pref.getInt("odo",-1),last=pref.getInt("oil_last",-1),interval=pref.getInt("oil_interval",7000);
        String msg="MANUAL DATA ONLY — no vehicle odometer connection\n\nCurrent odometer: "+(odo<0?"not entered":odo+" km")+"\nLast oil service: "+(last<0?"not entered":last+" km")+"\nOil interval: "+interval+" km";
        if(odo>=0&&last>=0)msg+="\nNext oil service: "+(last+interval)+" km\nRemaining: "+Math.max(0,last+interval-odo)+" km";
        new AlertDialog.Builder(this).setTitle("Maintenance Center").setMessage(msg).setPositiveButton("Update odometer",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){maintenanceInput("Current odometer (km)","odo",odo);}}).setNeutralButton("Oil service",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){maintenanceInput("Odometer at last oil change (km)","oil_last",last);}}).setNegativeButton("Close",null).show();
    }
    void maintenanceInput(String title,final String key,int old){final EditText input=new EditText(this);input.setInputType(2);if(old>=0)input.setText(String.valueOf(old));new AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Save",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{int val=Integer.parseInt(input.getText().toString().trim());if(val<0)throw new Exception();getPreferences(MODE_PRIVATE).edit().putInt(key,val).apply();maintenanceCenter();}catch(Exception e){toast("Enter a valid nonnegative kilometer reading");}}}).setNegativeButton("Cancel",null).show();}
    void seekMusic(float x){if(localPlayer==null)return;try{int duration=localPlayer.getDuration();if(duration>0){float f=Math.max(0,Math.min(1,(x-121f)/324f));localPlayer.seekTo((int)(duration*f));dashboard.invalidate();}}catch(Exception e){toast("Seeking unavailable");}}
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
        Dashboard(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
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
            String time=clockText();
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

        void drawDockLabel(int i,float cx,float y){
            String label=dockLabel(i);if(label.length()>16)label=label.substring(0,15)+"…";
            String pkg=getPreferences(MODE_PRIVATE).getString("dock_pkg_"+i,"");
            if(pkg.length()>0){try{
                android.graphics.drawable.Drawable icon=getPackageManager().getApplicationIcon(pkg);
                int left=(int)cx-13;icon.setBounds(left,353,left+26,379);icon.draw(c);
                centerText(label,cx,396,10,Color.WHITE,true);return;
            }catch(Exception ignored){}}
            centerText(label,cx,y,12,Color.WHITE,true);
        }
        void drawDaylight(){
            // Independent daytime composition: ivory, slate, teal and real SUV photography.
            gradient(0,0,800,480,col("#17354C"),col("#416B80"));
            if(daySuv!=null){fill(Color.WHITE);c.drawBitmap(daySuv,null,new RectF(360,76,799,346),p);}
            box(0,0,98,480,col("#173A45"));
            box(100,0,800,68,col("#183A51"));
            text("ZOMAOS  /  NEXUS V9",119,31,23,Color.WHITE,true);
            text("DAYLIGHT COCKPIT",120,52,12,col("#58D9E8"),true);
            String[] nav={"HOME","MUSIC","RADIO","AUTO","OBD","APPS","SET"};
            for(int i=0;i<nav.length;i++){
                if(i==0)box(5,75,93,122,col("#087F89"));
                text(nav[i],14,108+i*43,12,Color.WHITE,true);
            }
            box(105,74,377,165,col("#244A60"));
            box(103,192,468,338,col("#18384D"));
            box(474,195,624,300,col("#18384D"));
            box(631,192,791,341,col("#1B3B50"));
            box(98,347,800,410,col("#17394C"));
            String[] actions={"USB FILES","FM RADIO","ANDROID AUTO","OBD","APPS"};
            int[] edges={103,239,372,509,640,793};
            for(int i=0;i<5;i++){
                box(edges[i],353,edges[i+1]-6,401,col("#29536A"));
                drawDockLabel(i,(edges[i]+edges[i+1]-6)/2,382);
            }
        }
        void v10Clock(float cx,float cy,boolean day){
            int teal=col("#17BFC9"), ink=day?col("#1B3949"):col("#E5F6FC");
            fill(day?Color.argb(235,239,248,250):Color.argb(235,9,29,48));c.drawCircle(cx,cy,66,p);
            fill(day?col("#F8FCFE"):col("#102B41"));c.drawCircle(cx,cy,60,p);
            for(int i=0;i<12;i++){double a=i*Math.PI/6-Math.PI/2;float in=i%3==0?44:49;
                line(cx+(float)Math.cos(a)*in,cy+(float)Math.sin(a)*in,cx+(float)Math.cos(a)*54,cy+(float)Math.sin(a)*54,teal,i%3==0?2:1);}
            Calendar cal=Calendar.getInstance();float seconds=cal.get(Calendar.SECOND),mins=cal.get(Calendar.MINUTE)+seconds/60f,hours=cal.get(Calendar.HOUR)+mins/60f;
            hand(cx,cy,(float)(hours*Math.PI/6-Math.PI/2),27,4,ink);
            hand(cx,cy,(float)(mins*Math.PI/30-Math.PI/2),40,3,teal);
            hand(cx,cy,(float)(seconds*Math.PI/30-Math.PI/2),44,1,col("#FF996C"));
            fill(teal);c.drawCircle(cx,cy,3,p);
            centerText("ZomaOS",cx,cy-20,9,ink,true);
        }
        void v10Button(float l,float t,float r,float b,String label,boolean day,boolean selected){
            box(l,t,r,b,selected?(day?col("#D1F2F4"):col("#0D566C")):(day?Color.argb(240,239,246,250):Color.argb(230,25,47,68)));
            if(selected)line(l+10,b-3,r-10,b-3,col("#18C5D4"),2);
            centerText(label,(l+r)/2,(t+b)/2+5,12,day?col("#1D3546"):Color.WHITE,true);
        }
        void drawV10(){
            boolean day=appearance==1;
            int ink=day?col("#173448"):col("#EAF7FC"),muted=day?col("#496879"):col("#A8C8D7"),teal=col("#13BAC8");
            gradient(0,0,800,480,day?col("#D8EAF1"):col("#071625"),day?col("#A9C4D1"):col("#102B40"));
            // Photographic car backdrop, with real Canvas text, controls, clock and media.
            if(daySuv!=null){fill(Color.WHITE);c.drawBitmap(daySuv,null,new RectF(306,58,800,349),p);
                if(!day){fill(Color.argb(120,2,17,37));c.drawRect(306,58,800,349,p);}}
            gradient(310,58,490,349,day?col("#DCEBF1"):col("#102639"),Color.TRANSPARENT);
            box(0,0,800,56,day?col("#19384B"):col("#0A1B2D"));
            text("Z",20,37,35,teal,true);text("ZomaOS",67,31,22,Color.WHITE,true);
            text("NEXUS V10",179,31,18,col("#65DBE6"),true);
            text("USB   BT   WiFi",592,31,12,col("#D2EDF3"),false);
            text(clockText(),702,34,25,Color.WHITE,true);
            box(0,57,93,416,day?col("#18384B"):col("#0A1C2E"));
            String[] nav={"HOME","MUSIC","RADIO","AUTO","OBD","APPS","SET"};
            for(int i=0;i<nav.length;i++){float y=64+i*48;
                if(i==0)box(6,y,87,y+42,col("#087F94"));
                centerText(nav[i],46,y+27,12,Color.WHITE,true);
            }
            box(104,65,360,158,day?Color.argb(231,241,248,251):Color.argb(220,10,31,48));
            text(clockText(),124,122,47,ink,true);
            text(new SimpleDateFormat("EEE, MMM d, yyyy",Locale.ENGLISH).format(new Date()),128,145,12,muted,false);
            // Transparent glass media card stays separate from car hero.
            box(106,190,465,339,day?Color.argb(239,229,240,247):Color.argb(234,9,30,48));
            line(117,195,454,195,teal,2);
            String source=usbMode&&localPlayer!=null?"USB MUSIC":localMode&&localPlayer!=null?"LOCAL MUSIC":externalSource.length()>0?externalSource:"MEDIA";
            text(source,125,219,15,teal,true);
            String title=(usbMode||localMode)&&trackTitle.length()>0?trackTitle:externalSource.length()>0?"Factory source selected":"Select your music source";
            if(title.length()>36)title=title.substring(0,33)+"...";
            text(title,125,248,18,ink,true);
            String subtitle=(usbMode||localMode)?trackArtist:externalSource.length()>0?"Status unverified • tap source":"USB • FM Radio • Bluetooth";
            if(subtitle.length()>45)subtitle=subtitle.substring(0,42)+"...";
            text(subtitle,125,270,12,muted,false);
            if(albumArt!=null){fill(Color.WHITE);c.drawBitmap(albumArt,null,new RectF(406,204,453,252),p);}
            if(localPlayer!=null)try{int duration=localPlayer.getDuration();int pos=localPlayer.getCurrentPosition();
                if(duration>0){box(121,280,445,284,day?col("#AEC6D2"):col("#375568"));
                    box(121,280,121+324f*pos/duration,284,teal);}
            }catch(Exception ignored){}
            centerText("|<",187,321,22,ink,true);
            centerText(localPlayer!=null&&localPlayer.isPlaying()?"II":">",296,321,23,teal,true);
            centerText(">|",406,321,22,ink,true);
            box(483,211,627,289,day?Color.argb(232,235,246,249):Color.argb(232,10,31,49));
            text("MEDIA SOURCE",494,232,12,muted,true);
            text(source,494,253,15,ink,true);
            text("TAP TO SELECT",494,274,11,teal,true);
            if(showAnalog)v10Clock(707,264,day);
            for(int i=0;i<5;i++)v10Button(dockEdges[i],353,dockEdges[i+1]-6,401,dockLabel(i),day,i==1&&"FM RADIO".equals(externalSource));
            box(0,418,800,480,day?col("#D9EAF0"):col("#081929"));
            line(0,418,800,418,teal,1);
            centerText("ZOMAOS NEXUS V10   •   DRIVE SAFE",400,444,12,ink,true);
            centerText("MEDIA: "+(externalSource.length()>0?externalSource+" (SELECTED)":usbMode?"USB":localMode?"LOCAL":"IDLE")+"     |     OBD: "+status,400,465,10,muted,false);
            int odo=getPreferences(MODE_PRIVATE).getInt("odo",-1),last=getPreferences(MODE_PRIVATE).getInt("oil_last",-1),interval=getPreferences(MODE_PRIVATE).getInt("oil_interval",7000);
            if(getPreferences(MODE_PRIVATE).getBoolean("oil_visible",true)&&odo>=0&&last>=0&&odo>=last+interval-500){
                box(505,393,791,414,col("#8B4D27"));centerText(odo>=last+interval?"OIL DUE • TAP":"OIL IN "+Math.max(0,last+interval-odo)+" KM • TAP",647,408,11,Color.WHITE,true);
            }
        }
        @Override protected void onDraw(Canvas canvas){
            c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);drawV10();c.restore();
            if(usbMode&&localPlayer!=null&&System.currentTimeMillis()-lastResumeSave>5000){lastResumeSave=System.currentTimeMillis();saveUsbResume();}
            postInvalidateDelayed(1000);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();
            if(e.getAction()==MotionEvent.ACTION_DOWN&&y>=349&&y<=409&&x>=100){
                pressedDock=dockIndex(x);dockLongPressed=false;
                final int chosen=pressedDock;
                pendingDockLongPress=new Runnable(){public void run(){if(pressedDock==chosen&&chosen>=0){dockLongPressed=true;editDock(chosen);}}};
                dockHandler.postDelayed(pendingDockLongPress,650);return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL){if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);pressedDock=-1;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE&&pressedDock>=0){
                if(y<337||y>417||dockIndex(x)!=pressedDock){if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);pressedDock=-1;}
                return true;
            }
            if((e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE)&&y>=273&&y<=303&&x>=115&&x<=450){draggingSeek=true;return true;}
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            if(pendingDockLongPress!=null){dockHandler.removeCallbacks(pendingDockLongPress);pendingDockLongPress=null;}
            if(pressedDock>=0){int index=pressedDock;pressedDock=-1;if(!dockLongPressed&&dockIndex(x)==index)activateDock(index);return true;}
            if(draggingSeek){draggingSeek=false;seekMusic(x);return true;}
            if(y>=386&&y<=415&&x>=495){oilReminderActions();return true;}
            if(y>=349&&y<=409&&x>=100){int index=dockIndex(x);if(index>=0)activateDock(index);return true;}
            if(y>=290&&y<340&&x>=135&&x<465){if(x<242){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else openRememberedUsb();}else if(x<350)togglePlayback();else{if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else openRememberedUsb();}return true;}
            if(x>=475&&x<=625&&y>=195&&y<=310){chooseMediaSource();return true;}
            if(y>=418&&x>500){showZomaSettings();return true;}
            if(x<94){if(y<113)return true;if(y<155){musicHub();return true;}if(y<194){musicHub();return true;}if(y<234){openRadio();return true;}if(y<275){launch(new String[]{"me.ri3d.openauto"},"");return true;}if(y<317){showMode();return true;}if(y<362){showAppCenter();return true;}showZomaSettings();return true;}
            if(y>=190&&y<=290&&x>=100&&x<=465){if(usbMode)showUsbTracks();else openRememberedUsb();return true;}
            if(y<70&&x>630){showZomaSettings();return true;}
            return true;
        }

    }
}
