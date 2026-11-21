package com.zoma.os;

import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.content.*;
import android.graphics.*;
import android.view.KeyEvent;
import android.text.TextUtils;
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
    Bitmap carbonGaugePanel, carbonMusicPanel, carbonDockPanel, carbonSidePanel, carbonAnalogDial; Bitmap masterArtwork; Bitmap v147CarScene;
    Bitmap[] premiumIcons=new Bitmap[9];
    Bitmap[] approvedThemes=new Bitmap[4]; Bitmap n14Scene,n14SceneDay,n14SceneTwilight,n14SceneCarbon; Bitmap n14Header,n14Nav,n14Selected,n14Time,n14Music,n14Source,n14Dock,n14Clock; boolean carbonClock=true; Bitmap[] cinematicThemes=new Bitmap[4]; Bitmap exactElectric; Bitmap exactPearl; Bitmap exactTwilight; Bitmap nexusRoad; Bitmap signatureClock; Bitmap signatureDigital; Bitmap daySuv; Bitmap v11Night; Bitmap v11Day;
    String trackTitle="", trackArtist="", metadataSource="";
    long metadataTime=0;
    BroadcastReceiver musicInfoReceiver;
    MediaPlayer localPlayer;
    ArrayList<String> localTitles=new ArrayList<String>();
    ArrayList<String> localArtists=new ArrayList<String>();
    ArrayList<Long> localIds=new ArrayList<Long>();
    ArrayList<File> usbTracks=new ArrayList<File>();
    int usbIndex=-1; boolean usbMode=false;
    boolean shuffleMode=false; int repeatMode=0; // 0 off, 1 all, 2 one
    final Random shuffleRandom=new Random();
    void cycleRepeat(){repeatMode=(repeatMode+1)%3;getPreferences(MODE_PRIVATE).edit().putInt("repeatMode",repeatMode).apply();dashboard.invalidate();}
    void toggleShuffle(){shuffleMode=!shuffleMode;getPreferences(MODE_PRIVATE).edit().putBoolean("shuffleMode",shuffleMode).apply();dashboard.invalidate();}
    int nextTrackIndex(int current,int size,int direction){
        if(size<=1)return 0;
        if(shuffleMode&&direction>0){int pick=shuffleRandom.nextInt(size-1);return pick>=current?pick+1:pick;}
        return (current+(direction>0?1:-1)+size)%size;
    }
    void onUsbTrackComplete(){
        if(repeatMode==2&&localPlayer!=null){try{localPlayer.seekTo(0);localPlayer.start();return;}catch(Exception ignored){}}
        if(repeatMode==0&&usbIndex==usbTracks.size()-1&&!shuffleMode){pauseAudio();return;}
        nextUsb(1);
    }
    void onLocalTrackComplete(){
        if(repeatMode==2&&localPlayer!=null){try{localPlayer.seekTo(0);localPlayer.start();return;}catch(Exception ignored){}}
        if(repeatMode==0&&localIndex==localIds.size()-1&&!shuffleMode){pauseAudio();return;}
        nextLocal(1);
    }
    boolean resumeAttempted=false;
    final Handler resumeHandler=new Handler();
    final Runnable resumeTick=new Runnable(){public void run(){saveUsbPlaybackState();resumeHandler.postDelayed(this,5000);}};
    Bitmap concept; Bitmap dayConcept; Bitmap albumArt; boolean draggingSeek=false; boolean showAnalog=true; int selectedPage=0;
    AudioManager audioManager; boolean hasAudioFocus=false; boolean clock12=false; int appearance=0;
    AudioManager.OnAudioFocusChangeListener focusListener=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){pauseAudio();}}};
    // User-configurable dock. Empty preference means the original built-in action.
    final String[] dockDefaults={"USB MUSIC","RADIO FM","ANDROID AUTO","APPS","FILE BROWSER"};
    final int[] dockEdges={70,214,356,498,640,787};
    boolean dockLongPressed=false;
    final Handler dockHandler=new Handler();
    Runnable pendingDockLongPress;
    int pressedDock=-1;
    boolean touchStarted=false;
    float downX=0f,downY=0f;
    // Small finger movement is normal on resistive/older car touchscreens.
    static final float DOCK_LONG_PRESS_SLOP=18f;
    static final float DOCK_RELEASE_SLOP=32f;
    String dockLabel(int index){
        String pkg=getPreferences(MODE_PRIVATE).getString("dock_pkg_"+index,"");
        if(pkg.length()==0)return dockDefaults[index];
        try{return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)));}
        catch(Exception e){return dockDefaults[index];}
    }
    void activateDock(int index){
        String pkg=getPreferences(MODE_PRIVATE).getString("dock_pkg_"+index,"");
        if(index==2){openAndroidAuto();return;}
        if(pkg.length()>0){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent!=null){stopAudio();startActivity(intent);return;}
            toast("App unavailable. Restore shortcut in long press.");return;
        }
        if(index==0)openRememberedUsb();
        else if(index==1)openRadio();
        else if(index==2)openAndroidAuto();
        else if(index==3)showAppCenter();
        else if(index==4)openFileBrowser();
        
    }
    // Single Android Auto entry point for dock and entertainment hub.
    void openAndroidAuto(){
        String[] packages={"gb.xxy.hr","com.google.android.projection.gearhead","me.ri3d.openauto"};
        String saved=getPreferences(MODE_PRIVATE).getString("androidAutoComponent","");
        if(saved.length()>0){try{Intent i=new Intent(Intent.ACTION_MAIN);i.setComponent(ComponentName.unflattenFromString(saved));startActivity(i);return;}catch(Throwable e){android.util.Log.w("ZomaOS","Saved Android Auto shortcut unavailable",e);}}
        for(String pkg:packages){
            try{Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
                if(intent!=null){stopAudio();startActivity(intent);return;}}
            catch(Throwable err){android.util.Log.w("ZomaOS","Auto launch failed: "+pkg,err);}
        }
        // OEM and Headunit Reloaded package IDs vary; let the user choose the working app.
        chooseFactoryApp("androidAutoComponent","Android Auto / Headunit Reloaded");
    }
    void openFileBrowser(){
        launch(new String[]{"com.estrongs.android.pop","com.android.documentsui","com.speedsoftware.rootexplorer","com.ghisler.android.TotalCommander"},"");
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
    int dockIndex(float x){for(int i=0;i<dockDefaults.length;i++)if(x>=dockEdges[i]&&x<dockEdges[i+1])return i;return -1;}
    int masterDockIndex(float x){return dockIndex(x);}
    int localIndex=-1;
    boolean localMode=false;


    int centerMode=0; // 0 road, 1 analog, 2 digital
    int theme=0; // 0 cyan, 1 amber, 2 emerald
    final Handler ui=new Handler();
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Make the activity occupy the entire head-unit display on Android 4.1+.
    // The dashboard continues to use the same 800x480 touch coordinate system.
    void applyEdgeToEdge(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor=getWindow().getDecorView();
        // Android 4.1-4.3: HIDE_NAVIGATION is cleared by the FIRST touch.
        // That touch can be consumed by System UI instead of reaching our buttons.
        // Only hide navigation on API 19+ where IMMERSIVE_STICKY is supported.
        int flags=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  |View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if(Build.VERSION.SDK_INT>=19){
            flags|=View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|4096;
        }
        decor.setSystemUiVisibility(flags);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)applyEdgeToEdge();
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);applyEdgeToEdge();centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);appearance=getPreferences(MODE_PRIVATE).getInt("appearance",0);shuffleMode=getPreferences(MODE_PRIVATE).getBoolean("shuffleMode",false);repeatMode=getPreferences(MODE_PRIVATE).getInt("repeatMode",0);if(appearance<0||appearance>2)appearance=0;carbonClock=getPreferences(MODE_PRIVATE).getBoolean("carbonClock",true);loadN14Skin();showAnalog=getPreferences(MODE_PRIVATE).getBoolean("showAnalog",true);clock12=getPreferences(MODE_PRIVATE).getBoolean("clock12",false);audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);dashboard=new Dashboard();setContentView(dashboard);dashboard.setClickable(true);startMusicDiscovery();try{restoreUsbFolder();}catch(Throwable err){android.util.Log.e("ZomaOS","USB restore failed during launcher startup",err);}resumeHandler.postDelayed(resumeTick,5000);resumeLastUsbOnce();}
    // Load only the selected scene and eight small themed assets. No GIF or frame loop.
    void recycleN14(Bitmap b){if(b!=null&&!b.isRecycled())b.recycle();}
    void loadN14Skin(){
        // V15.3 renders the selected 800x480 master only. Legacy V14 scene assets are not loaded.
        recycleN14(masterArtwork);
        int masterId=appearance==2?R.drawable.master_carbon:appearance==1?R.drawable.master_pearl:R.drawable.master_electric;
        masterArtwork=BitmapFactory.decodeResource(getResources(),masterId);
    }
    void chooseCarbonGauge(){
        new AlertDialog.Builder(this).setTitle("Carbon Sport gauge")
          .setSingleChoiceItems(new String[]{"Analog clock (recommended)","Speedometer (requires live OBD)"},carbonClock?0:1,
            new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int choice){
                carbonClock=choice==0;
                getPreferences(MODE_PRIVATE).edit().putBoolean("carbonClock",carbonClock).apply();
                dashboard.invalidate();d.dismiss();
            }}).setNegativeButton("Cancel",null).show();
    }
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
    @Override protected void onPause(){saveUsbPlaybackState();super.onPause();pauseAudio();}
    @Override protected void onDestroy(){
        if(musicInfoReceiver!=null){try{unregisterReceiver(musicInfoReceiver);}catch(Exception ignored){}}
        resumeHandler.removeCallbacks(resumeTick);stop();releaseLocalPlayer();super.onDestroy();
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
    // Factory Bluetooth/phone integration: choose the OEM app once, then reopen it.
    // AVRCP metadata is owned by the factory Bluetooth stack and is NOT assumed public.
    void openFactoryBluetooth(){openFactoryApp("factoryBluetoothComponent", "Bluetooth Music / Phone");}
    void openFactoryPhone(){openFactoryApp("factoryPhoneComponent", "Factory Phone / Dial");}
    void openFactoryApp(String preferenceKey,String title){
        String saved=getPreferences(MODE_PRIVATE).getString(preferenceKey, "");
        if(saved.length()>0){
            try{
                Intent i=Intent.makeMainActivity(ComponentName.unflattenFromString(saved));
                startActivity(i);return;
            }catch(Exception ignored){getPreferences(MODE_PRIVATE).edit().remove(preferenceKey).apply();}
        }
        chooseFactoryApp(preferenceKey,title);
    }
    void chooseFactoryApp(final String preferenceKey,String title){
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final ArrayList<ResolveInfo> all=new ArrayList<ResolveInfo>(getPackageManager().queryIntentActivities(probe,0));
        Collections.sort(all,new Comparator<ResolveInfo>(){public int compare(ResolveInfo a,ResolveInfo b){
            return String.valueOf(a.loadLabel(getPackageManager())).compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager())));
        }});
        if(all.isEmpty()){toast("No factory app found");return;}
        final String[] names=new String[all.size()];
        for(int n=0;n<all.size();n++)names[n]=String.valueOf(all.get(n).loadLabel(getPackageManager()))+" ["+all.get(n).activityInfo.packageName+"]";
        new AlertDialog.Builder(this).setTitle("Select " + title + " (one time)")
          .setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){
              ResolveInfo app=all.get(n);
              ComponentName component=new ComponentName(app.activityInfo.packageName,app.activityInfo.name);
              getPreferences(MODE_PRIVATE).edit().putString(preferenceKey,component.flattenToString()).apply();
              openFactoryApp(preferenceKey,"Factory app");
          }}).setNegativeButton("Cancel",null).show();
    }
    void factoryBluetoothOptions(){
        new AlertDialog.Builder(this).setTitle("Factory Bluetooth")
          .setItems(new String[]{"Open Bluetooth Music","Open Phone / Dial","Change Bluetooth app","Change Phone app"},
          new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
              if(which==0)openFactoryBluetooth();
              else if(which==1)openFactoryPhone();
              else if(which==2)chooseFactoryApp("factoryBluetoothComponent","Bluetooth Music");
              else chooseFactoryApp("factoryPhoneComponent","Factory Phone");
          }}).setNegativeButton("Cancel",null).show();
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
    // V12: Factory tuner stays authoritative. No guessed frequency or unsupported MCU commands.
    void openSystemSettings(String action){
        try{startActivity(new Intent(action));}
        catch(Exception e){try{startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}
        catch(Exception ignored){toast("System settings unavailable");}}
    }
    void openRadio(){
        try {
            Intent i=getPackageManager().getLaunchIntentForPackage("com.spd.radio");
            if(i==null){i=new Intent();i.setClassName("com.spd.radio","com.spd.radio.MainActivity");}
            // Only change audio source after a launch request succeeds.
            startActivity(i);
            saveUsbPlaybackState();
            getPreferences(MODE_PRIVATE).edit().putString("last_audio_source","fm").apply();
            stopAudio();
        } catch(Throwable e){
            android.util.Log.e("ZomaOS","Factory radio launch failed",e);
            toast("Factory FM Radio unavailable - check installed Radio app");
        }
    }
    // The stock radio's frequency and preset commands are not exposed by this source.
    // Avoid fake station display or broadcasting undocumented hardware commands.
    boolean requestMusicFocus(){
        if(audioManager==null)return true;
        int result=audioManager.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        hasAudioFocus=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!hasAudioFocus)toast("Audio output busy — stop other source first");
        return hasAudioFocus;
    }
    void pauseAudio(){try{if(localPlayer!=null&&localPlayer.isPlaying())localPlayer.pause();}catch(Exception ignored){}if(dashboard!=null)dashboard.invalidate();}
    void stopAudio(){saveUsbPlaybackState();releaseLocalPlayer();usbMode=false;localMode=false;trackTitle="";trackArtist="";if(audioManager!=null&&hasAudioFocus){audioManager.abandonAudioFocus(focusListener);hasAudioFocus=false;}if(dashboard!=null)dashboard.invalidate();}
    void releaseLocalPlayer(){
        try{if(localPlayer!=null){localPlayer.reset();localPlayer.release();}}catch(Throwable ignored){}
        localPlayer=null;albumArt=null;
    }
    boolean audioFile(File f){String n=f.getName().toLowerCase(Locale.US);return n.endsWith(".mp3")||n.endsWith(".wav")||n.endsWith(".ogg")||n.endsWith(".m4a")||n.endsWith(".aac")||n.endsWith(".flac");}
    void collectMusic(File folder,int depth,int limit){
        if(depth>9||usbTracks.size()>=limit||folder==null||!folder.canRead())return;
        File[] files;try{files=folder.listFiles();}catch(Throwable err){android.util.Log.w("ZomaOS","Unreadable USB folder: "+folder,err);return;}if(files==null)return;
        Arrays.sort(files,new Comparator<File>(){public int compare(File a,File b){return a.getName().compareToIgnoreCase(b.getName());}});
        for(File f:files){if(usbTracks.size()>=limit)break;if(f.isDirectory())collectMusic(f,depth+1,limit);else if(audioFile(f))usbTracks.add(f);}
    }
    void playUsb(int index){
        if(index<0||index>=usbTracks.size())return;
        saveUsbPlaybackState();
        releaseLocalPlayer();
        if(!requestMusicFocus())return;
        try{File f=usbTracks.get(index);albumArt=null;localPlayer=new MediaPlayer();localPlayer.setDataSource(f.getAbsolutePath());
            localPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);localPlayer.prepare();localPlayer.start();
            usbIndex=index;usbMode=true;localMode=false;
            getPreferences(MODE_PRIVATE).edit().putString("last_audio_source","usb").putString("usb_last_file",f.getAbsolutePath()).putInt("usb_last_position",0).putBoolean("usb_was_playing",true).apply();
            trackTitle=f.getName().replaceFirst("(?i)\\.(mp3|m4a|wav|ogg|flac)$", "").replace('_',' ');trackArtist="USB Music";metadataSource="Zoma USB player";
            try{MediaMetadataRetriever m=new MediaMetadataRetriever();m.setDataSource(f.getAbsolutePath());String t=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);String a=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);if(t!=null&&t.length()>0)trackTitle=t;if(a!=null&&a.length()>0)trackArtist=a;byte[] art=m.getEmbeddedPicture();if(art!=null)albumArt=BitmapFactory.decodeByteArray(art,0,art.length);m.release();}catch(Exception ignored){}
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){onUsbTrackComplete();}});dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play this USB audio file");}
    }
    void nextUsb(int direction){
        if(usbTracks.isEmpty())return;
        // Dedicated track navigation: never alter the playback position within the same track.
        int base=usbIndex<0?(direction>0?-1:0):usbIndex;
        int target=nextTrackIndex(base,usbTracks.size(),direction);
        playUsb(target);
    }
    void resumeLastUsb(){
        try{
            String last=getPreferences(MODE_PRIVATE).getString("usb_last_file","");
            if(last.length()==0){openRememberedUsb();return;}
            File track=new File(last);
            if(!track.isFile()||!track.canRead()){toast("USB unavailable - choose folder");openRememberedUsb();return;}
            if(usbTracks.isEmpty())restoreUsbFolder();
            int found=-1;for(int i=0;i<usbTracks.size();i++)if(last.equals(usbTracks.get(i).getAbsolutePath())){found=i;break;}
            if(found<0){usbTracks.add(track);found=usbTracks.size()-1;}
            int position=getPreferences(MODE_PRIVATE).getInt("usb_last_position",0);
            playUsb(found);
            if(localPlayer!=null&&position>0)localPlayer.seekTo(Math.min(position,Math.max(0,localPlayer.getDuration()-500)));
        }catch(Throwable err){android.util.Log.e("ZomaOS","USB resume failed",err);toast("Unable to resume USB audio");}
    }
    void togglePlayback(){if(localPlayer==null){resumeLastUsb();return;}try{
        if(localPlayer.isPlaying()){saveUsbPlaybackState();localPlayer.pause();if(usbMode)getPreferences(MODE_PRIVATE).edit().putBoolean("usb_was_playing",false).apply();}
        else if(requestMusicFocus()){localPlayer.start();if(usbMode)getPreferences(MODE_PRIVATE).edit().putBoolean("usb_was_playing",true).apply();}
        dashboard.invalidate();}catch(Exception e){toast("Player error");}}
    void showUsbTracks(){
        if(usbTracks.isEmpty()){toast("No supported audio found. Choose a different folder.");return;}
        final String[] labels=new String[usbTracks.size()];for(int i=0;i<labels.length;i++)labels[i]=usbTracks.get(i).getName();
        new AlertDialog.Builder(this).setTitle("USB MUSIC  •  "+labels.length+" files").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){playUsb(i);}})
            .setNeutralButton("Choose folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseUsbRoot();}}).setNegativeButton("Close",null).show();
    }
    // Persist position periodically, and immediately on pause/source switch.
    void saveUsbPlaybackState(){
        if(!usbMode||localPlayer==null||usbIndex<0||usbIndex>=usbTracks.size())return;
        try{getPreferences(MODE_PRIVATE).edit()
            .putString("usb_last_file",usbTracks.get(usbIndex).getAbsolutePath())
            .putInt("usb_last_position",Math.max(0,localPlayer.getCurrentPosition()))
            .putBoolean("usb_was_playing",localPlayer.isPlaying()).apply();}
        catch(Exception ignored){}
    }
    // One startup attempt, delayed to allow USB storage to mount. Never override last FM source.
    void resumeLastUsbOnce(){
        if(resumeAttempted)return;
        resumeAttempted=true;
        final android.content.SharedPreferences pref=getPreferences(MODE_PRIVATE);
        if(!"usb".equals(pref.getString("last_audio_source",""))||!pref.getBoolean("usb_was_playing",false))return;
        final String path=pref.getString("usb_last_file","");
        if(path.length()==0)return;
        final int position=Math.max(0,pref.getInt("usb_last_position",0));
        resumeHandler.postDelayed(new Runnable(){int attempts=0;public void run(){
            // User selected another source while waiting: do not steal audio focus.
            if(!"usb".equals(pref.getString("last_audio_source","")))return;
            File f=new File(path);
            if(!f.isFile()||!f.canRead()){
                if(++attempts<6)resumeHandler.postDelayed(this,2000);
                return;
            }
            File parent=f.getParentFile();if(parent==null)return;
            rememberUsbFolder(parent);
            usbTracks.clear();collectMusic(parent,0,2000);
            int index=usbTracks.indexOf(f);
            if(index<0)return;
            playUsb(index);
            if(localPlayer!=null){try{localPlayer.seekTo(Math.min(position,Math.max(0,localPlayer.getDuration()-1000)));}catch(Exception ignored){}}
        }},2500);
    }
    // Remember the chosen music directory across launcher restarts.
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
        // Always offer a folder picker: a previously remembered folder may be stale.
        try{restoreUsbFolder();if(!usbTracks.isEmpty())showUsbTracks();else chooseUsbRoot();}
        catch(Throwable err){android.util.Log.e("ZomaOS","USB browser error",err);toast("USB browser unavailable; select a folder");try{chooseUsbRoot();}catch(Throwable ignored){}}

    }
    // Detect actual mounted USB folders rather than showing Android's generic roots.
    // Some head units retain stale USBA mount directories after removal.
    boolean hasUsbAudio(File dir,int depth){
        if(depth<0||dir==null||!dir.isDirectory()||!dir.canRead())return false;
        File[] files;try{files=dir.listFiles();}catch(Throwable t){return false;}
        if(files==null)return false;
        for(File f:files){if(f.isFile()&&audioFile(f))return true;}
        if(depth>0)for(File f:files){if(f.isDirectory()&&hasUsbAudio(f,depth-1))return true;}
        return false;
    }
    void chooseUsbRoot(){
        LinkedHashMap<String,File> candidates=new LinkedHashMap<String,File>();
        String[] bases={"/mnt/usb","/mnt/usb_storage","/mnt/udisk","/mnt/usbhost1","/mnt/usbhost2","/storage","/mnt/media_rw"};
        for(String base:bases){
            File root=new File(base);
            if(!root.isDirectory()||!root.canRead())continue;
            File[] children=root.listFiles();
            if(children!=null)for(File child:children){
                if(child.isDirectory()&&child.canRead()&&
                   (child.getName().startsWith("USBA")||child.getName().startsWith("usb")||
                    child.getName().startsWith("sd")||child.getName().matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))&&
                    hasUsbAudio(child,3)){
                    try{candidates.put(child.getCanonicalPath(),child);}catch(IOException ignored){}
                }
            }
            if(hasUsbAudio(root,1)){
                try{candidates.put(root.getCanonicalPath(),root);}catch(IOException ignored){}
            }
        }
        // Prefer the deepest mount, not its parent directory.
        ArrayList<File> mounts=new ArrayList<File>(candidates.values());
        ArrayList<File> filtered=new ArrayList<File>();
        for(File f:mounts){
            boolean parent=false;
            for(File other:mounts)if(other!=f&&other.getAbsolutePath().startsWith(f.getAbsolutePath()+"/")){parent=true;break;}
            if(!parent)filtered.add(f);
        }
        if(filtered.size()==1){browseFolder(filtered.get(0));return;}
        if(filtered.size()>1){
            final File[] choices=filtered.toArray(new File[filtered.size()]);
            String[] labels=new String[choices.length];
            for(int i=0;i<labels.length;i++)labels[i]=choices[i].getName();
            new AlertDialog.Builder(this).setTitle("Choose USB drive")
                .setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){browseFolder(choices[n]);}})
                .setNegativeButton("Cancel",null).show();
            return;
        }
        // Fallback allows manual browsing when a USB contains no recognized audio yet.
        final String[] roots={"/mnt/usb","/mnt/usb_storage","/storage","/mnt/sdcard"};
        final ArrayList<File> available=new ArrayList<File>();
        for(String root:roots){File f=new File(root);if(f.isDirectory()&&f.canRead())available.add(f);}
        if(available.isEmpty()){toast("No readable USB storage found");return;}
        final String[] labels=new String[available.size()];
        for(int i=0;i<labels.length;i++)labels[i]=available.get(i).getAbsolutePath();
        new AlertDialog.Builder(this).setTitle("USB not detected - browse manually")
            .setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){browseFolder(available.get(n));}})
            .setNegativeButton("Cancel",null).show();
    }
    void browseFolder(final File folder){
        File[] entries;
        try{entries=folder.listFiles();}catch(Throwable err){android.util.Log.e("ZomaOS","Cannot list USB folder",err);toast("Cannot access USB folder");return;}
        if(entries==null){toast("Cannot access folder");return;}
        final ArrayList<File> children=new ArrayList<File>();for(File f:entries){try{if(f.isDirectory()||audioFile(f))children.add(f);}catch(SecurityException ignored){}}
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
        try{dialog.show();dialog.setContentView(panel);if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);}
        catch(Throwable err){android.util.Log.e("ZomaOS","USB file picker failed",err);toast("Unable to display USB files");}
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
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){onLocalTrackComplete();}});
            try{MediaMetadataRetriever mr=new MediaMetadataRetriever();mr.setDataSource(this,uri);byte[] art=mr.getEmbeddedPicture();if(art!=null)albumArt=BitmapFactory.decodeByteArray(art,0,art.length);mr.release();}catch(Exception ignored){}dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play selected audio file");}
    }
    void nextLocal(int step){if(localIds.size()>0)playLocal(nextTrackIndex(localIndex,localIds.size(),step));}
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
        final String[] choices={"USB MUSIC - browse flash drive", "ZOMA MUSIC - indexed library", "BLUETOOTH MUSIC - factory player", "GEELY MUSIC - original player", "FM RADIO - original tuner", "ANDROID AUTO - OpenAuto", "BLUETOOTH / PHONE settings"};
        new AlertDialog.Builder(this).setTitle("ZomaOS Entertainment Hub").setItems(choices,new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int n){if(n==0)openRememberedUsb();else if(n==1)openLocalMusic();else if(n==2)openFactoryBluetooth();else if(n==3)openMusic();else if(n==4)openRadio();else if(n==5)openAndroidAuto();else factoryBluetoothOptions();}
        }).setNegativeButton("Close",null).show();
    }
    // Best-effort media keys. Steering wheel may be routed privately by the factory MCU.
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_MEDIA_NEXT){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else return super.onKeyDown(keyCode,event);return true;}
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PREVIOUS){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else return super.onKeyDown(keyCode,event);return true;}
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||keyCode==KeyEvent.KEYCODE_HEADSETHOOK){if(localPlayer!=null){togglePlayback();return true;}}
        return super.onKeyDown(keyCode,event);
    }
    void chooseAppearance(){final String[] choices={"Electric Night", "Pearl Day", "Carbon Sport"};new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(choices,appearance,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){appearance=n;getPreferences(MODE_PRIVATE).edit().putInt("appearance",n).apply();loadN14Skin();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    void chooseClockFormat(){final String[] choices={"24-hour", "12-hour AM/PM"};new AlertDialog.Builder(this).setTitle("Clock format").setSingleChoiceItems(choices,clock12?1:0,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){clock12=n==1;getPreferences(MODE_PRIVATE).edit().putBoolean("clock12",clock12).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    String clockText(){return new SimpleDateFormat(clock12?"hh:mm a":"HH:mm",Locale.ENGLISH).format(new Date());}
    void showZomaSettings(){
        final String[] items={"Appearance • Night / Daylight", "Clock • 12 / 24 hour", "USB music folder", "Music player selection", "App Center", "Maintenance Center", "Oil reminder • Show / hide", "Oil interval (km)", "OBD connection", "Android system settings", "Restore ZomaOS defaults"};
        final Dialog dialog=new Dialog(this);dialog.setTitle("ZomaOS V15.8 • Settings");
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(16,10,16,10);panel.setBackgroundColor(Color.rgb(16,39,59));
        TextView heading=new TextView(this);heading.setText("ZOMAOS V15.8   /   SETTINGS");heading.setTextSize(22);heading.setTextColor(Color.WHITE);heading.setPadding(12,8,8,14);panel.addView(heading);
        ListView list=new ListView(this);list.setCacheColorHint(Color.TRANSPARENT);list.setDividerHeight(1);list.setScrollingCacheEnabled(false);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,items){@Override public View getView(int pos,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(pos,convert,parent);v.setTextColor(Color.WHITE);v.setTextSize(17);v.setPadding(14,10,8,10);return v;}};
        list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(this);close.setText("BACK TO DASHBOARD");panel.addView(close);close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int n,long id){dialog.dismiss();switch(n){
            case 0:chooseAppearance();break;case 1:chooseClockFormat();break;
            case 2:chooseUsbRoot();break;case 3:chooseAnyMusicApp();break;case 4:showAppCenter();break;
            case 5:maintenanceCenter();break;case 6:toggleOilReminder();break;
            case 7:maintenanceInput("Oil change interval (km)","oil_interval",getPreferences(MODE_PRIVATE).getInt("oil_interval",7000));break;
            case 8:showMode();break;case 9:launch(new String[]{},"settings");break;

            case 10:new AlertDialog.Builder(MainActivity.this).setTitle("Restore defaults?").setPositiveButton("Reset",new DialogInterface.OnClickListener(){public void onClick(DialogInterface a,int b){getPreferences(MODE_PRIVATE).edit().clear().apply();theme=0;showAnalog=true;centerMode=0;appearance=0;clock12=false;dashboard.invalidate();}}).setNegativeButton("Cancel",null).show();break;
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
    // Coordinates are in the same 800x480 virtual canvas as drawLiveProgress.
    static final float MUSIC_SEEK_LEFT=197f;
    static final float MUSIC_SEEK_RIGHT=434f;
    void seekMusic(float x){
        if(localPlayer==null)return;
        try{
            int duration=localPlayer.getDuration();
            if(duration>0){
                float seekLeft=appearance==1?203f:186f;
                float seekRight=appearance==1?510f:518f;
                float fraction=Math.max(0f,Math.min(1f,(x-seekLeft)/(seekRight-seekLeft)));
                localPlayer.seekTo((int)(duration*fraction));
                dashboard.invalidate();
            }
        }catch(Exception e){toast("Seeking unavailable");}
    }
    void openMaps(){launch(new String[]{"com.google.android.apps.maps","com.sygic.aura","com.mapswithme.maps.pro"},"");}
    void showMode(){final String[] modes={"DEMO (clearly simulated)","Bluetooth ELM327 (paired)","Phone bridge over Wi-Fi", "Disconnect / live blank", "Reset trip"};new AlertDialog.Builder(this).setTitle("ZomaOS Telemetry Source").setItems(modes,new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)demo();if(which==1)chooseBluetooth();if(which==2)bridgeDialog();if(which==3){stop();simulated=false;resetReadings();setStatus("DISCONNECTED");}if(which==4){tripKm=0;dashboard.invalidate();}}}).show();}
    void demo(){stop();resetReadings();simulated=true;carbonClock=false;dashboard.invalidate();toast("DEMO: simulated speed shown in clock dial");setStatus("DEMO / NOT REAL DATA");new Thread(new Runnable(){public void run(){int i=0;while(simulated){int s=(int)(35+25*Math.sin(i/7.0));setSample(s,800+s*35,85+(i%7),25+(i%35),60);i++;try{Thread.sleep(900);}catch(Exception e){break;}}}}).start();}
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
        void fitText(String v,float x,float y,float size,float maxWidth,int color,boolean bold){
            fill(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size);
            if(p.measureText(v)>maxWidth)p.setTextSize(size*maxWidth/p.measureText(v));
            c.drawText(v,x,y,p);
        }
        void fitCenteredText(String v,float x,float y,float size,float maxWidth,int color,boolean bold){
            fill(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size);float measured=p.measureText(v);
            if(measured>maxWidth)p.setTextSize(size*maxWidth/measured);
            c.drawText(v,x-p.measureText(v)/2,y,p);
        }
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
        // V11: artwork is ONLY the vehicle photograph. Every control and clock is live Canvas UI.
        void glass(float l,float t,float r,float b,boolean day){
            box(l+1,t+3,r+1,b+4,day?Color.argb(35,17,58,84):Color.argb(70,0,7,21));
            box(l,t,r,b,day?Color.argb(231,246,251,254):Color.argb(229,13,56,100));
            line(l+11,t+1,r-11,t+1,day?col("#FFFFFF"):col("#50DDF8"),1.5f);
        }
        // V11 premium composition: full-bleed photo + independent live controls.
        // Hit regions match onTouchEvent; no text or controls are baked into artwork.
        // Exact-reference cockpit: the visual chrome comes from the approved 800x480 artwork.
        // Dynamic text and clock hands are always rendered live. Hit regions below are real.
        void drawLiveProgress(float left,float top,float right,boolean active){
            fill(col("#142D47"));c.drawRoundRect(new RectF(left,top,right,top+6),3,3,p);
            if(!active||localPlayer==null){text("--:--",left,top+24,10,col("#8FA9BE"),false);return;}
            try{
                int duration=localPlayer.getDuration();int position=localPlayer.getCurrentPosition();
                if(duration<=0)return;
                float f=Math.max(0f,Math.min(1f,position/(float)duration));
                fill(col("#48E5FF"));c.drawRoundRect(new RectF(left,top,left+(right-left)*f,top+6),3,3,p);
                fill(Color.WHITE);c.drawCircle(left+(right-left)*f,top+3,5,p);
                text(String.format(Locale.ENGLISH,"%d:%02d",position/60000,(position/1000)%60),left,top+24,10,Color.WHITE,false);
                String end=String.format(Locale.ENGLISH,"%d:%02d",duration/60000,(duration/1000)%60);
                text(end,right-29,top+24,10,Color.WHITE,false);
            }catch(Exception ignored){}
        }
        // V12.3 Carbon Sport: lightweight vector UI, no baked-in controls.
        // All interactive positions match the existing Dashboard touch handler.
        void drawCarbon(){
            int bg=col("#080B11"), panel=col("#141A23"), edge=col("#39404C");
            int red=col("#FF304B"), white=col("#F3F6FB"), muted=col("#A8B2C0");
            gradient(0,0,800,480,bg,col("#1A0C14"));
            // Subtle diagonal carbon weave; deliberately low contrast.
            for(int k=-480;k<850;k+=12){line(k,0,k+480,480,col("#141820"),1);}
            box(0,0,800,54,col("#0B1018"));line(0,53,800,53,red,1.5f);
            text("Zoma",108,37,27,white,true);text("OS",183,37,27,red,true);
            fitCenteredText(clockText(),741,37,25,94,white,true);
            text("BT",576,34,13,muted,true);text("Wi-Fi",622,34,13,muted,true);
            // Seven sidebar hit zones, consistent with V11.
            String[] side={"HOME","MUSIC","RADIO","AUTO","OBD","APPS","SET"};
            box(0,55,95,434,col("#0B1018"));
            for(int i=0;i<7;i++){
                int yy=99+i*51;
                if(i==0){box(7,yy-28,89,yy+18,col("#24121B"));line(9,yy+18,87,yy+18,red,2);}
                centerText(side[i],48,yy+1,11,i==0?red:white,true);
            }
            // The central stage uses a photograph only as an optional subdued visual.
            if(daySuv!=null){
                p.setAlpha(105);c.drawBitmap(daySuv,new Rect(230,75,710,365),new RectF(103,65,428,273),p);p.setAlpha(255);
            }
            text("CARBON SPORT",116,88,15,red,true);
            text("GEELY X7",225,263,18,white,true);
            // Music: progress is always computed from actual local player state.
            box(430,76,786,273,panel);line(440,76,776,76,red,2);
            text("USB MUSIC",447,105,16,white,true);
            String song=(usbMode||localMode)?trackTitle:"Select USB music";
            if(song==null||song.length()==0)song="Select USB music";
            if(song.length()>29)song=song.substring(0,28)+"…";
            text(song,447,134,13,muted,false);
            drawLiveProgress(447,173,765,usbMode||localMode);
            centerText("PREV",500,248,15,white,true);
            centerText("PLAY",610,248,16,red,true);
            centerText("NEXT",719,248,15,white,true);
            // Radio: do not invent station frequency or metadata from the MCU.
            box(104,282,425,366,panel);line(113,282,415,282,red,2);
            text("FM RADIO",119,312,17,white,true);
            text(getPreferences(MODE_PRIVATE).getString("last_audio_source","").equals("fm")?"RADIO SELECTED":"OPEN FACTORY RADIO",119,341,12,muted,false);
            box(430,282,786,366,panel);line(440,282,776,282,red,2);
            text("ZOMAOS",450,314,17,white,true);
            text(new SimpleDateFormat("EEE, dd MMM yyyy",Locale.ENGLISH).format(new Date()),450,341,13,muted,false);
            // Four dock buttons use the same dockEdges hit boxes as all other themes.
            for(int i=0;i<4;i++){
                float l=dockEdges[i]+2,r=dockEdges[i+1]-5;
                box(l,382,r,425,panel);line(l+8,424,r-8,424,red,2);
                centerText(dockLabel(i),(l+r)/2,408,13,white,true);
            }
            centerText("ZomaOS  |  CARBON SPORT",445,458,12,muted,false);
        }
        // V12.7.1 cinematic scene contains only vehicle and city; all UI widgets are drawn live.
        // Real-time clock, song, playback seek, source and analog hands are repainted live.
        // No climate bar is present in any of the four image resources.
        // V12.8: glass widgets rendered separately from static cinematic scene.
        // V12.8: multi-layer glass widget. All text/icons remain real Canvas elements.
        // No fixed labels, clock faces, or music progress are rasterized into the scene.
        // Responsive glass: no scaled full-panel PNG. Scaling the old artwork
        // introduced giant circular arcs across unrelated cards.
        void liveCard(float l,float t,float r,float b,int surface,int edge){
            float radius=14f;
            RectF outer=new RectF(l,t,r,b);
            // Ambient drop shadow gives each card separation from the city photograph.
            p.reset();p.setAntiAlias(true);p.setColor(Color.argb(100,0,0,0));
            p.setShadowLayer(10f,0f,5f,Color.argb(155,0,0,0));
            c.drawRoundRect(new RectF(l+2,t+3,r+2,b+4),radius,radius,p);
            p.clearShadowLayer();
            // Semi-transparent base, not an opaque rectangle.
            int base=Color.argb(appearance==1?186:182,
                Color.red(surface),Color.green(surface),Color.blue(surface));
            p.setColor(base);p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(outer,radius,radius,p);
            // Internal top-to-bottom highlight and subtle darkening create glass depth.
            p.setShader(new android.graphics.LinearGradient(l,t,l,b,
                new int[]{Color.argb(52,255,255,255),Color.argb(9,255,255,255),Color.argb(47,0,0,0)},
                new float[]{0f,0.27f,1f},android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(l+1,t+1,r-1,b-1),radius-1,radius-1,p);
            p.setShader(null);
            // Outer diffuse neon halo, then crisp thin border.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3f);
            p.setColor(Color.argb(52,Color.red(edge),Color.green(edge),Color.blue(edge)));
            c.drawRoundRect(new RectF(l-1,t-1,r+1,b+1),radius+1,radius+1,p);
            p.setStrokeWidth(1.1f);
            p.setColor(Color.argb(205,Color.red(edge),Color.green(edge),Color.blue(edge)));
            c.drawRoundRect(new RectF(l+.5f,t+.5f,r-.5f,b-.5f),radius,radius,p);
            // Specular line on upper edge. Shorter than the panel to avoid flat framing.
            p.setStrokeWidth(1.3f);p.setColor(Color.argb(80,255,255,255));
            c.drawLine(l+radius,t+1.8f,r-radius,t+1.8f,p);
            p.setStyle(Paint.Style.FILL);p.setShader(null);p.clearShadowLayer();
            // Localized blue underglow, like the approved Nexus reference.
            p.setShader(new LinearGradient(l,t+(b-t)*.7f,l,b+3f,
                new int[]{Color.TRANSPARENT,Color.argb(82,Color.red(edge),Color.green(edge),Color.blue(edge))},
                null,Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(l+3,b-8,r-3,b-1),4f,4f,p);
            p.setShader(null);
        }
        void liveClock(float cx,float cy,int edge,int fg,int surface){
            // Live clock with layered metallic bezel; not a bitmap with frozen hands.
            p.reset();p.setAntiAlias(true);p.setColor(Color.argb(155,0,0,0));
            p.setShadowLayer(9f,0f,4f,Color.BLACK);c.drawCircle(cx,cy,67,p);p.clearShadowLayer();
            p.setShader(new LinearGradient(cx-60,cy-60,cx+60,cy+60,
                new int[]{col("#D9F1FF"),col("#325B78"),col("#06111F"),col("#B4D7EC")},
                null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,66,p);p.setShader(null);
            p.setColor(col("#071522"));c.drawCircle(cx,cy,61,p);
            p.setShader(new RadialGradient(cx-12,cy-15,61,
                new int[]{col("#20384B"),col("#050A11")},null,Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,57,p);p.setShader(null);
            for(int i=0;i<60;i++){
                float a=(float)(i*Math.PI/30-Math.PI/2);
                float inner=i%5==0?43:51;
                line(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,
                     cx+(float)Math.cos(a)*54,cy+(float)Math.sin(a)*54,
                     i%5==0?edge:Color.argb(135,180,205,221),i%5==0?2.6f:0.8f);
            }
            centerText("ZomaOS",cx,cy-14,11,fg,false);
            Calendar t=Calendar.getInstance();float ss=t.get(Calendar.SECOND),mm=t.get(Calendar.MINUTE)+ss/60f,hh=t.get(Calendar.HOUR)+mm/60f;
            hand(cx,cy,(float)(hh*Math.PI/6-Math.PI/2),29,5,fg);
            hand(cx,cy,(float)(mm*Math.PI/30-Math.PI/2),43,3,fg);
            hand(cx,cy,(float)(ss*Math.PI/30-Math.PI/2),49,1,edge);
            fill(edge);c.drawCircle(cx,cy,4,p);
        }
        void n14Image(Bitmap b,float l,float t,float r,float bottom){
            if(b==null)return;
            p.reset();p.setAntiAlias(true);p.setFilterBitmap(true);p.setColor(Color.WHITE);
            int cropX=appearance==1?25:4, cropTop=8;
            Rect source=new Rect(cropX,cropTop,b.getWidth()-4,b.getHeight());
            c.drawBitmap(b,source,new RectF(l,t,r,bottom),p);
        }
        void n14ClockHands(float cx,float cy,int edge){
            Calendar now=Calendar.getInstance();
            float ss=now.get(Calendar.SECOND),mm=now.get(Calendar.MINUTE)+ss/60f,hh=now.get(Calendar.HOUR)+mm/60f;
            hand(cx,cy,(float)(hh*Math.PI/6-Math.PI/2),27,4,Color.WHITE);
            hand(cx,cy,(float)(mm*Math.PI/30-Math.PI/2),43,3,Color.WHITE);
            hand(cx,cy,(float)(ss*Math.PI/30-Math.PI/2),48,1,edge);
            fill(edge);c.drawCircle(cx,cy,3,p);
        }

        void liteIcon(int id,float cx,float cy,int color){
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);
            switch(id){
                case 0: // home
                    c.drawLine(cx-9,cy,cx,cy-8,p);c.drawLine(cx,cy-8,cx+9,cy,p);c.drawRect(cx-6,cy,cx+6,cy+9,p);break;
                case 1: // music
                    c.drawLine(cx+2,cy-9,cx+2,cy+5,p);c.drawLine(cx+2,cy-9,cx+10,cy-11,p);c.drawLine(cx+10,cy-11,cx+10,cy+3,p);c.drawOval(new RectF(cx-6,cy+3,cx+2,cy+9),p);c.drawOval(new RectF(cx+2,cy+1,cx+10,cy+7),p);break;
                case 2: // radio
                    c.drawRoundRect(new RectF(cx-11,cy-5,cx+11,cy+8),2,2,p);c.drawLine(cx-7,cy-7,cx+7,cy-12,p);c.drawCircle(cx-5,cy+1,3,p);c.drawLine(cx+2,cy,cx+8,cy,p);c.drawLine(cx+2,cy+4,cx+8,cy+4,p);break;
                case 7: // Android Auto arrow
                    Path ar=new Path();ar.moveTo(cx,cy-11);ar.lineTo(cx+10,cy+9);ar.lineTo(cx,cy+4);ar.lineTo(cx-10,cy+9);ar.close();c.drawPath(ar,p);break;
                case 3: // OBD engine
                    c.drawRect(cx-9,cy-5,cx+8,cy+7,p);c.drawLine(cx-5,cy-9,cx+4,cy-9,p);c.drawLine(cx-11,cy-2,cx-13,cy-2,p);c.drawLine(cx+9,cy+1,cx+13,cy+1,p);break;
                case 4: // apps
                    for(int i=0;i<4;i++){float x=cx-10+(i%2)*12,y=cy-9+(i/2)*12;c.drawRect(x,y,x+8,y+8,p);}break;
                case 5: // settings
                    c.drawCircle(cx,cy,8,p);c.drawCircle(cx,cy,3,p);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(cx+(float)Math.cos(angle)*9,cy+(float)Math.sin(angle)*9,cx+(float)Math.cos(angle)*12,cy+(float)Math.sin(angle)*12,p);}break;
                case 6: // folder
                    c.drawRoundRect(new RectF(cx-11,cy-5,cx+11,cy+9),2,2,p);c.drawLine(cx-9,cy-5,cx-5,cy-9,p);c.drawLine(cx-5,cy-9,cx+1,cy-9,p);break;
            }
            p.setStyle(Paint.Style.FILL);
        }
        void premiumIcon(int id,float cx,float cy,float size){
            if(id<0||id>=premiumIcons.length||premiumIcons[id]==null)return;
            Bitmap b=premiumIcons[id];p.reset();p.setAntiAlias(true);p.setFilterBitmap(true);p.setAlpha(255);
            c.drawBitmap(b,null,new RectF(cx-size/2,cy-size/2,cx+size/2,cy+size/2),p);
        }
        void carbonDial(float cx,float cy,float radius,boolean clockMode,int red){
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(col("#333944"));c.drawCircle(cx,cy,radius,p);
            p.setStrokeWidth(3);p.setColor(red);c.drawCircle(cx,cy,radius-5,p);
            for(int i=0;i<12;i++){double a=i*Math.PI/6-Math.PI/2;float x1=cx+(radius-20)*(float)Math.cos(a),y1=cy+(radius-20)*(float)Math.sin(a);float x2=cx+(radius-10)*(float)Math.cos(a),y2=cy+(radius-10)*(float)Math.sin(a);p.setStrokeWidth(i%3==0?3:1);p.setColor(i%3==0?Color.WHITE:col("#8E96A5"));c.drawLine(x1,y1,x2,y2,p);}
            p.setStyle(Paint.Style.FILL);
            if(clockMode){n14ClockHands(cx,cy,red);}
            else {centerText(speed>=0?String.valueOf(speed):"--",cx,cy+9,39,Color.WHITE,true);centerText("km/h",cx,cy+30,12,Color.WHITE,false);if(speed<0||simulated)centerText("NO LIVE OBD",cx,cy+47,9,red,false);}
        }
        void drawCarbonSport(){drawMasterV15();}

        // V15.3: cover baked media labels with a live player panel.
        // Theme art remains full resolution; only the media region is redrawn.
        // V15.5: geometry follows the 800x480 artwork, not the previous V15.3 mockup.
        // The master images already contain a clean numbered clock face.
        // A true symmetric tapered hand. The old triangle used only a 0.7px tip
        // and made the hour and minute hands appear like thin needles.
        // Sport design #2: short counterweight, angular tapered body and crisp tip.
        // All vertices are calculated from one shared pivot to prevent wobble.
        void premiumHand(float cx,float cy,float angle,float length,float width,int color){
            float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            float px=-dy,py=dx;
            android.graphics.Path shape=new android.graphics.Path();
            shape.moveTo(cx-dx*12+px*width*.30f,cy-dy*12+py*width*.30f);
            shape.lineTo(cx+dx*5+px*width*.55f,cy+dy*5+py*width*.55f);
            shape.lineTo(cx+dx*(length-12)+px*width*.34f,cy+dy*(length-12)+py*width*.34f);
            shape.lineTo(cx+dx*length,cy+dy*length);
            shape.lineTo(cx+dx*(length-12)-px*width*.34f,cy+dy*(length-12)-py*width*.34f);
            shape.lineTo(cx+dx*5-px*width*.55f,cy+dy*5-py*width*.55f);
            shape.lineTo(cx-dx*12-px*width*.30f,cy-dy*12-py*width*.30f);
            shape.close();
            p.reset();p.setAntiAlias(true);
            p.setColor(Color.argb(105,0,0,0));
            c.save();c.translate(1.2f,1.5f);c.drawPath(shape,p);c.restore();
            p.setColor(color);c.drawPath(shape,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.75f);
            p.setColor(Color.argb(145,255,255,255));c.drawPath(shape,p);
            p.setStyle(Paint.Style.FILL);
        }
        void drawMasterV15(){
            boolean day=appearance==1;
            int accent=appearance==2?col("#FF435A"):day?col("#1679C4"):col("#2BDBFF");
            int fg=day?col("#172A40"):Color.WHITE;
            int muted=day?col("#456178"):col("#AEC6D8");
            n14Image(masterArtwork,0,0,800,480);
            if(day){fill(col("#152B3B"));c.drawRect(0,0,3,480,p);}
            // Dial centers measured from the actual 800x480 master artwork, then
            // mapped through n14Image's source crop (Day left=25; others left=4; top=8).
            // Day requires an independent pivot; Carbon/Electric share calibrated geometry.
            // Day original center ~ (237,151) -> rendered ~ (219,145).
            // Carbon/Electric original center ~ (232,148) -> rendered ~ (229,142).
            float cx=day?219f:229f,cy=day?141f:138f;
            Calendar now=Calendar.getInstance();
            float sec=now.get(Calendar.SECOND),minute=now.get(Calendar.MINUTE)+sec/60f,hour=now.get(Calendar.HOUR)+minute/60f;
            if(carbonClock){
                // Do NOT repaint the clock dial or its printed numerals.
                int handColor=day?col("#E5F4FF"):Color.WHITE;
                // Tapered premium hands with subtle drop shadow and metal hub.
                premiumHand(cx,cy,(float)(hour*Math.PI/6-Math.PI/2),49,9,handColor);
                premiumHand(cx,cy,(float)(minute*Math.PI/30-Math.PI/2),70,6,handColor);
                premiumHand(cx,cy,(float)(sec*Math.PI/30-Math.PI/2),77,1.8f,accent);
                fill(col("#233748"));c.drawCircle(cx,cy,6,p);
                fill(col("#EAF6FF"));c.drawCircle(cx,cy,2.8f,p);
            }else{
                fill(day?col("#142A3E"):col("#10131C"));c.drawCircle(cx,cy,69,p);
                centerText(speed>=0?String.valueOf(speed):"--",cx,cy+10,33,Color.WHITE,true);
                centerText(simulated?"DEMO km/h":"km/h",cx,cy+29,11,simulated?col("#FFCE65"):Color.WHITE,false);
            }
            // Minimal translucent status text; no opaque rectangular header over the SUV.
            fill(day?Color.argb(135,245,250,255):Color.argb(130,4,13,24));
            c.drawRoundRect(new RectF(410,5,791,32),8,8,p);
            text("ZomaOS",422,24,13,accent,true);
            text("Wi-Fi",542,23,12,muted,false);
            text("BT",595,23,12,muted,false);
            fitCenteredText(clockText(),728,26,20,117,fg,true);
            // Player fits WITHIN the artwork's horizontal media frame (y=300..368).
            // Do not cover its illuminated border, especially on Pearl Day.
            float pl=day?118:116,pr=day?759:764,pt=310,pb=359;
            fill(day?Color.argb(228,239,247,253):Color.argb(222,12,24,39));
            c.drawRoundRect(new RectF(pl,pt,pr,pb),7,7,p);
            float artX=pl+5;
            if(albumArt!=null&&!albumArt.isRecycled()){
                fill(Color.WHITE);c.drawBitmap(albumArt,null,new RectF(artX,314,artX+40,354),p);
            }else{
                fill(day?col("#D8E5EF"):col("#23394E"));c.drawRoundRect(new RectF(artX,314,artX+40,354),3,3,p);
                centerText("♫",artX+20,342,23,accent,true);
            }
            String title=(usbMode||localMode)?trackTitle:"Choose USB music";
            String artist=(usbMode||localMode)?trackArtist:"USB MUSIC";
            if(title==null||title.length()==0)title="Choose USB music";
            if(artist==null)artist="";
            float tx=artX+48, barRight=day?520:530;
            fill(fg);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(12);
            title=TextUtils.ellipsize(title,new android.text.TextPaint(p),barRight-tx,TextUtils.TruncateAt.END).toString();
            text(title,tx,326,12,fg,true);
            fill(muted);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(10);
            artist=TextUtils.ellipsize(artist,new android.text.TextPaint(p),barRight-tx,TextUtils.TruncateAt.END).toString();
            text(artist,tx,338,10,muted,false);
            float left=tx,right=barRight,top=346;
            fill(day?col("#B9CDD9"):col("#36506B"));
            c.drawRoundRect(new RectF(left,top,right,top+3),2,2,p);
            if(localPlayer!=null){try{
                int duration=localPlayer.getDuration(),position=localPlayer.getCurrentPosition();
                if(duration>0){float f=Math.max(0f,Math.min(1f,position/(float)duration));
                    fill(accent);c.drawRoundRect(new RectF(left,top,left+(right-left)*f,top+3),2,2,p);
                    fill(fg);c.drawCircle(left+(right-left)*f,top+1.5f,3,p);
                }
            }catch(Exception ignored){}}
            musicCircle(542,335,15,"⇄",shuffleMode,day,accent);
            musicCircle(584,335,17,"◀",false,day,accent);
            musicCircle(631,335,21,localPlayer!=null&&localPlayer.isPlaying()?"Ⅱ":"▶",true,day,accent);
            musicCircle(680,335,17,"▶",false,day,accent);
            musicCircle(725,335,15,repeatMode==2?"↻¹":"↻",repeatMode!=0,day,accent);
            drawOriginalFiveDockApps(day,accent);
            if(pressedDock>=0&&pressedDock<5){
                float l=dockEdges[pressedDock]+5,r=dockEdges[pressedDock+1]-5;
                p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);p.setColor(accent);
                c.drawRoundRect(new RectF(l,386,r,468),8,8,p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        void musicCircle(float x,float y,float r,String glyph,boolean active,boolean day,int accent){
            fill(day?col("#D5E5F2"):col("#1A324A"));c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(active?2.4f:1.3f);
            p.setColor(active?accent:(day?col("#7093AD"):col("#789AB2")));c.drawCircle(x,y,r-1,p);
            p.setStyle(Paint.Style.FILL);
            centerText(glyph,x,y+5,r>19?19:r>16?16:14,active?accent:(day?col("#244963"):Color.WHITE),true);
        }

        // Draw a compact flat apps glyph, matching the neighboring dock icons.
        // Avoid the circular badge which made this button visually inconsistent.
        void drawOriginalFiveDockApps(boolean day,int accent){
            float x=569f,y=423f;
            // Restore the baked-in engine area using BOTH clean sides of the same
            // tile, interpolating each row's actual theme gradient. Feather edges
            // to preserve highlights and avoid the stretched rectangular stamp.
            if(masterArtwork!=null){
                int cropX=appearance==1?25:4;
                float sx=(masterArtwork.getWidth()-cropX-4)/800f;
                float sy=(masterArtwork.getHeight()-8)/480f;
                Bitmap tile=masterArtwork;
                Paint repair=new Paint(Paint.ANTI_ALIAS_FLAG);
                final int left=537,right=604,top=398,bottom=450;
                for(int yy=top;yy<bottom;yy++){
                    int srcY=Math.max(0,Math.min(tile.getHeight()-1,8+Math.round(yy*sy)));
                    int leftX=Math.max(0,Math.min(tile.getWidth()-1,cropX+Math.round(524f*sx)));
                    int rightX=Math.max(0,Math.min(tile.getWidth()-1,cropX+Math.round(614f*sx)));
                    int lc=tile.getPixel(leftX,srcY),rc=tile.getPixel(rightX,srcY);
                    float fy=Math.min(1f,Math.min((yy-top+1)/6f,(bottom-yy)/6f));
                    for(int xx=left;xx<right;xx++){
                        float t=(xx-left)/(float)(right-left);
                        // Slightly vary the sample within clean texture strips
                        // to retain the carbon weave without importing engine pixels.
                        int jitter=(xx%5)-2;
                        int sl=Math.max(0,Math.min(tile.getWidth()-1,leftX+jitter));
                        int sr=Math.max(0,Math.min(tile.getWidth()-1,rightX+jitter));
                        int l=tile.getPixel(sl,srcY),rr=tile.getPixel(sr,srcY);
                        int red=Math.round(Color.red(l)*(1-t)+Color.red(rr)*t);
                        int green=Math.round(Color.green(l)*(1-t)+Color.green(rr)*t);
                        int blue=Math.round(Color.blue(l)*(1-t)+Color.blue(rr)*t);
                        float fx=Math.min(1f,Math.min((xx-left+1)/7f,(right-xx)/7f));
                        repair.setColor(Color.rgb(red,green,blue));
                        repair.setAlpha(Math.round(255f*Math.min(fx,fy)));
                        c.drawRect(xx,yy,xx+1,yy+1,repair);
                    }
                }
            }
            int color=day?col("#1679C4"):col("#C5EDFF");
            p.reset();p.setAntiAlias(true);p.setColor(color);
            for(int row=0;row<2;row++)for(int column=0;column<2;column++){
                float l=x-17+column*20,t=y-19+row*20;
                c.drawRoundRect(new RectF(l,t,l+14,t+14),2,2,p);
            }
        }
        void drawApproved(){drawMasterV15();}
        void drawV11(){drawApproved();}
        @Override protected void onDraw(Canvas canvas){
            c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);
            drawV11();c.restore();postInvalidateDelayed(1000); // 1 FPS for clock seconds; no 30/60 FPS continuous scene animation
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN){
                android.util.Log.d("ZomaTouch", "DOWN x="+x+" y="+y);
                // A fresh gesture must not inherit an old pending long-press.
                if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);
                pendingDockLongPress=null;pressedDock=-1;dockLongPressed=false;draggingSeek=false;
                touchStarted=true;downX=x;downY=y;
                if(y>=382&&y<=472&&x>=70&&x<787){
                    pressedDock=masterDockIndex(x);dockLongPressed=false;invalidate();
                    final int idx=pressedDock;
                    if(idx>=0&&idx<5){pendingDockLongPress=new Runnable(){public void run(){if(pressedDock==idx){dockLongPressed=true;editDock(idx);}}};
                        dockHandler.postDelayed(pendingDockLongPress,650);}
                    return true;
                }
                if(y>=341&&y<=355&&x>=(appearance==1?203:186)&&x<=(appearance==1?510:518)&&localPlayer!=null){draggingSeek=true;seekMusic(x);return true;}
                return true;
            }
            if(a==MotionEvent.ACTION_CANCEL){
                android.util.Log.w("ZomaTouch", "CANCEL dock="+pressedDock+" x="+x+" y="+y);
                if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);
                pendingDockLongPress=null;pressedDock=-1;draggingSeek=false;touchStarted=false;invalidate();return true;
            }
            if(a==MotionEvent.ACTION_MOVE){
                if(draggingSeek){seekMusic(x);return true;}
                if(pressedDock>=0&&(Math.abs(x-downX)>DOCK_LONG_PRESS_SLOP||Math.abs(y-downY)>DOCK_LONG_PRESS_SLOP)){
                    // Movement cancels the long press, not the short tap. Older resistive
                    // touch panels commonly jitter before ACTION_UP.
                    if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);
                    pendingDockLongPress=null;
                }
                return true;
            }
            if(a!=MotionEvent.ACTION_UP)return true;
            if(!touchStarted){android.util.Log.w("ZomaTouch", "UP without DOWN x="+x+" y="+y);return true;}
            android.util.Log.d("ZomaTouch", "UP x="+x+" y="+y+" dock="+pressedDock);
            touchStarted=false;
            if(pendingDockLongPress!=null){dockHandler.removeCallbacks(pendingDockLongPress);pendingDockLongPress=null;}
            if(pressedDock>=0){
                int i=pressedDock;pressedDock=-1;invalidate();
                // Never trigger a tile when the user drags far away or into another tile.
                boolean withinTile=x>=dockEdges[i]-DOCK_RELEASE_SLOP
                    &&x<=dockEdges[i+1]+DOCK_RELEASE_SLOP
                    &&y>=382-DOCK_RELEASE_SLOP&&y<=472+DOCK_RELEASE_SLOP;
                if(!dockLongPressed&&withinTile){
                    android.util.Log.i("ZomaTouch", "ACTIVATE dock="+i);
                    activateDock(i);
                }else{
                    android.util.Log.w("ZomaTouch", "SKIPPED dock="+i+" long="+dockLongPressed+" within="+withinTile);
                }
                dockLongPressed=false;return true;
            }
            if(draggingSeek){draggingSeek=false;seekMusic(x);return true;}
            x=downX;y=downY; // Use the original contact point for single-tap hotspots.
            // Header quick links open the actual system controls (not decorative labels).
            if(y>=0&&y<=40){
                if(x>=525&&x<580){try{startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));}catch(Throwable settingsError){launch(new String[]{},"settings");}return true;}
                if(x>=580&&x<640){try{startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Throwable settingsError){launch(new String[]{},"settings");}return true;}
            }
            // A compact Apps hotspot occupies the lower portion of the folder tile.
            // Five visible left-side icons: Home, Navigation, Music, Phone, Settings.
            // Do not reuse the seven-item V14 hit grid on the V15 artwork.
            if(x<107&&y>=65&&y<354){
                if(y<125)return true; // Home: stay on launcher
                if(y<183){openAndroidAuto();return true;}
                if(y<240){musicHub();return true;}
                if(y<298){openFactoryPhone();return true;}
                showZomaSettings();return true;
            }
            // Media controls are inside the live 130..767 x 260..350 panel.
            if(y>=310&&y<=359&&x>=521&&x<=744){
                if(x<562)toggleShuffle();
                else if(x<609){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else openRememberedUsb();}
                else if(x<655)togglePlayback();
                else if(x<703){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else openRememberedUsb();}
                else cycleRepeat();
                return true;
            }
            if(y>=28&&y<=285&&x>=105&&x<=357){return true;}
            if(y>=310&&y<=359&&x>=130&&x<521){openRememberedUsb();return true;}
            // Do not open Settings from the decorative top status/header area.
            return true;
        }

    }
}
