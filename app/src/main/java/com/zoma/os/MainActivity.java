package com.zoma.os;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.IBinder;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
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
    Bitmap carbonGaugePanel, carbonMusicPanel, carbonDockPanel, carbonSidePanel, carbonAnalogDial; Bitmap masterArtwork; Bitmap premiumCar; Bitmap premiumScene; Bitmap premiumMasterV2; Bitmap premiumNightClean,premiumDayClean,premiumNightRoad,premiumDayRoad,premiumWheel; Bitmap premiumV6Bg,premiumV6Fx,premiumV6Car,premiumV6Wn,premiumV6Wr; Bitmap v147CarScene;
    BitmapShader premiumDayRoadShader,premiumNightRoadShader;
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
    AudioManager audioManager; boolean hasAudioFocus=false; boolean clock12=false; int appearance=0; final boolean premiumMotion=false; int premiumFps=30; int premiumGaugeMode=0; int premiumDayMode=2; int premiumSceneMode=0; Bitmap premiumEnBg; boolean enTried=false; int enLoadedFor=-1; boolean enDayNow=false; long dayAutoAt=0L; boolean dayAutoVal=true;
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
            .setItems(trA(new String[]{"Set Program","Restore Default"}),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int choice){
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
        new AlertDialog.Builder(this).setTitle("Set Program • "+dockDefaults[index]).setItems(trA(names),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            String pkg=apps.get(which).activityInfo.packageName;
            getPreferences(MODE_PRIVATE).edit().putString("dock_pkg_"+index,pkg).apply();dashboard.invalidate();
        }}).setNegativeButton(tr("Cancel"),null).show();
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
    @Override public void onCreate(Bundle b){super.onCreate(b);installCrashLog();ar="ar".equals(getPreferences(MODE_PRIVATE).getString("lang","en"));gpsInit();applyEdgeToEdge();centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);appearance=getPreferences(MODE_PRIVATE).getInt("appearance",0);premiumFps=getPreferences(MODE_PRIVATE).getInt("premiumFps",30);premiumGaugeMode=getPreferences(MODE_PRIVATE).getInt("premiumGaugeMode",0);premiumDayMode=getPreferences(MODE_PRIVATE).getInt("premiumDayMode",2);premiumSceneMode=getPreferences(MODE_PRIVATE).getInt("premiumScene",0);if(premiumFps!=10&&premiumFps!=20&&premiumFps!=30)premiumFps=30;shuffleMode=getPreferences(MODE_PRIVATE).getBoolean("shuffleMode",false);repeatMode=getPreferences(MODE_PRIVATE).getInt("repeatMode",0);if(appearance<0||appearance>2)appearance=0;carbonClock=getPreferences(MODE_PRIVATE).getBoolean("carbonClock",true);loadN14Skin();showAnalog=getPreferences(MODE_PRIVATE).getBoolean("showAnalog",true);clock12=getPreferences(MODE_PRIVATE).getBoolean("clock12",false);audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);dashboard=new Dashboard();setContentView(dashboard);dashboard.setClickable(true);startMusicDiscovery();try{restoreUsbFolder();}catch(Throwable err){android.util.Log.e("ZomaOS","USB restore failed during launcher startup",err);}resumeHandler.postDelayed(resumeTick,5000);resumeLastUsbOnce();registerWheelKeys();loadWelcomePrefs();registerScreenWake();startWelcome();ui.postDelayed(new Runnable(){public void run(){maybeShowLauncherHint();}},6000);}
    // Load only the selected scene and eight small themed assets. No GIF or frame loop.
    void recycleN14(Bitmap b){if(b!=null&&!b.isRecycled())b.recycle();}
    void loadN14Skin(){
        // V15.3 renders the selected 800x480 master only. Legacy V14 scene assets are not loaded.
        recycleN14(masterArtwork);
        int masterId=appearance==2?R.drawable.master_carbon:appearance==1?R.drawable.master_pearl:R.drawable.master_electric;
        masterArtwork=BitmapFactory.decodeResource(getResources(),masterId);
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
    @Override protected void onDestroy(){try{if(wheelRx!=null)unregisterReceiver(wheelRx);}catch(Throwable ignored){}try{if(factoryKeyRx!=null)unregisterReceiver(factoryKeyRx);}catch(Throwable ignored){}try{if(screenRx!=null)unregisterReceiver(screenRx);}catch(Throwable ignored){}
        if(musicInfoReceiver!=null){try{unregisterReceiver(musicInfoReceiver);}catch(Exception ignored){}}
        resumeHandler.removeCallbacks(resumeTick);stop();releaseLocalPlayer();super.onDestroy();
    }
    void showMediaDiagnostics(){
        String selected=getPreferences(MODE_PRIVATE).getString("musicPackage","Not selected");
        String msg="Selected player: "+selected+"\nMetadata: "+
            (trackTitle.length()>0?trackTitle+" / "+trackArtist:"No compatible metadata broadcast detected")+
            "\nSource: "+(metadataSource.length()>0?metadataSource:"none")+
            "\n\nThe factory player may keep metadata private. Read only, nothing is changed.";
        new AlertDialog.Builder(this).setTitle(tr("Media diagnostics - V7")).setMessage(msg)
            .setPositiveButton(tr("OK"),null).setNeutralButton("Choose player",new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){chooseAnyMusicApp();}}).show();
    }
    void resetReadings(){speed=-1;rpm=-1;coolant=-999;load=-1;fuel=-1;lastSample=0;}
    void stop(){running=false;simulated=false;try{if(btSocket!=null)btSocket.close();}catch(Exception ignored){}try{if(tcpSocket!=null)tcpSocket.close();}catch(Exception ignored){}btSocket=null;tcpSocket=null;}
    void setStatus(String s){status=s;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    void setSample(int s,int r,int t,int l,int f){long now=System.currentTimeMillis();if(!simulated && speed>=0 && lastSample>0 && now-lastSample<5000){double dk=(speed+s)/2.0*(now-lastSample)/3600000.0;tripKm+=dk;odoAdd(dk);}lastSample=now;speed=s;rpm=r;coolant=t;checkEngineAlert(t);load=l;fuel=f;ui.post(new Runnable(){public void run(){dashboard.invalidate();}});}
    int accent(){return Color.parseColor(theme==1?"#FFBD69":theme==2?"#60E8BA":theme==3?"#C58AFF":theme==4?"#FF6D80":"#60D7FF");}
    int mutedAccent(){return Color.parseColor(theme==1?"#A66A2C":theme==2?"#17785D":theme==3?"#6C3992":theme==4?"#92374B":"#17658B");}
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
          .setItems(trA(names),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){
              ResolveInfo app=all.get(n);
              ComponentName component=new ComponentName(app.activityInfo.packageName,app.activityInfo.name);
              getPreferences(MODE_PRIVATE).edit().putString(preferenceKey,component.flattenToString()).apply();
              openFactoryApp(preferenceKey,"Factory app");
          }}).setNegativeButton(tr("Cancel"),null).show();
    }
    void factoryBluetoothOptions(){
        new AlertDialog.Builder(this).setTitle(tr("Factory Bluetooth"))
          .setItems(trA(new String[]{"Open Bluetooth Music","Open Phone / Dial","Change Bluetooth app","Change Phone app"}),
          new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
              if(which==0)openFactoryBluetooth();
              else if(which==1)openFactoryPhone();
              else if(which==2)chooseFactoryApp("factoryBluetoothComponent","Bluetooth Music");
              else chooseFactoryApp("factoryPhoneComponent","Factory Phone");
          }}).setNegativeButton(tr("Cancel"),null).show();
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
        new AlertDialog.Builder(this).setTitle(tr("Select original Music app")).setItems(trA(labels),new android.content.DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int index){String pkg=candidates.get(index).activityInfo.packageName;
                getPreferences(MODE_PRIVATE).edit().putString("musicPackage",pkg).apply();openMusic();}
        }).setNeutralButton("All apps",new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseAnyMusicApp();}})
          .setNegativeButton(tr("Cancel"),null).show();
    }
    void chooseAnyMusicApp(){
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final ArrayList<ResolveInfo> all=new ArrayList<ResolveInfo>(getPackageManager().queryIntentActivities(probe,0));
        String[] labels=new String[all.size()];for(int i=0;i<all.size();i++)labels[i]=String.valueOf(all.get(i).loadLabel(getPackageManager()))+" ["+all.get(i).activityInfo.packageName+"]";
        new AlertDialog.Builder(this).setTitle(tr("Choose Music player")).setItems(trA(labels),new android.content.DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int index){getPreferences(MODE_PRIVATE).edit().putString("musicPackage",all.get(index).activityInfo.packageName).apply();openMusic();}
        }).setNegativeButton(tr("Cancel"),null).show();
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
    // Factory radio (com.spd.radio) status and keys. Only standard Android media-button broadcasts are used,
    // and only if the radio app itself declares a media-button receiver. Nothing is faked.
    // Steering-wheel keys: only reaches us if the unit sends standard media keys. Logged for the Key test.
    // Welcome animation (off by default) and dock icon size. Cheap: drawn for ~7 s only, auto-skips on a slow unit.
    boolean welcomeOn=false;String userName="";int dockSize=0;long welcomeStart=0L;long screenOffAt=0L;BroadcastReceiver screenRx;
    void loadWelcomePrefs(){android.content.SharedPreferences sp=getPreferences(MODE_PRIVATE);welcomeOn=sp.getBoolean("welcomeOn",false);userName=sp.getString("userName","");dockSize=sp.getInt("dockSize",0);if(dockSize<0||dockSize>2)dockSize=0;}
    void startWelcome(){if(welcomeOn){welcomeStart=android.os.SystemClock.uptimeMillis();if(dashboard!=null)dashboard.invalidate();}}
    void registerScreenWake(){
        try{
            screenRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
                if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){screenOffAt=android.os.SystemClock.elapsedRealtime();}
                else if(screenOffAt>0&&android.os.SystemClock.elapsedRealtime()-screenOffAt>=300000L){startWelcome();}
            }};
            IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenRx,f);
        }catch(Throwable e){android.util.Log.w("ZomaOS","Screen receiver failed",e);}
    }
    void chooseDockSize(){
        final String[] choices={"Normal","Large","Huge"};
        new AlertDialog.Builder(this).setTitle(tr("Dock icon size")).setSingleChoiceItems(choices,dockSize,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){
            dockSize=n;getPreferences(MODE_PRIVATE).edit().putInt("dockSize",n).apply();if(dashboard!=null)dashboard.invalidate();d.dismiss();returnToSettings();
        }}).setNegativeButton(tr("Cancel"),null).show();
    }
    void chooseUserName(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setText(userName);
        new AlertDialog.Builder(this).setTitle(tr("Your name")).setView(input).setPositiveButton(tr("Save"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            String v=input.getText().toString().trim();if(v.length()>20)v=v.substring(0,20);userName=v;getPreferences(MODE_PRIVATE).edit().putString("userName",v).apply();returnToSettings();
        }}).setNegativeButton(tr("Cancel"),null).show();
    }
    long lastUnitKeyAt=0L;void nextUsbOrLocal(int d){if(usbMode)nextUsb(d);else if(localMode)nextLocal(d);}
    BroadcastReceiver wheelRx,factoryKeyRx;String lastKeyLog="No key received yet";
    void logKey(String src,int code,int action){lastKeyLog=src+": "+KeyEvent.keyCodeToString(code)+" ("+code+") "+(action==KeyEvent.ACTION_DOWN?"down":"up")+" at "+new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());}
    void wheelAct(int code){
        if(code==KeyEvent.KEYCODE_MEDIA_NEXT){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else if(fmMode())radioKey(code);}
        else if(code==KeyEvent.KEYCODE_MEDIA_PREVIOUS){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else if(fmMode())radioKey(code);}
        else if(code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(localPlayer!=null)togglePlayback();else if(fmMode())radioKey(code);}
    }
    void registerWheelKeys(){
        try{
            wheelRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
                KeyEvent k=(KeyEvent)i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if(k==null)return;
                logKey("Broadcast",k.getKeyCode(),k.getAction());
                if(k.getAction()==KeyEvent.ACTION_DOWN)wheelAct(k.getKeyCode());
                abortBroadcast();
            }};
            IntentFilter f=new IntentFilter(Intent.ACTION_MEDIA_BUTTON);f.setPriority(2147483647);
            registerReceiver(wheelRx,f);
            // The unit's own key broadcasts (names and extras read from RadioService.apk). Logged for the Key test; passive, no polling.
            factoryKeyRx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
                String a=i.getAction();if(a==null)return;
                String t=new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                if("com.spd.system.key".equals(a)){int kv=i.getIntExtra("value",-1),ks=i.getIntExtra("state",-1);lastKeyLog="Unit key: value "+kv+" state "+ks+" at "+t;
                    // The factory radio acts only when (state & 0xff)==2 (read from RadioService.apk), so that is the real press. Act on it only, once per 350 ms.
                    long nw=android.os.SystemClock.uptimeMillis();
                    if((ks&0xff)==2&&(kv==19||kv==20)&&nw-lastUnitKeyAt>350L){
                        // Radio active: the factory radio engine handles 19/20 itself, so do nothing here (no double seek).
                        if(usbMode||localMode){lastUnitKeyAt=nw;if(kv==20)nextUsbOrLocal(1);else nextUsbOrLocal(-1);}
                    }
                }
                else if("com.spd.home.key".equals(a))lastKeyLog="Unit home key: source "+i.getIntExtra("source",-1)+" value "+i.getIntExtra("value",-1)+" at "+t;
                else if("com.spd.media.command".equals(a)){int cm=i.getIntExtra("command",-1);lastKeyLog="Unit media command: "+cm+" at "+t;
                    if(cm==5){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);}else if(cm==4){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);}}
            }};
            IntentFilter f2=new IntentFilter("com.spd.system.key");f2.addAction("com.spd.home.key");f2.addAction("com.spd.media.command");
            registerReceiver(factoryKeyRx,f2);
        }catch(Throwable e){android.util.Log.w("ZomaOS","Wheel key receiver failed",e);}
    }
    void showKeyTest(){
        final AlertDialog d=new AlertDialog.Builder(this).setTitle("Button check").setMessage("Press steering-wheel or panel buttons now.\n\n"+lastKeyLog).setPositiveButton(tr("OK"),null).create();
        d.setOnKeyListener(new DialogInterface.OnKeyListener(){public boolean onKey(DialogInterface dd,int code,KeyEvent e){
            if(code==KeyEvent.KEYCODE_BACK)return false;
            logKey("Key",code,e.getAction());d.setMessage("Press steering-wheel or panel buttons now.\n\n"+lastKeyLog);
            if(e.getAction()==KeyEvent.ACTION_DOWN&&code!=KeyEvent.KEYCODE_VOLUME_UP&&code!=KeyEvent.KEYCODE_VOLUME_DOWN)wheelAct(code);
            return code!=KeyEvent.KEYCODE_VOLUME_UP&&code!=KeyEvent.KEYCODE_VOLUME_DOWN;}});
        d.show();
    }
    final java.util.HashMap<String,android.graphics.drawable.Drawable> iconCache=new java.util.HashMap<String,android.graphics.drawable.Drawable>();
    android.graphics.drawable.Drawable cachedIcon(String pkg){android.graphics.drawable.Drawable d=iconCache.get(pkg);if(d==null){try{d=getPackageManager().getApplicationIcon(pkg);iconCache.put(pkg,d);}catch(Throwable e){return null;}}return d;}
    long musicAt=0L;boolean musicOn=false;
    boolean musicActive(){long n=android.os.SystemClock.uptimeMillis();if(n-musicAt>2000L){musicAt=n;musicOn=audioManager!=null&&audioManager.isMusicActive();}return musicOn;}
    boolean fmMode(){return !usbMode&&!localMode&&"fm".equals(getPreferences(MODE_PRIVATE).getString("last_audio_source",""));}
    // Factory radio engine: the radio app talks to the unit's own service via com.spd.spdservices.SpdManager (found by reading the radio APK).
    // Message codes read from that APK: 10005 next seek, 10006 previous seek. Reflection only, run on a button press, never in the background.
    Object spdSvc;String spdErr="";
    Object spdService(){
        if(spdSvc!=null)return spdSvc;
        try{
            Class<?> k=Class.forName("com.spd.spdservices.SpdManager");
            Object m=k.getConstructor(Context.class).newInstance(this);
            java.lang.reflect.Method g=null;
            for(java.lang.reflect.Method mm:k.getMethods())if("getAppService".equals(mm.getName()))g=mm;
            if(g==null){spdErr="SpdManager has no getAppService";return null;}
            // The radio app calls getAppService("RadioPlayer"); read from its code.
            Object svc=g.getParameterTypes().length==0?g.invoke(m):g.invoke(m,"RadioPlayer");
            if(svc==null){
                // The radio app starts the engine with this action and tries again; do the same.
                try{startService(new Intent("com.spd.action.radio"));}catch(Throwable ignored){}
                spdErr="radio service was starting, try again";
            }else spdSvc=svc;
        }catch(Throwable e){spdErr=String.valueOf(e);}
        return spdSvc;
    }
    java.lang.reflect.Method spdSendMethod(Object svc){
        java.lang.reflect.Method best=null;
        for(java.lang.reflect.Method mm:svc.getClass().getMethods())if("sendMsg".equals(mm.getName())){
            Class<?>[] pt=mm.getParameterTypes();
            if(pt.length==4&&pt[3]==String.class)return mm;
            if(pt.length==3&&best==null)best=mm;
        }
        return best;
    }
    boolean radioSend(int what,int arg1,int arg2){
        try{
            Object svc=spdService();if(svc==null)return false;
            java.lang.reflect.Method mm=spdSendMethod(svc);if(mm==null){spdErr="no sendMsg method";return false;}
            Object[] args=mm.getParameterTypes().length==4?new Object[]{Integer.valueOf(what),Integer.valueOf(arg1),Integer.valueOf(arg2),"ZomaOS"}:new Object[]{Integer.valueOf(what),Integer.valueOf(arg1),Integer.valueOf(arg2)};
            mm.invoke(svc,args);spdErr="sent "+what;return true;
        }catch(Throwable e){spdErr=String.valueOf(e);return false;}
    }
    boolean radioKey(int code){
        if(code==KeyEvent.KEYCODE_MEDIA_NEXT&&radioSend(10005,0,0))return true;
        if(code==KeyEvent.KEYCODE_MEDIA_PREVIOUS&&radioSend(10006,0,0))return true;
        // RadioService.apk listens for this broadcast itself (command 5 = next station, 4 = previous), read from its code.
        if(code==KeyEvent.KEYCODE_MEDIA_NEXT||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS){
            try{Intent b=new Intent("com.spd.media.command");b.putExtra("command",code==KeyEvent.KEYCODE_MEDIA_NEXT?5:4);sendBroadcast(b);spdErr="sent media.command";return true;}catch(Throwable e){spdErr=String.valueOf(e);}
        }
        try{
            Intent probe=new Intent(Intent.ACTION_MEDIA_BUTTON);probe.setPackage("com.spd.radio");
            List<ResolveInfo> rx=getPackageManager().queryBroadcastReceivers(probe,0);
            if(rx==null||rx.isEmpty()){toast("The factory radio does not accept media keys - use its own screen");return false;}
            for(ResolveInfo r:rx){
                for(int act=0;act<2;act++){
                    Intent i=new Intent(Intent.ACTION_MEDIA_BUTTON);
                    i.setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));
                    i.putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(act==0?KeyEvent.ACTION_DOWN:KeyEvent.ACTION_UP,code));
                    sendBroadcast(i);
                }
            }
            return true;
        }catch(Throwable e){android.util.Log.w("ZomaOS","Radio key failed",e);return false;}
    }
    // Writes what the unit's factory classes expose (names only, nothing private) so the radio and wheel keys can be wired up.
    String factoryReport(){
        StringBuilder sb=new StringBuilder("ZomaOS factory report\nAndroid API "+android.os.Build.VERSION.SDK_INT+" "+android.os.Build.MODEL+" "+android.os.Build.DEVICE+"\n");
        String[] names={"com.spd.spdservices.SpdManager","com.spd.app.service.IAppService","com.spd.app.service.IAppService$Stub","com.spd.app.service.IAppCallBack","com.spd.app.service.IAppCallBack$Stub","com.spd.carinfoservice.CarModel"};
        for(String n:names){
            sb.append("\n== ").append(n).append("\n");
            try{
                Class<?> k=Class.forName(n);sb.append("loader: ").append(k.getClassLoader()).append("\nsuper: ").append(k.getSuperclass()).append("\n");
                for(Class<?> i:k.getInterfaces())sb.append("implements: ").append(i.getName()).append("\n");
                for(java.lang.reflect.Field f:k.getDeclaredFields()){
                    sb.append("field ").append(f.getType().getSimpleName()).append(" ").append(f.getName());
                    try{f.setAccessible(true);if(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&(f.getType()==int.class||f.getType()==String.class))sb.append(" = ").append(f.get(null));}catch(Throwable e){}
                    sb.append("\n");
                }
                for(java.lang.reflect.Method m:k.getDeclaredMethods()){
                    sb.append("method ").append(m.getReturnType().getSimpleName()).append(" ").append(m.getName()).append("(");
                    Class<?>[] pt=m.getParameterTypes();for(int q=0;q<pt.length;q++){if(q>0)sb.append(",");sb.append(pt[q].getSimpleName());}
                    sb.append(")\n");
                }
            }catch(Throwable e){sb.append("not available: ").append(e).append("\n");}
        }
        try{
            PackageManager pm=getPackageManager();
            sb.append("\n== services for action com.spd.action.radio\n");
            List<ResolveInfo> sv=pm.queryIntentServices(new Intent("com.spd.action.radio"),0);
            if(sv!=null)for(ResolveInfo r:sv)sb.append(r.serviceInfo.packageName).append("/").append(r.serviceInfo.name).append("\n");
            sb.append("\n== packages\n");
            for(ApplicationInfo ai:pm.getInstalledApplications(0)){
                String pn=ai.packageName.toLowerCase();
                if(pn.indexOf("spd")>=0||pn.indexOf("geely")>=0||pn.indexOf("carinfo")>=0||pn.indexOf("mcu")>=0||pn.indexOf("canbus")>=0||pn.indexOf("music")>=0||pn.indexOf("media")>=0)sb.append(ai.packageName).append("  ").append(ai.sourceDir).append("\n");
            }
        }catch(Throwable e){sb.append("package scan failed ").append(e).append("\n");}
        return sb.toString();
    }
    void saveFactoryReport(){
        try{
            String rep=factoryReport();
            java.io.File dir=new java.io.File(android.os.Environment.getExternalStorageDirectory(),"ZomaOS");dir.mkdirs();
            java.io.File out=new java.io.File(dir,"zomaos-factory-report.txt");
            java.io.FileOutputStream fo=new java.io.FileOutputStream(out);fo.write(rep.getBytes("UTF-8"));fo.close();
            toast("Saved: "+out.getAbsolutePath());
        }catch(Throwable e){toast("Could not save report: "+e);}
    }
    void showRadioDiagnostics(){
        StringBuilder sb=new StringBuilder();
        PackageManager pm=getPackageManager();
        sb.append("Last audio source from ZomaOS: ").append(getPreferences(MODE_PRIVATE).getString("last_audio_source","none")).append("\n");
        sb.append("Android sees music playing: ").append(audioManager!=null&&audioManager.isMusicActive()?"yes":"no").append("\n");
        try{
            Object svc=spdService();
            sb.append("Factory radio service: ").append(svc!=null?"connected":"not available").append("\n");
            if(svc!=null){for(java.lang.reflect.Method mm:svc.getClass().getMethods())if("sendMsg".equals(mm.getName()))sb.append("  sendMsg params: ").append(mm.getParameterTypes().length).append("\n");}
            else{sb.append("  ").append(spdErr).append("\n");try{for(java.lang.reflect.Method mm:Class.forName("com.spd.spdservices.SpdManager").getDeclaredMethods()){sb.append("  SpdManager.").append(mm.getName()).append("(").append(mm.getParameterTypes().length).append(")\n");}}catch(Throwable ignored){}}
        }catch(Throwable e){sb.append("Service check failed\n");}
        try{
            PackageInfo pi=pm.getPackageInfo("com.spd.radio",PackageManager.GET_RECEIVERS|PackageManager.GET_SERVICES);
            sb.append("Radio app: com.spd.radio v").append(pi.versionName).append("\n");
            Intent probe=new Intent(Intent.ACTION_MEDIA_BUTTON);probe.setPackage("com.spd.radio");
            List<ResolveInfo> rx=pm.queryBroadcastReceivers(probe,0);
            sb.append("Media-key receivers: ").append(rx==null?0:rx.size()).append("\n");
            sb.append("Receivers: ").append(pi.receivers==null?0:pi.receivers.length).append("\n");
            if(pi.receivers!=null)for(int n=0;n<pi.receivers.length&&n<8;n++)sb.append("  ").append(pi.receivers[n].name).append(pi.receivers[n].exported?" (open)":"").append("\n");
            sb.append("Services: ").append(pi.services==null?0:pi.services.length).append("\n");
            if(pi.services!=null)for(int n=0;n<pi.services.length&&n<8;n++)sb.append("  ").append(pi.services[n].name).append(pi.services[n].exported?" (open)":"").append("\n");
        }catch(Throwable e){sb.append("Radio app com.spd.radio not found\n");}
        try{
            sb.append("Factory packages:\n");int shownPk=0;
            for(ApplicationInfo ai:pm.getInstalledApplications(0)){
                String pn=ai.packageName.toLowerCase();
                if((pn.indexOf("spd")>=0||pn.indexOf("geely")>=0||pn.indexOf("carinfo")>=0||pn.indexOf("mcu")>=0||pn.indexOf("canbus")>=0)&&shownPk<14){shownPk++;sb.append("  ").append(ai.packageName).append("\n    ").append(ai.sourceDir).append("\n");}
            }
            if(shownPk==0)sb.append("  none found\n");
        }catch(Throwable e){sb.append("Package list failed\n");}
        new AlertDialog.Builder(this).setTitle("Radio info").setMessage(sb.toString())
            .setPositiveButton(tr("OK"),null).setNegativeButton("Save report",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){saveFactoryReport();}}).setNeutralButton("Try NEXT station",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){boolean ok=radioSend(10005,0,0);toast("Radio next: "+(ok?"sent":"failed")+" - "+spdErr);}}).show();
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
        new AlertDialog.Builder(this).setTitle("USB MUSIC  •  "+labels.length+" files").setItems(trA(labels),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){playUsb(i);}})
            .setNeutralButton("Choose folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){chooseUsbRoot();}}).setNegativeButton(tr("Close"),null).show();
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
            new AlertDialog.Builder(this).setTitle(tr("Choose USB drive"))
                .setItems(trA(labels),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){browseFolder(choices[n]);}})
                .setNegativeButton(tr("Cancel"),null).show();
            return;
        }
        // Fallback allows manual browsing when a USB contains no recognized audio yet.
        final String[] roots={"/mnt/usb","/mnt/usb_storage","/storage","/mnt/sdcard"};
        final ArrayList<File> available=new ArrayList<File>();
        for(String root:roots){File f=new File(root);if(f.isDirectory()&&f.canRead())available.add(f);}
        if(available.isEmpty()){toast("No readable USB storage found");return;}
        final String[] labels=new String[available.size()];
        for(int i=0;i<labels.length;i++)labels[i]=available.get(i).getAbsolutePath();
        new AlertDialog.Builder(this).setTitle(tr("USB not detected - browse manually"))
            .setItems(trA(labels),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){browseFolder(available.get(n));}})
            .setNegativeButton(tr("Cancel"),null).show();
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
            .setItems(trA(names),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){playLocal(which);}})
            .setNeutralButton("Factory Music",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){openMusic();}})
            .setNegativeButton(tr("Close"),null).show();
    }
    void musicHub(){
        final String[] choices={"USB MUSIC - browse flash drive", "ZOMA MUSIC - indexed library", "BLUETOOTH MUSIC - factory player", "GEELY MUSIC - original player", "FM RADIO - original tuner", "ANDROID AUTO - OpenAuto", "BLUETOOTH / PHONE settings"};
        new AlertDialog.Builder(this).setTitle(tr("ZomaOS Entertainment Hub")).setItems(trA(choices),new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int n){if(n==0)openRememberedUsb();else if(n==1)openLocalMusic();else if(n==2)openFactoryBluetooth();else if(n==3)openMusic();else if(n==4)openRadio();else if(n==5)openAndroidAuto();else factoryBluetoothOptions();}
        }).setNegativeButton(tr("Close"),null).show();
    }
    // Best-effort media keys. Steering wheel may be routed privately by the factory MCU.
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){logKey("Key",keyCode,KeyEvent.ACTION_DOWN);
        if(keyCode==KeyEvent.KEYCODE_MEDIA_NEXT){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else return super.onKeyDown(keyCode,event);return true;}
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PREVIOUS){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else return super.onKeyDown(keyCode,event);return true;}
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||keyCode==KeyEvent.KEYCODE_HEADSETHOOK){if(localPlayer!=null){togglePlayback();return true;}}
        return super.onKeyDown(keyCode,event);
    }
    void chooseAppearance(){final String[] choices={"Electric Night", "Pearl Day", "Carbon Sport"};new AlertDialog.Builder(this).setTitle(tr("Accent color")).setSingleChoiceItems(choices,appearance,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){appearance=n;getPreferences(MODE_PRIVATE).edit().putInt("appearance",n).apply();loadN14Skin();dashboard.invalidate();d.dismiss();returnToSettings();}}).setNegativeButton(tr("Cancel"),null).show();}
    void chooseClockFormat(){final String[] choices={"24 hour", "12 hour AM/PM"};new AlertDialog.Builder(this).setTitle(tr("Clock format")).setSingleChoiceItems(choices,clock12?1:0,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){clock12=n==1;getPreferences(MODE_PRIVATE).edit().putBoolean("clock12",clock12).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton(tr("Cancel"),null).show();}
    String clockText(){return new SimpleDateFormat(clock12?"hh:mm a":"HH:mm",Locale.ENGLISH).format(new Date());}
    // Category-first settings for 800x480 resistive displays (API 16 compatible).
    void showZomaSettings(){
        final String[][] g={{"Display","Theme, clock, chips, gauges"},{"Map & GPS","Map app, phone GPS, offline map"},{"Vehicle & OBD","Data source, fault codes, trip"},
            {"Music & Media","USB folder, player"},{"Apps & Shortcuts","Bottom bar, app center"},{"Maintenance","Oil, odometer"},{"System","Android, backup, about"}};
        final int cyan=Color.rgb(45,227,255);
        final Dialog dlg=new Dialog(this);dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.argb(245,8,18,28));bg.setCornerRadius(dp(14));bg.setStroke(dp(1),Color.argb(120,45,227,255));
        root.setBackgroundDrawable(bg);root.setPadding(dp(14),dp(10),dp(14),dp(12));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt=new TextView(this);tt.setText(tr("ZomaOS V15.9  /  SETTINGS"));tt.setTextColor(Color.WHITE);tt.setTextSize(21);tt.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(tt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView close=new TextView(this);close.setText(tr("CLOSE"));close.setTextColor(cyan);close.setTextSize(15);close.setTypeface(Typeface.DEFAULT_BOLD);close.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable cb=new android.graphics.drawable.GradientDrawable();cb.setColor(Color.argb(60,45,227,255));cb.setCornerRadius(dp(10));close.setBackgroundDrawable(cb);close.setPadding(dp(18),dp(8),dp(18),dp(8));
        close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dlg.dismiss();}});head.addView(close);
        root.addView(head,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        int[] per={3,2,2};int k=0;
        for(int rw=0;rw<3;rw++){
            LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
            for(int c=0;c<per[rw]&&k<g.length;c++,k++){
                final int idx=k;
                LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(14),dp(8),dp(14),dp(8));
                android.graphics.drawable.GradientDrawable tg=new android.graphics.drawable.GradientDrawable();tg.setColor(Color.argb(255,22,36,48));tg.setCornerRadius(dp(12));tg.setStroke(dp(1),Color.argb(90,45,227,255));tile.setBackgroundDrawable(tg);
                TextView a=new TextView(this);a.setText(tr(g[k][0]));a.setTextColor(Color.WHITE);a.setTextSize(18);a.setTypeface(Typeface.DEFAULT_BOLD);tile.addView(a);
                TextView b=new TextView(this);b.setText(tr(g[k][1]));b.setTextColor(Color.rgb(125,145,160));b.setTextSize(12);tile.addView(b);
                tile.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dlg.dismiss();showZomaSettingsCategory(idx);}});
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(72),1f);lp.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(tile,lp);
            }
            LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);ll.setMargins(0,dp(rw==0?10:0),0,0);root.addView(line,ll);
        }
        dlg.setContentView(root);
        Window w=dlg.getWindow();w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        w.setLayout(Math.min(dm.widthPixels-dp(20),dp(640)),ViewGroup.LayoutParams.WRAP_CONTENT);
        trackOverlay(dlg);dlg.show();
    }
    // ---- Unified settings screens: every screen uses section headers + "Label: value" rows. ----
    // Header = null action. DISABLED = shown grayed out and not tappable. Toggles flip in place and re-open the screen.
    final Runnable DISABLED=new Runnable(){public void run(){}};
    Runnable settingsReturn=null;
    void returnToSettings(){Runnable r=settingsReturn;settingsReturn=null;if(r!=null)r.run();}
    void sHead(ArrayList<String> r,ArrayList<Runnable> a,String t){r.add(tr(t));a.add(null);}
    void sRow(ArrayList<String> r,ArrayList<Runnable> a,String t,Runnable x){r.add(tr(t));a.add(x);}
    volatile int overlays=0;
    void trackOverlay(Dialog d){overlays++;d.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface x){if(overlays>0)overlays--;}});}
    // Last error log (crash or map/download error), shown in System > Last error.
    File errFile(){return new File(getFilesDir(),"last_error.txt");}
    void logError(String where,Throwable t){
        try{java.io.StringWriter sw=new java.io.StringWriter();java.io.PrintWriter pw=new java.io.PrintWriter(sw);pw.println(new java.util.Date()+"  "+where);if(t!=null)t.printStackTrace(pw);pw.flush();
            String txt=sw.toString();if(txt.length()>6000)txt=txt.substring(0,6000);
            java.io.FileOutputStream o=new java.io.FileOutputStream(errFile());o.write(txt.getBytes("UTF-8"));o.close();}catch(Throwable ignored){}
    }
    void installCrashLog(){
        final Thread.UncaughtExceptionHandler prev=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread th,Throwable ex){logError("CRASH in "+th.getName(),ex);if(prev!=null)prev.uncaughtException(th,ex);}});
    }
    void showLastError(){
        String txt="";try{File f=errFile();if(f.isFile()){java.io.FileInputStream in=new java.io.FileInputStream(f);byte[] b=new byte[(int)Math.min(f.length(),8000)];int n=in.read(b);in.close();txt=new String(b,0,Math.max(n,0),"UTF-8");}}catch(Throwable ignored){}
        final String shown=txt.length()>0?txt:tr("No error recorded");
        ScrollView sv=new ScrollView(this);TextView tv=new TextView(this);tv.setText(shown);tv.setTextSize(11);tv.setPadding(dp(12),dp(8),dp(12),dp(8));sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(tr("Last error")).setView(sv).setPositiveButton(tr("OK"),null)
          .setNegativeButton(tr("Clear"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){errFile().delete();}}).show();
    }
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    // Settings screens: dark glass card, cyan section headers, title + grey hint, value or ON/OFF pill on the right. Same colours as the chips.
    void showSectioned(final String title,final ArrayList<String> rows,final ArrayList<Runnable> acts){
        final int cyan=Color.rgb(45,227,255),gold=Color.rgb(231,197,139);
        final Dialog dlg=new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.argb(245,8,18,28));bg.setCornerRadius(dp(14));bg.setStroke(dp(1),Color.argb(120,45,227,255));
        root.setBackgroundDrawable(bg);root.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt=new TextView(this);tt.setText(title);tt.setTextColor(Color.WHITE);tt.setTextSize(21);tt.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(tt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView back=new TextView(this);back.setText(tr("BACK"));back.setTextColor(cyan);back.setTextSize(15);back.setTypeface(Typeface.DEFAULT_BOLD);back.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bb=new android.graphics.drawable.GradientDrawable();bb.setColor(Color.argb(60,45,227,255));bb.setCornerRadius(dp(10));back.setBackgroundDrawable(bb);back.setPadding(dp(18),dp(8),dp(18),dp(8));
        back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dlg.dismiss();showZomaSettings();}});
        head.addView(back);
        root.addView(head,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        View line=new View(this);line.setBackgroundColor(Color.argb(90,45,227,255));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1));lp.setMargins(0,dp(8),0,dp(4));root.addView(line,lp);
        ListView lv=new ListView(this);lv.setDivider(null);lv.setDividerHeight(0);lv.setCacheColorHint(0);lv.setSelector(new android.graphics.drawable.ColorDrawable(Color.argb(40,45,227,255)));
        ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,rows){
            @Override public boolean areAllItemsEnabled(){return false;}
            @Override public boolean isEnabled(int i){return acts.get(i)!=null&&acts.get(i)!=DISABLED;}
            @Override public View getView(int i,View v,ViewGroup parent){
                String raw=rows.get(i);boolean header=acts.get(i)==null,off=acts.get(i)==DISABLED;
                LinearLayout row=new LinearLayout(MainActivity.this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
                if(header){
                    TextView h=new TextView(MainActivity.this);h.setText(raw);h.setTextColor(gold);h.setTextSize(12.5f);h.setTypeface(Typeface.DEFAULT_BOLD);
                    row.setPadding(dp(6),dp(12),dp(6),dp(2));row.addView(h);return row;}
                String first=raw,sub="";int nl=raw.indexOf('\n');if(nl>=0){first=raw.substring(0,nl);sub=raw.substring(nl+1);}
                String label=first,val="";int ci=first.indexOf(": ");if(ci>0){label=first.substring(0,ci);val=first.substring(ci+2);}
                LinearLayout col=new LinearLayout(MainActivity.this);col.setOrientation(LinearLayout.VERTICAL);
                TextView l=new TextView(MainActivity.this);l.setText(label);l.setTextSize(17);l.setTextColor(off?Color.rgb(110,120,130):Color.WHITE);col.addView(l);
                if(sub.length()>0){TextView sv=new TextView(MainActivity.this);sv.setText(sub);sv.setTextSize(12);sv.setTextColor(Color.rgb(125,145,160));col.addView(sv);}
                row.addView(col,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                row.setPadding(dp(10),dp(9),dp(10),dp(9));
                android.graphics.drawable.GradientDrawable rb=new android.graphics.drawable.GradientDrawable();rb.setColor(Color.argb(off?25:55,30,50,66));rb.setCornerRadius(dp(10));
                LinearLayout wrap=new LinearLayout(MainActivity.this);wrap.setPadding(0,dp(3),0,dp(3));row.setBackgroundDrawable(rb);
                if(val.length()>0){
                    String u=val.toUpperCase(Locale.ENGLISH);boolean sw=u.equals("ON")||u.equals("OFF");
                    TextView pv=new TextView(MainActivity.this);pv.setText(val);pv.setTextSize(sw?13:14);pv.setTypeface(Typeface.DEFAULT_BOLD);pv.setGravity(Gravity.CENTER);
                    pv.setSingleLine(true);pv.setEllipsize(android.text.TextUtils.TruncateAt.END);pv.setMaxWidth(dp(190));
                    android.graphics.drawable.GradientDrawable pg=new android.graphics.drawable.GradientDrawable();pg.setCornerRadius(dp(12));
                    if(sw){boolean on=u.equals("ON");pg.setColor(on?Color.argb(200,20,160,110):Color.argb(120,90,100,110));pv.setTextColor(Color.WHITE);pv.setMinWidth(dp(52));}
                    else{pg.setColor(Color.argb(55,45,227,255));pv.setTextColor(cyan);}
                    pv.setBackgroundDrawable(pg);pv.setPadding(dp(12),dp(5),dp(12),dp(5));
                    row.addView(pv);
                }
                wrap.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                return wrap;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> pr,View v,int item,long id){Runnable x=acts.get(item);if(x!=null&&x!=DISABLED){dlg.dismiss();x.run();}}});
        root.addView(lv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        dlg.setContentView(root);
        Window w=dlg.getWindow();w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        w.setLayout(Math.min(dm.widthPixels-dp(20),dp(640)),Math.min(dm.heightPixels-dp(16),dp(420)));
        trackOverlay(dlg);dlg.show();
    }
    String gaugesLabel(){return premiumGaugeMode==0?"Show":premiumGaugeMode==1?"When connected":"Hide";}
    // THEME / SCREEN / PERFORMANCE. Smoothness is grayed out when the animated theme is off.
    void showDisplaySettings(){
        final String[] dayNames={"Day","Night","Auto (sunrise - sunset)"};
        final String[] accentNames={"Electric Night","Pearl Day","Carbon Sport"};
        final Runnable self=new Runnable(){public void run(){showDisplaySettings();}};
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"THEME");
        sRow(r,a,"Accent color: "+accentNames[appearance<0||appearance>2?0:appearance],new Runnable(){public void run(){settingsReturn=self;chooseAppearance();}});
        sHead(r,a,"SCREEN");
        sRow(r,a,"Language: "+(ar?"\u0627\u0644\u0639\u0631\u0628\u064a\u0629":"English")+"\nEnglish or Arabic",new Runnable(){public void run(){toggleLanguage();}});
        sRow(r,a,"Clock format: "+(clock12?"12 hour":"24 hour"),new Runnable(){public void run(){clock12=!clock12;getPreferences(MODE_PRIVATE).edit().putBoolean("clock12",clock12).apply();dashboard.invalidate();showDisplaySettings();}});
        sRow(r,a,"Weather chip: "+(infoChips?"ON":"OFF")+"\nWeather from the phone, beside the clock",new Runnable(){public void run(){infoChips=!infoChips;getPreferences(MODE_PRIVATE).edit().putBoolean("infoChips",infoChips).apply();dashboard.invalidate();self.run();}});
        sRow(r,a,"Prayer times: "+(prayerOn?"ON":"OFF")+"\nNext prayer countdown beside the clock. Off by default",new Runnable(){public void run(){prayerOn=!prayerOn;getPreferences(MODE_PRIVATE).edit().putBoolean("prayerOn",prayerOn).apply();dashboard.invalidate();self.run();}});
        sRow(r,a,"Gauges: "+gaugesLabel()+"\nSpeed and RPM gauges on the home screen",new Runnable(){public void run(){settingsReturn=self;choosePremiumGaugeMode();}});
        sRow(r,a,"Dock icon size: "+(dockSize==0?"Normal":dockSize==1?"Large":"Huge")+"\nBigger icons and text on the bottom dock",new Runnable(){public void run(){settingsReturn=self;chooseDockSize();}});
        sRow(r,a,"Welcome animation: "+(welcomeOn?"On":"Off")+"\nShort start-up screen (7 s, tap to skip). All themes",new Runnable(){public void run(){welcomeOn=!welcomeOn;getPreferences(MODE_PRIVATE).edit().putBoolean("welcomeOn",welcomeOn).apply();if(welcomeOn)startWelcome();else welcomeStart=0L;showDisplaySettings();}});
        sRow(r,a,"Your name: "+(userName.length()==0?"not set":userName)+"\nShown in the welcome greeting",new Runnable(){public void run(){settingsReturn=self;chooseUserName();}});
        showSectioned(tr("Display"),r,a);
    }
    void showNavSettings(){
        final Runnable self=new Runnable(){public void run(){showNavSettings();}};
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"NAVIGATION APP");
        sRow(r,a,"Map button: "+navLabel()+"\nThe app the arrow button on the left opens",new Runnable(){public void run(){chooseNavApp(new Runnable(){public void run(){showAppsSettings();}});}});
        sHead(r,a,"PHONE GPS");
        sRow(r,a,"Phone GPS: "+gpsLabel()+"\nUse the phone as GPS source for the unit",new Runnable(){public void run(){gpsDialog(self);}});
        showSectioned(tr("Map & GPS"),r,a);
    }
    void showMusicSettings(){
        final Runnable self=new Runnable(){public void run(){showMusicSettings();}};
        String player=getPreferences(MODE_PRIVATE).getString("musicPackage",null);
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"SOURCES");
        sRow(r,a,"USB music folder\nChoose the flash drive or folder to play from",new Runnable(){public void run(){chooseUsbRoot();}});
        sRow(r,a,"Music player: "+(player==null?"Not set":player)+"\nThe app the Music button opens",new Runnable(){public void run(){chooseAnyMusicApp();}});
        showSectioned(tr("Music & Media"),r,a);
    }
    void showVehicleSettings(){
        final Runnable self=new Runnable(){public void run(){showVehicleSettings();}};
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"CONNECTION");
        sRow(r,a,"Data source\nDemo, Bluetooth ELM327, or disconnect",new Runnable(){public void run(){showMode();}});
        sRow(r,a,"Read fault codes\nStored codes with offline explanations (read only)",new Runnable(){public void run(){askDtcs();}});
        sRow(r,a,"Engine temperature alert: "+(getPreferences(MODE_PRIVATE).getBoolean("obd_alerts",true)?"ON":"OFF")+"\nWarns at 105 C or more",new Runnable(){public void run(){android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);p.edit().putBoolean("obd_alerts",!p.getBoolean("obd_alerts",true)).apply();showVehicleSettings();}});
        sRow(r,a,"Read several values per request: "+(getPreferences(MODE_PRIVATE).getBoolean("obdBatch",true)?"ON":"OFF")+"\nFaster OBD. Turn off if your adapter stops answering",new Runnable(){public void run(){android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);boolean on=!p.getBoolean("obdBatch",true);android.content.SharedPreferences.Editor e=p.edit();e.putBoolean("obdBatch",on);if(on){for(String k:new ArrayList<String>(p.getAll().keySet()))if(k.startsWith("obdNoBatch_"))e.remove(k);}e.commit();toast(on?"Takes effect on next connect":"Single-value reads on next connect");showVehicleSettings();}});
        sHead(r,a,"TRIP");
        sRow(r,a,"Reset trip",new Runnable(){public void run(){tripKm=0;dashboard.invalidate();toast("Trip reset");showVehicleSettings();}});
        showSectioned(tr("Vehicle & OBD"),r,a);
    }
    void showMaintenanceSettings(){
        final android.content.SharedPreferences pref=getPreferences(MODE_PRIVATE);
        final int interval=pref.getInt("oil_interval",7000);
        boolean oilOn=pref.getBoolean("oil_visible",true);
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"OIL");
        sRow(r,a,"Oil reminder on home screen: "+(oilOn?"Show":"Hide"),new Runnable(){public void run(){toggleOilReminder();showMaintenanceSettings();}});
        sRow(r,a,"Oil change interval: "+interval+" km\nTap to edit",new Runnable(){public void run(){maintenanceInput("Oil change interval (km)","oil_interval",interval);}});
        sRow(r,a,"Oil service actions\nMark oil changed, hide reminder",new Runnable(){public void run(){oilReminderActions();}});
        sHead(r,a,"OVERVIEW");
        sRow(r,a,"Maintenance center\nOdometer from OBD if the car reports it, else counted from driving",new Runnable(){public void run(){maintenanceCenter();}});
        showSectioned(tr("Maintenance"),r,a);
    }
    void showAppsSettings(){
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"SHORTCUTS (BOTTOM BAR)");
        for(int i=0;i<dockDefaults.length;i++){
            final int idx=i;
            sRow(r,a,"Shortcut "+(i+1)+": "+dockLabel(i),new Runnable(){public void run(){editDock(idx);}});
        }
        sHead(r,a,"APPS");
        sRow(r,a,"App center\nAll installed apps",new Runnable(){public void run(){showAppCenter();}});
        showSectioned(tr("Apps & Shortcuts"),r,a);
    }
    void restoreDefaultsDialog(){
        new AlertDialog.Builder(MainActivity.this).setTitle(tr("Restore defaults?"))
          .setPositiveButton(tr("Reset"),new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface x,int b){
              getPreferences(MODE_PRIVATE).edit().clear().apply();
              theme=0;showAnalog=true;centerMode=0;appearance=0;clock12=false;
              premiumFps=30;premiumGaugeMode=0;premiumDayMode=2;
              dashboard.invalidate();
            }
          }).setNegativeButton(tr("Cancel"),null).show();
    }
    void showAboutDialog(){
        String ver="";
        try{ver=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Throwable ignored){}
        new AlertDialog.Builder(this).setTitle(tr("About ZomaOS"))
          .setMessage("ZomaOS V15.9"+(ver.length()>0?" ("+ver+")":"")+"\nA themeable home screen for older Android head units.\n\nProject: github.com/hmakaslan-jpg/ZomaOS\nSupport the project: ko-fi.com/zomaos\n\nIndependent project, not affiliated with Geely or Google.")
          .setPositiveButton(tr("OK"),null).show();
    }
    void showSystemSettings(){
        ArrayList<String> r=new ArrayList<String>();ArrayList<Runnable> a=new ArrayList<Runnable>();
        sHead(r,a,"ANDROID");
        sRow(r,a,"Android settings",new Runnable(){public void run(){launch(new String[]{},"settings");}});
        sRow(r,a,"Change default launcher\nShow the launcher chooser again",new Runnable(){public void run(){showDefaultLauncherReset();}});
        sHead(r,a,"ZOMAOS");
        sRow(r,a,"About",new Runnable(){public void run(){showAboutDialog();}});
        sRow(r,a,"Back up settings\nSave to USB or unit storage",new Runnable(){public void run(){backupSettings();}});
        sRow(r,a,"Restore settings\nFrom ZomaOS-settings.txt on USB or storage",new Runnable(){public void run(){restoreSettings();}});
        sRow(r,a,"Last error\nShow or clear the last crash or map error",new Runnable(){public void run(){showLastError();}});
        sRow(r,a,"Restore ZomaOS defaults\nResets theme, gauges and display options",new Runnable(){public void run(){restoreDefaultsDialog();}});
        showSectioned(tr("System"),r,a);
    }
    void showZomaSettingsCategory(final int category){
        if(category==0)showDisplaySettings();
        else if(category==1)showNavSettings();
        else if(category==2)showVehicleSettings();
        else if(category==3)showMusicSettings();
        else if(category==4)showAppsSettings();
        else if(category==5)showMaintenanceSettings();
        else showSystemSettings();
    }
    // Android 4.1 controls the HOME default; only the system settings can
    // reliably clear the user's preferred launcher without privileged access.
    boolean isDefaultHome(){
        try{Intent h=new Intent(Intent.ACTION_MAIN);h.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri=getPackageManager().resolveActivity(h,PackageManager.MATCH_DEFAULT_ONLY);
            return ri!=null&&ri.activityInfo!=null&&getPackageName().equals(ri.activityInfo.packageName);
        }catch(Throwable t){return true;}
    }
    // One-time tip (never repeated): shown only when ZomaOS is not the default launcher.
    void maybeShowLauncherHint(){
        try{
            if(getPreferences(MODE_PRIVATE).getBoolean("launcherHintShown",false)||isDefaultHome())return;
            getPreferences(MODE_PRIVATE).edit().putBoolean("launcherHintShown",true).apply();
            toast(tr("Tip: press HOME and choose ZomaOS, then Always, to make it your main screen. Settings > Change default launcher explains how."));
        }catch(Throwable ignored){}
    }
    void showDefaultLauncherReset(){
        new AlertDialog.Builder(this).setTitle(tr("Change default launcher"))
          .setMessage(tr("Open ZomaOS app information, tap Clear defaults, then press HOME. Android will ask which launcher to use. Choose Just once if you want the question every time. This does not erase themes or music."))
          .setPositiveButton(tr("OPEN APP INFO"),new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog,int which){
                try{
                    Intent settings=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    settings.setData(Uri.parse("package:"+getPackageName()));
                    startActivity(settings);
                }catch(Throwable err){
                    try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));}
                    catch(Throwable ignored){toast("Open Android Settings > Apps > ZomaOS > Clear defaults");}
                }
            }
          }).setNegativeButton(tr("CANCEL"),null).show();
    }
    // One "Gauges" setting (premiumGaugeMode: 0 Show, 1 When connected, 2 Hide) drives both the animated and the static themes.
    boolean speedWidgetOn(){return premiumGaugeMode==0||(premiumGaugeMode==1&&(speed>=0||rpm>=0||simulated));}
    boolean premiumIsDay(){
        if(premiumDayMode==0)return true;
        if(premiumDayMode==1)return false;
        // Auto follows the same clock the screen shows (the unit's own zone). An explicit "autoTz" pref still overrides it.
        String tz=getPreferences(MODE_PRIVATE).getString("autoTz",null);
        Calendar cal=(tz==null||tz.length()==0)?Calendar.getInstance():Calendar.getInstance(java.util.TimeZone.getTimeZone(tz));
        int minutes=cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE);
        if(tz==null||tz.length()==0){
            // Auto by the sun when the phone has given a position; checked at most every 30 s (not per frame).
            final long up=android.os.SystemClock.uptimeMillis();
            if(dayAutoAt!=0L&&up-dayAutoAt<30000L)return dayAutoVal;
            boolean v=minutes>=6*60&&minutes<18*60+30;
            try{
                double la=GpsService.lastLat,lo=GpsService.lastLon;
                if(!Double.isNaN(la)&&!Double.isNaN(lo)&&!(la==0.0&&lo==0.0)){
                    double[] st=sunRiseSet(System.currentTimeMillis(),la,lo);
                    if(st!=null){double hh=minutes/60.0;v=hh>=st[0]&&hh<st[1];}
                }
            }catch(Throwable ignored){}
            dayAutoAt=up;dayAutoVal=v;return v;
        }
        return minutes>=6*60&&minutes<18*60+30;
    }
    // Sunrise and sunset in wall-clock hours for today, same zone handling as nextPrayer. Null near the poles.
    static double[] sunRiseSet(long now,double lat,double lon){
        TimeZone tzo=TimeZone.getDefault();double tz=tzo.getOffset(now)/3600000.0;boolean zoneFixed=false;
        if(Math.abs(tz-lon/15.0)>2.5){tz=Math.round(lon/15.0);zoneFixed=true;}
        Calendar cal=Calendar.getInstance(tzo);cal.setTimeInMillis(now);
        int y=cal.get(Calendar.YEAR),m=cal.get(Calendar.MONTH)+1,d=cal.get(Calendar.DAY_OF_MONTH);
        if(zoneFixed&&lat>21&&lat<32&&lon>24&&lon<37&&egyptDst(y,m,d))tz+=1;
        int Y=y,M=m;if(M<=2){Y-=1;M+=12;}double A=Math.floor(Y/100.0),B=2-A+Math.floor(A/4.0);
        double jd=Math.floor(365.25*(Y+4716))+Math.floor(30.6001*(M+1))+d+B-1524.5-lon/(15.0*24.0);
        double adj=tz-lon/15.0;
        double rise=sunAngleTime(jd,0.833,6.0/24.0,lat,true)+adj;
        double set=sunAngleTime(jd,0.833,18.0/24.0,lat,false)+adj;
        if(Double.isNaN(rise)||Double.isNaN(set)||set<=rise)return null;
        return new double[]{rise,set};
    }
    void choosePremiumGaugeMode(){
        final String[] modes={"Show","When connected (hide without OBD data)","Hide"};
        new AlertDialog.Builder(this).setTitle(tr("Gauges"))
          .setSingleChoiceItems(modes,premiumGaugeMode,new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int mode){
                premiumGaugeMode=mode;
                getPreferences(MODE_PRIVATE).edit().putInt("premiumGaugeMode",mode).apply();
                if(dashboard!=null)dashboard.invalidate();
                d.dismiss();returnToSettings();
            }
          }).setNegativeButton(tr("Cancel"),null).show();
    }
    // App center: names and icons are loaded ONCE when the screen opens and reused while scrolling (light on API 16).
    static class AppItem{ResolveInfo ri;String label;String pkg;android.graphics.drawable.Drawable icon;int color;int uses;android.graphics.drawable.Drawable tileBg;android.graphics.drawable.Drawable iconBg;}
    static final int[] APP_COLORS={Color.rgb(160,90,255),Color.rgb(255,140,50),Color.rgb(60,140,255),Color.rgb(60,210,110),Color.rgb(240,190,60),Color.rgb(255,90,120),Color.rgb(60,200,230)};
    android.graphics.drawable.GradientDrawable appShape(int fill,int stroke,float radius,boolean oval){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        if(oval)g.setShape(android.graphics.drawable.GradientDrawable.OVAL);else g.setCornerRadius(radius);
        g.setColor(fill);g.setStroke(1,stroke);return g;
    }
    void showAppCenter(){
        final float dn=getResources().getDisplayMetrics().density;
        final android.content.SharedPreferences sp=getPreferences(MODE_PRIVATE);
        // Palette follows the active theme: light/dark look, accent colour, and multicolour tiles only on Electric Night.
        final boolean light=premiumMotion?premiumIsDay():appearance==1;
        final int acc=appearance==2?(light?Color.rgb(214,40,68):Color.rgb(255,87,111)):appearance==1?(light?Color.rgb(22,121,196):Color.rgb(92,205,246)):(light?Color.rgb(16,130,190):Color.rgb(42,223,250));
        final boolean multi=appearance==0&&premiumMotion&&premiumSceneMode==1;
        final int bgCol=light?Color.rgb(230,238,245):Color.rgb(4,8,16),txtCol=light?Color.rgb(18,38,56):Color.WHITE;
        final int boxFill=light?Color.WHITE:Color.rgb(14,26,40),boxLine=light?Color.rgb(150,175,195):Color.rgb(40,90,120);
        final int titleCol=multi&&!light?Color.rgb(255,205,100):acc;
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final PackageManager pm=getPackageManager();
        List<ResolveInfo> found=pm.queryIntentActivities(probe,0);
        final ArrayList<AppItem> all=new ArrayList<AppItem>();
        for(ResolveInfo ri:found){
            AppItem it=new AppItem();it.ri=ri;it.pkg=ri.activityInfo.packageName;it.label=String.valueOf(ri.loadLabel(pm));
            try{it.icon=ri.loadIcon(pm);}catch(Throwable e){}
            it.color=multi?APP_COLORS[Math.abs(it.pkg.hashCode())%APP_COLORS.length]:acc;
            it.uses=sp.getInt("appuse_"+it.pkg,0);
            int c=it.color;
            it.tileBg=appShape(light?Color.WHITE:Color.rgb(Color.red(c)/7+8,Color.green(c)/7+10,Color.blue(c)/7+16),Color.argb(190,Color.red(c),Color.green(c),Color.blue(c)),12*dn,false);
            it.iconBg=appShape(Color.argb(light?40:80,Color.red(c),Color.green(c),Color.blue(c)),c,0,true);
            all.add(it);
        }
        final ArrayList<AppItem> shown=new ArrayList<AppItem>();
        final int[] sortMode={sp.getInt("appSort",0)};
        final String[] query={""};
        final Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
            public int getCount(){return shown.size();}public Object getItem(int i){return shown.get(i);}public long getItemId(int i){return i;}
            public View getView(int i,View reuse,android.view.ViewGroup parent){
                LinearLayout cell;ImageView icon;TextView name;
                if(reuse instanceof LinearLayout){cell=(LinearLayout)reuse;icon=(ImageView)cell.getChildAt(0);name=(TextView)cell.getChildAt(1);}
                else{
                    cell=new LinearLayout(MainActivity.this);cell.setOrientation(1);cell.setGravity(Gravity.CENTER);cell.setPadding((int)(4*dn),(int)(8*dn),(int)(4*dn),(int)(6*dn));
                    icon=new ImageView(MainActivity.this);icon.setPadding((int)(7*dn),(int)(7*dn),(int)(7*dn),(int)(7*dn));cell.addView(icon,new LinearLayout.LayoutParams((int)(60*dn),(int)(60*dn)));
                    name=new TextView(MainActivity.this);name.setTextColor(txtCol);name.setTextSize(13);name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(-1,-2);nl.topMargin=(int)(4*dn);cell.addView(name,nl);
                    cell.setLayoutParams(new GridView.LayoutParams(-1,(int)(100*dn)));
                }
                AppItem it=shown.get(i);
                cell.setBackgroundDrawable(it.tileBg);icon.setBackgroundDrawable(it.iconBg);icon.setImageDrawable(it.icon);name.setText(it.label);
                return cell;
            }
        };
        final Runnable refilter=new Runnable(){public void run(){
            shown.clear();String q=query[0].toLowerCase();
            for(AppItem it:all)if(q.length()==0||it.label.toLowerCase().indexOf(q)>=0)shown.add(it);
            final int mode=sortMode[0];
            Collections.sort(shown,new Comparator<AppItem>(){public int compare(AppItem x,AppItem y){
                if(mode==1&&x.uses!=y.uses)return y.uses-x.uses;
                return x.label.compareToIgnoreCase(y.label);}});
            adapter.notifyDataSetChanged();
        }};
        refilter.run();
        GridView grid=new GridView(this);grid.setNumColumns(5);grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing((int)(8*dn));grid.setHorizontalSpacing((int)(8*dn));grid.setPadding((int)(12*dn),(int)(6*dn),(int)(12*dn),(int)(8*dn));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));grid.setCacheColorHint(0);grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int n,long id){try{
            AppItem a=shown.get(n);Intent i=new Intent(Intent.ACTION_MAIN);i.setClassName(a.ri.activityInfo.packageName,a.ri.activityInfo.name);startActivity(i);
            a.uses++;sp.edit().putInt("appuse_"+a.pkg,a.uses).apply();dialog.dismiss();
        }catch(Exception e){toast("Cannot open this app");}}});
        // Long press opens Android's own App Info or uninstall confirmation.
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
            public boolean onItemLongClick(AdapterView<?> parent,View cell,int position,long id){
                final String pkg=shown.get(position).pkg;
                final String app=shown.get(position).label;
                new AlertDialog.Builder(MainActivity.this).setTitle(app)
                  .setItems(trA(new String[]{"App info / Force stop / Clear data","Uninstall app"}),
                    new DialogInterface.OnClickListener(){
                      public void onClick(DialogInterface d,int choice){
                        try{
                            Intent intent;
                            if(choice==0)intent=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg));
                            else intent=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg));
                            startActivity(intent);
                        }catch(Throwable err){toast("Android system cannot perform this action");}
                      }
                    }).setNegativeButton(tr("CANCEL"),null).show();
                return true;
            }
        });
        LinearLayout appPanel=new LinearLayout(this);appPanel.setOrientation(1);appPanel.setBackgroundColor(bgCol);
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding((int)(16*dn),(int)(10*dn),(int)(16*dn),(int)(6*dn));
        TextView title=new TextView(this);title.setText(tr("Apps"));title.setTextColor(titleCol);title.setTextSize(22);title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title,new LinearLayout.LayoutParams(-2,-2));
        final EditText search=new EditText(this);search.setSingleLine(true);search.setHint(tr("Search apps"));search.setHintTextColor(Color.rgb(110,135,150));search.setTextColor(txtCol);search.setTextSize(15);
        search.setBackgroundDrawable(appShape(boxFill,boxLine,10*dn,false));search.setPadding((int)(12*dn),(int)(6*dn),(int)(12*dn),(int)(6*dn));
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence c,int a,int b,int d){}public void onTextChanged(CharSequence c,int a,int b,int d){}
            public void afterTextChanged(android.text.Editable e){query[0]=e.toString().trim();refilter.run();}});
        boolean hasKeyboard=true;
        try{android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);hasKeyboard=imm!=null&&!imm.getEnabledInputMethodList().isEmpty();}catch(Throwable ignored){}
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(0,-2,1);sl.leftMargin=(int)(14*dn);sl.rightMargin=(int)(10*dn);
        if(hasKeyboard)bar.addView(search,sl);else{bar.addView(new View(this),sl);}
        final TextView sort=new TextView(this);sort.setGravity(Gravity.CENTER);sort.setTextColor(txtCol);sort.setTextSize(14);sort.setTypeface(Typeface.DEFAULT_BOLD);
        sort.setBackgroundDrawable(appShape(boxFill,boxLine,10*dn,false));
        final String[] sortNames={tr("A-Z"),tr("Most used")};
        sort.setText(sortNames[sortMode[0]]);
        sort.setOnClickListener(new View.OnClickListener(){public void onClick(View v){sortMode[0]=1-sortMode[0];sp.edit().putInt("appSort",sortMode[0]).apply();sort.setText(sortNames[sortMode[0]]);refilter.run();}});
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams((int)(100*dn),(int)(40*dn));bl.rightMargin=(int)(10*dn);bar.addView(sort,bl);
        TextView back=new TextView(this);back.setText(tr("Back"));back.setGravity(Gravity.CENTER);back.setTextColor(txtCol);back.setTextSize(14);back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setBackgroundDrawable(appShape(light?Color.WHITE:Color.rgb(24,50,70),acc,10*dn,false));
        back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        bar.addView(back,new LinearLayout.LayoutParams((int)(92*dn),(int)(40*dn)));
        appPanel.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        appPanel.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(appPanel);dialog.show();dialog.getWindow().setLayout(-1,-1);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgCol));
    }
    void toggleOilReminder(){boolean v=!getPreferences(MODE_PRIVATE).getBoolean("oil_visible",true);getPreferences(MODE_PRIVATE).edit().putBoolean("oil_visible",v).apply();dashboard.invalidate();toast(v?"Oil reminder shown":"Oil reminder hidden; service data kept");}
    void oilReminderActions(){
        new AlertDialog.Builder(this).setTitle(tr("Oil maintenance")).setItems(trA(new String[]{"Oil changed now (use current odometer)","Hide reminder (keep service data)","Maintenance details"}),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){
            if(n==0){final int odo=getPreferences(MODE_PRIVATE).getInt("odo",-1);if(odo<0){maintenanceInput("Current odometer (km)","odo",odo);return;}
                new AlertDialog.Builder(MainActivity.this).setTitle(tr("Confirm oil change")).setMessage("Record oil change at "+odo+" km? Only confirm after actually changing oil.").setPositiveButton(tr("Confirm"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){getPreferences(MODE_PRIVATE).edit().putInt("oil_last",odo).putBoolean("oil_visible",true).apply();dashboard.invalidate();toast("Oil service recorded");}}).setNegativeButton(tr("Cancel"),null).show();
            }else if(n==1){getPreferences(MODE_PRIVATE).edit().putBoolean("oil_visible",false).apply();dashboard.invalidate();}else maintenanceCenter();
        }}).show();
    }
    void maintenanceCenter(){
        final android.content.SharedPreferences pref=getPreferences(MODE_PRIVATE);
        final int odo=pref.getInt("odo",-1),last=pref.getInt("oil_last",-1),interval=pref.getInt("oil_interval",7000);
        String msg=""+(getPreferences(MODE_PRIVATE).getBoolean("odo_obd",false)?"Odometer read from the vehicle (OBD)":"Odometer entered by you, then counted from driving")+"\n\nCurrent odometer: "+(odo<0?"not entered":odo+" km")+"\nLast oil service: "+(last<0?"not entered":last+" km")+"\nOil interval: "+interval+" km";
        if(odo>=0&&last>=0)msg+="\nNext oil service: "+(last+interval)+" km\nRemaining: "+Math.max(0,last+interval-odo)+" km";
        new AlertDialog.Builder(this).setTitle(tr("Maintenance Center")).setMessage(msg).setPositiveButton(tr("Update odometer"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){maintenanceInput("Current odometer (km)","odo",odo);}}).setNeutralButton("Oil service",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){maintenanceInput("Odometer at last oil change (km)","oil_last",last);}}).setNegativeButton(tr("Close"),null).show();
    }
    void maintenanceInput(String title,final String key,int old){final EditText input=new EditText(this);input.setInputType(2);if(old>=0)input.setText(String.valueOf(old));new AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton(tr("Save"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{int val=Integer.parseInt(input.getText().toString().trim());if(val<0)throw new Exception();getPreferences(MODE_PRIVATE).edit().putInt(key,val).apply();maintenanceCenter();}catch(Exception e){toast("Enter a valid nonnegative kilometer reading");}}}).setNegativeButton(tr("Cancel"),null).show();}
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
    void showMode(){final String[] modes={"DEMO (clearly simulated)","Bluetooth ELM327 (paired)","Disconnect / live blank"};new AlertDialog.Builder(this).setTitle(tr("Data source")).setItems(trA(modes),new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)demo();if(which==1)chooseBluetooth();if(which==2){stop();simulated=false;resetReadings();setStatus("DISCONNECTED");}}}).show();}
    void demo(){stop();resetReadings();simulated=true;carbonClock=false;dashboard.invalidate();toast("DEMO: simulated speed shown in speed widget");setStatus("DEMO / NOT REAL DATA");new Thread(new Runnable(){public void run(){int i=0;while(simulated){int s=(int)(35+25*Math.sin(i/7.0));setSample(s,800+s*35,85+(i%7),25+(i%35),60);i++;try{Thread.sleep(900);}catch(Exception e){break;}}}}).start();}
    void chooseBluetooth(){BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null){toast("Android Bluetooth API unavailable");return;}if(!adapter.isEnabled()){toast("Enable Bluetooth in Android settings first");return;}Set<BluetoothDevice> devices=adapter.getBondedDevices();if(devices==null||devices.isEmpty()){toast("Pair ELM327 in Android Bluetooth settings first");return;}final ArrayList<BluetoothDevice> list=new ArrayList<BluetoothDevice>(devices);String[] labels=new String[list.size()];for(int i=0;i<list.size();i++){BluetoothDevice dev=list.get(i);labels[i]=dev.getName()+"  ("+dev.getAddress()+")";}new AlertDialog.Builder(this).setTitle(tr("Select paired OBD adapter")).setItems(trA(labels),new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){connectBluetooth(list.get(i));}}).show();}
    // Phone GPS: GpsService reads NMEA from the phone companion (Bluetooth SPP or Wi-Fi TCP) and publishes a system test provider.
    android.content.SharedPreferences gpsPrefs(){return getSharedPreferences("zoma_gps",MODE_PRIVATE);}
    void gpsStart(){try{startService(new Intent(this,GpsService.class));}catch(Exception ignored){}}
    void gpsInit(){infoChips=getPreferences(MODE_PRIVATE).getBoolean("infoChips",true);prayerOn=getPreferences(MODE_PRIVATE).getBoolean("prayerOn",false);if(!gpsPrefs().getString("mode","off").equals("off"))gpsStart();ui.postDelayed(new Runnable(){long seenFix=0;public void run(){long fx=GpsService.fixAt;if(!running&&!simulated&&GpsService.fresh()&&fx!=seenFix){seenFix=fx;setSample(gpsKmhShown(),-1,-1,-1,-1);}ui.postDelayed(this,250);}},3000);}
    // Speed shown from GPS: below ~2 km/h is receiver jitter at a stop, so show 0 at once.
    // Map opacity: 0..255 (204 = 80%). Change this one number to tune.
    static final int MAP_ALPHA=204;
    static int gpsKmhShown(){double v=GpsService.speedKmh;return v<2.0?0:(int)Math.round(v);}
    // Info chips (weather from the phone companion, next prayer computed on the unit) and the map button.
    boolean infoChips=true;boolean prayerOn=false;
    static double fixA(double a){return a-360.0*Math.floor(a/360.0);}
    static double fixH(double a){return a-24.0*Math.floor(a/24.0);}
    static double[] sunPos(double jd){double D=jd-2451545.0;double g=fixA(357.529+0.98560028*D);double q=fixA(280.459+0.98564736*D);
        double L=fixA(q+1.915*Math.sin(Math.toRadians(g))+0.020*Math.sin(Math.toRadians(2*g)));double e=23.439-0.00000036*D;
        double ra=fixH(Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(e))*Math.sin(Math.toRadians(L)),Math.cos(Math.toRadians(L))))/15.0);
        return new double[]{Math.toDegrees(Math.asin(Math.sin(Math.toRadians(e))*Math.sin(Math.toRadians(L)))),q/15.0-ra};}
    static double sunAngleTime(double jd,double angle,double portion,double lat,boolean ccw){double[] sp=sunPos(jd+portion);double noon=fixH(12-sp[1]);
        double c=(-Math.sin(Math.toRadians(angle))-Math.sin(Math.toRadians(sp[0]))*Math.sin(Math.toRadians(lat)))/(Math.cos(Math.toRadians(sp[0]))*Math.cos(Math.toRadians(lat)));
        if(c>1)c=1;if(c<-1)c=-1;double t=Math.toDegrees(Math.acos(c))/15.0;return noon+(ccw?-t:t);}
    // Egyptian General Authority method: Fajr 19.5, Isha 17.5, Maghrib at sunset, standard Asr. Returns local hours for Fajr, Dhuhr, Asr, Maghrib, Isha.
    static double[] prayerHours(int y,int m,int d,double lat,double lon,double tz){
        int Y=y,M=m;if(M<=2){Y-=1;M+=12;}double A=Math.floor(Y/100.0),B=2-A+Math.floor(A/4.0);
        double jd=Math.floor(365.25*(Y+4716))+Math.floor(30.6001*(M+1))+d+B-1524.5-lon/(15.0*24.0);
        double adj=tz-lon/15.0;
        double dhuhr=fixH(12-sunPos(jd+0.5)[1]);
        double fajr=sunAngleTime(jd,19.5,5.0/24.0,lat,true);
        double decl=sunPos(jd+13.0/24.0)[0];double aa=-Math.toDegrees(Math.atan(1.0/(1.0+Math.tan(Math.toRadians(Math.abs(lat-decl))))));
        double asr=sunAngleTime(jd,aa,13.0/24.0,lat,false);
        double mag=sunAngleTime(jd,0.833,18.0/24.0,lat,false);
        double isha=sunAngleTime(jd,17.5,18.0/24.0,lat,false);
        return new double[]{fajr+adj,dhuhr+adj,asr+adj,mag+adj,isha+adj};}
    // Egypt summer time (law of 2023): from the last Friday of April to the last Thursday of October.
    static int lastWeekday(int y,int month0,int dow){Calendar c=Calendar.getInstance();c.clear();c.set(y,month0,1);c.set(Calendar.DAY_OF_MONTH,c.getActualMaximum(Calendar.DAY_OF_MONTH));while(c.get(Calendar.DAY_OF_WEEK)!=dow)c.add(Calendar.DAY_OF_MONTH,-1);return c.get(Calendar.DAY_OF_MONTH);}
    static boolean egyptDst(int y,int m,int d){if(m<4||m>10)return false;if(m>4&&m<10)return true;if(m==4)return d>=lastWeekday(y,Calendar.APRIL,Calendar.FRIDAY);return d<=lastWeekday(y,Calendar.OCTOBER,Calendar.THURSDAY);}
    // Next prayer: returns {index 0..4, minutes until it}.
    static int[] nextPrayer(long now,double lat,double lon){
        TimeZone tzo=TimeZone.getDefault();double tz=tzo.getOffset(now)/3600000.0;
        // Head units often keep a wrong zone (e.g. GMT+8) with the wall clock set to local time. Prayer times are computed in wall-clock hours, so if the zone is far from the longitude, use the longitude's zone.
        boolean zoneFixed=false;
        if(Math.abs(tz-lon/15.0)>2.5){tz=Math.round(lon/15.0);zoneFixed=true;}
        for(int dayAdd=0;dayAdd<2;dayAdd++){
            Calendar cal=Calendar.getInstance(tzo);cal.setTimeInMillis(now+dayAdd*86400000L);
            double tzd=tz;if(zoneFixed&&lat>21&&lat<32&&lon>24&&lon<37&&egyptDst(cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH)))tzd=tz+1;
            double[] h=prayerHours(cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH),lat,lon,tzd);
            Calendar c0=Calendar.getInstance(tzo);c0.setTimeInMillis(now+dayAdd*86400000L);c0.set(Calendar.HOUR_OF_DAY,0);c0.set(Calendar.MINUTE,0);c0.set(Calendar.SECOND,0);c0.set(Calendar.MILLISECOND,0);
            for(int i=0;i<5;i++){long at=c0.getTimeInMillis()+Math.round(h[i]*3600000.0);if(at>now)return new int[]{i,(int)((at-now+30000L)/60000L)};}
        }
        return new int[]{0,0};}
    static String wxWord(int code){if(code==0)return "CLEAR";if(code<=2)return "PART CLOUDY";if(code==3)return "CLOUDY";if(code==45||code==48)return "FOG";if(code>=51&&code<=57)return "DRIZZLE";if(code>=61&&code<=67)return "RAIN";if(code>=71&&code<=77)return "SNOW";if(code>=80&&code<=82)return "SHOWERS";if(code>=95)return "STORM";return "WEATHER";}
    String navLabel(){String p=getPreferences(MODE_PRIVATE).getString("navPackage","");if(p.length()==0)return findStrada()!=null?"Auto (Strada)":"Auto (Android Auto)";try{return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)));}catch(Exception e){return p;}}
    String findStrada(){try{Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);for(ResolveInfo ri:getPackageManager().queryIntentActivities(probe,0)){String lab=String.valueOf(ri.loadLabel(getPackageManager())).toLowerCase(Locale.ENGLISH);if(lab.indexOf("strada")>=0)return ri.activityInfo.packageName;}}catch(Throwable ignored){}return null;}
    void openNavApp(){
        SharedPreferences sp=getPreferences(MODE_PRIVATE);
        String p=sp.getString("navPackage",""),comp=sp.getString("navComponent","");
        if(p.length()>0){
            Intent i=null;
            if(comp.length()>0){try{i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);i.setComponent(ComponentName.unflattenFromString(comp));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}catch(Throwable e){i=null;}}
            if(i==null)i=getPackageManager().getLaunchIntentForPackage(p);
            if(i!=null){try{startActivity(i);return;}catch(Throwable ignored){}}
            toast("Chosen map app not found, using Auto");
        }
        p=findStrada();
        if(p!=null){Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null){try{startActivity(i);return;}catch(Throwable ignored){}}}
        openAndroidAuto();
    }
    void chooseNavApp(final Runnable back){
        Intent probe=new Intent(Intent.ACTION_MAIN);probe.addCategory(Intent.CATEGORY_LAUNCHER);
        final ArrayList<ResolveInfo> all=new ArrayList<ResolveInfo>(getPackageManager().queryIntentActivities(probe,0));
        String[] labels=new String[all.size()+1];labels[0]="Auto (Strada if installed, else Android Auto)";
        for(int i=0;i<all.size();i++)labels[i+1]=String.valueOf(all.get(i).loadLabel(getPackageManager()))+" ["+all.get(i).activityInfo.packageName+"]";
        new AlertDialog.Builder(this).setTitle(tr("Map button opens")).setItems(trA(labels),new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface d,int index){ActivityInfo ai=index==0?null:all.get(index-1).activityInfo;getPreferences(MODE_PRIVATE).edit().putString("navPackage",ai==null?"":ai.packageName).putString("navComponent",ai==null?"":new ComponentName(ai.packageName,ai.name).flattenToString()).apply();toast(ai==null?"Map button: Auto":"Map button: "+navLabel());if(back!=null)back.run();}
        }).setNegativeButton(tr("Cancel"),null).show();
    }
    String gpsLabel(){String m=gpsPrefs().getString("mode","off");return m.equals("off")?"OFF":(m.equals("bt")?"Bluetooth":"Wi-Fi")+" | "+GpsService.status;}
    void gpsDialog(final Runnable back){final String[] items={"Wi-Fi hotspot (recommended)","Bluetooth (phone companion)","Off","How to enable mock locations"};new AlertDialog.Builder(this).setTitle("Phone GPS: "+gpsLabel()).setItems(trA(items),new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(w==2){gpsPrefs().edit().putString("mode","off").commit();stopService(new Intent(MainActivity.this,GpsService.class));toast("Phone GPS off");if(back!=null)back.run();}else if(w==1)gpsChooseBt(back);else if(w==0)gpsWifi(back);else gpsHelp(back);}}).show();}
    void gpsChooseBt(final Runnable back){BluetoothAdapter ad=BluetoothAdapter.getDefaultAdapter();if(ad==null){toast("Android Bluetooth API unavailable. Use Wi-Fi instead");return;}try{if(!ad.isEnabled())ad.enable();}catch(Exception ignored){}Set<BluetoothDevice> devs=null;try{devs=ad.getBondedDevices();}catch(Exception ignored){}final ArrayList<BluetoothDevice> list=new ArrayList<BluetoothDevice>();if(devs!=null)list.addAll(devs);String[] labels=new String[list.size()+1];for(int i=0;i<list.size();i++)labels[i]=list.get(i).getName()+"  ("+list.get(i).getAddress()+")";labels[list.size()]="Enter address manually";new AlertDialog.Builder(this).setTitle(tr("Select your phone")).setItems(trA(labels),new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){if(i>=list.size()){gpsManualMac(back);return;}gpsPrefs().edit().putString("mode","bt").putString("mac",list.get(i).getAddress()).commit();gpsStart();toast("Open the ZomaOS companion on the phone and tap Start");if(back!=null)back.run();}}).show();}
    void gpsManualMac(final Runnable back){final EditText field=new EditText(this);field.setSingleLine(true);field.setText(gpsPrefs().getString("mac",""));new AlertDialog.Builder(this).setTitle("Phone Bluetooth address").setMessage("Format AA:BB:CC:DD:EE:FF. Find it on the phone: Settings > About phone > Status > Bluetooth address.").setView(field).setPositiveButton("Connect",new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){String m=field.getText().toString().trim().toUpperCase(Locale.US);if(!BluetoothAdapter.checkBluetoothAddress(m)){toast("Not a valid Bluetooth address");return;}gpsPrefs().edit().putString("mode","bt").putString("mac",m).commit();gpsStart();toast("Open the ZomaOS companion on the phone and tap Start");if(back!=null)back.run();}}).setNegativeButton("Cancel",null).show();}
    void gpsWifi(final Runnable back){final EditText field=new EditText(this);field.setSingleLine(true);String h=gpsPrefs().getString("host","");field.setText(h.length()>0?h:"auto:10110");new AlertDialog.Builder(this).setTitle(tr("Phone IP:port")).setMessage(tr("Turn on the phone hotspot and connect the unit to it. 192.168.43.1 is the usual Android hotspot address.\nLeave auto to find the phone address automatically.")).setView(field).setPositiveButton(tr("Connect"),new android.content.DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){gpsPrefs().edit().putString("mode","wifi").putString("host",field.getText().toString().trim()).commit();gpsStart();if(back!=null)back.run();}}).setNegativeButton(tr("Cancel"),null).show();}
    void gpsHelp(final Runnable back){new AlertDialog.Builder(this).setTitle(tr("Allow mock locations")).setMessage(tr("Other apps (maps) see the phone GPS only if mock locations are on.\n\n1. Install the free Activity Launcher app.\n2. Open it, find Settings > Developer options (or Development settings) and open it.\n3. Turn on Allow mock locations (select ZomaOS if it asks for a mock location app).\n4. Come back and pick Bluetooth or Wi-Fi above.")).setPositiveButton(tr("OK"),null).show();}
    // Fault codes: read-only mode 03 (stored codes). ZomaOS never clears codes.
    static final String[] DTC_TABLE={
        "P0016 Crankshaft and camshaft position correlation (bank 1 sensor A)",
        "P0030 Oxygen sensor heater control circuit (bank 1 sensor 1)",
        "P0036 Oxygen sensor heater control circuit (bank 1 sensor 2)",
        "P0100 Mass air flow sensor circuit",
        "P0101 Mass air flow sensor range or performance",
        "P0102 Mass air flow sensor circuit low",
        "P0103 Mass air flow sensor circuit high",
        "P0105 Manifold pressure sensor circuit",
        "P0106 Manifold pressure sensor range or performance",
        "P0110 Intake air temperature sensor circuit",
        "P0113 Intake air temperature sensor circuit high",
        "P0115 Coolant temperature sensor circuit",
        "P0116 Coolant temperature sensor range or performance",
        "P0117 Coolant temperature sensor circuit low",
        "P0118 Coolant temperature sensor circuit high",
        "P0120 Throttle or pedal position sensor circuit",
        "P0121 Throttle position sensor range or performance",
        "P0122 Throttle position sensor circuit low",
        "P0123 Throttle position sensor circuit high",
        "P0125 Coolant too cold to reach closed loop",
        "P0128 Coolant thermostat below regulating temperature",
        "P0130 Oxygen sensor circuit (bank 1 sensor 1)",
        "P0131 Oxygen sensor circuit low voltage (bank 1 sensor 1)",
        "P0133 Oxygen sensor slow response (bank 1 sensor 1)",
        "P0135 Oxygen sensor heater circuit (bank 1 sensor 1)",
        "P0136 Oxygen sensor circuit (bank 1 sensor 2)",
        "P0141 Oxygen sensor heater circuit (bank 1 sensor 2)",
        "P0171 System too lean (bank 1)",
        "P0172 System too rich (bank 1)",
        "P0174 System too lean (bank 2)",
        "P0175 System too rich (bank 2)",
        "P0200 Injector circuit",
        "P0201 Injector circuit cylinder 1",
        "P0202 Injector circuit cylinder 2",
        "P0203 Injector circuit cylinder 3",
        "P0204 Injector circuit cylinder 4",
        "P0230 Fuel pump primary circuit",
        "P0261 Cylinder 1 injector circuit low",
        "P0300 Random or multiple cylinder misfire",
        "P0301 Cylinder 1 misfire",
        "P0302 Cylinder 2 misfire",
        "P0303 Cylinder 3 misfire",
        "P0304 Cylinder 4 misfire",
        "P0315 Crankshaft position system variation not learned",
        "P0325 Knock sensor circuit (bank 1)",
        "P0327 Knock sensor circuit low (bank 1)",
        "P0335 Crankshaft position sensor circuit",
        "P0336 Crankshaft position sensor range or performance",
        "P0340 Camshaft position sensor circuit",
        "P0341 Camshaft position sensor range or performance",
        "P0351 Ignition coil A circuit",
        "P0352 Ignition coil B circuit",
        "P0353 Ignition coil C circuit",
        "P0354 Ignition coil D circuit",
        "P0400 Exhaust gas recirculation flow",
        "P0420 Catalyst efficiency below threshold (bank 1)",
        "P0430 Catalyst efficiency below threshold (bank 2)",
        "P0440 Evaporative emission system",
        "P0442 Evaporative system small leak",
        "P0446 Evaporative vent control circuit",
        "P0455 Evaporative system large leak (check the fuel cap)",
        "P0456 Evaporative system very small leak",
        "P0480 Cooling fan 1 control circuit",
        "P0500 Vehicle speed sensor",
        "P0505 Idle control system",
        "P0506 Idle speed lower than expected",
        "P0507 Idle speed higher than expected",
        "P0562 System voltage low",
        "P0563 System voltage high",
        "P0601 Control module memory checksum error",
        "P0605 Control module ROM error",
        "P0620 Generator control circuit",
        "P0700 Transmission control system",
        "P0705 Transmission range sensor circuit",
        "P0715 Transmission input speed sensor circuit",
        "P0720 Transmission output speed sensor circuit",
        "P0730 Incorrect gear ratio",
        "P0741 Torque converter clutch performance",
        "P2096 Post catalyst fuel trim too lean (bank 1)",
        "P2187 System too lean at idle (bank 1)",
        "P2195 Oxygen sensor signal stuck lean (bank 1 sensor 1)",
        "P2270 Oxygen sensor signal stuck lean (bank 1 sensor 2)",
        "U0100 Lost communication with engine control module",
        "U0101 Lost communication with transmission control module",
        "U0121 Lost communication with ABS control module",
        "U0140 Lost communication with body control module",
        "C0035 Front wheel speed sensor circuit",
        "C0040 Right front wheel speed sensor circuit",
        "C0045 Left front wheel speed sensor circuit",
        "B0001 Driver airbag circuit"};
    static String dtcInfo(String c){for(String e:DTC_TABLE)if(e.startsWith(c+" "))return e.substring(6);return "Not in the offline list. Search the code online with your car model.";}
    static ArrayList<String> parseDtcs(String raw){ArrayList<String> out=new ArrayList<String>();StringBuilder sb=new StringBuilder();for(String ln:raw.split("[\\r\\n]+")){String x=ln.replace(" ","").trim();if(x.length()==0)continue;int c=x.indexOf(':');if(c>=0)x=x.substring(c+1);else if(x.length()==3)continue;if(!x.matches("[0-9A-F]+"))continue;sb.append(x);}String h=sb.toString();int p=h.startsWith("43")?0:h.indexOf("43");if(p<0)return out;h=h.substring(p+2);if(h.length()%4==2)h=h.substring(2);for(int i=0;i+4<=h.length();i+=4){String w=h.substring(i,i+4);if(w.equals("0000"))continue;int b=Integer.parseInt(w.substring(0,2),16);String letter="PCBU".substring((b>>6)&3,((b>>6)&3)+1);String code=letter+((b>>4)&3)+w.substring(1,2)+w.substring(2);if(!out.contains(code))out.add(code);}return out;}
    volatile boolean dtcRequest=false;
    void askDtcs(){if(simulated){new AlertDialog.Builder(this).setTitle(tr("Fault codes")).setMessage(tr("Demo mode: no real vehicle. Connect an OBD adapter to read codes.")).setPositiveButton(tr("OK"),null).show();return;}if(!running||btSocket==null){toast("Connect the Bluetooth OBD adapter first");return;}dtcRequest=true;toast("Reading fault codes...");}
    void readDtcs(InputStream in,OutputStream out){String msg;try{String raw=command(in,out,"03",4000);ArrayList<String> list=parseDtcs(raw);if(list.isEmpty())msg=raw.contains("NODATA")||raw.replace(" ","").contains("NODATA")||raw.contains("43")?"No stored fault codes.":"The car did not answer. Try again with the engine running.";else{StringBuilder sb=new StringBuilder();for(String c:list)sb.append(c).append("  ").append(dtcInfo(c)).append("\n\n");sb.append("Codes are only read, never cleared.");msg=sb.toString();}}catch(Exception e){msg="Could not read codes: "+e.getClass().getSimpleName();}final String m=tr(msg);ui.post(new Runnable(){public void run(){new AlertDialog.Builder(MainActivity.this).setTitle(tr("Fault codes")).setMessage(m).setPositiveButton(tr("OK"),null).show();}});}
    long lastHotAlert=0;
    void checkEngineAlert(int t){if(simulated||t<105||t>150||!getPreferences(MODE_PRIVATE).getBoolean("obd_alerts",true))return;long now=System.currentTimeMillis();if(now-lastHotAlert<60000)return;lastHotAlert=now;final int tt=t;ui.post(new Runnable(){public void run(){toast("Engine temperature high: "+tt+" C. Stop safely and let it cool.");}});}
    // Interface language: English by default; Arabic when chosen in Display settings. Text is translated at the central points (settings rows, dialogs, toasts, bottom bar).
    static boolean ar=false;
    static HashMap<String,String> AR_MAP=null;
    static final String[] AR_PAIRS={
        "App unavailable. Restore shortcut in long press.","\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u063a\u064a\u0631 \u0645\u062a\u0627\u062d. \u0627\u0633\u062a\u0639\u062f \u0627\u0644\u0627\u062e\u062a\u0635\u0627\u0631 \u0628\u0627\u0644\u0636\u063a\u0637 \u0627\u0644\u0645\u0637\u0648\u0644.",
        "Cancel","\u0625\u0644\u063a\u0627\u0621",
        "OK","\u0645\u0648\u0627\u0641\u0642",
        "Close","\u0625\u063a\u0644\u0627\u0642",
        "CLOSE","\u0625\u063a\u0644\u0627\u0642",
        "BACK","\u0631\u062c\u0648\u0639",
        "CANCEL","\u0625\u0644\u063a\u0627\u0621",
        "Save","\u062d\u0641\u0638",
        "Confirm","\u062a\u0623\u0643\u064a\u062f",
        "Connect","\u0627\u062a\u0635\u0627\u0644",
        "Reset","\u0625\u0639\u0627\u062f\u0629 \u0636\u0628\u0637",
        "Restore","\u0627\u0633\u062a\u0639\u0627\u062f\u0629",
        "No factory app found","\u0644\u0645 \u064a\u062a\u0645 \u0627\u0644\u0639\u062b\u0648\u0631 \u0639\u0644\u0649 \u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0645\u0635\u0646\u0639",
        "No launchable applications found","\u0644\u0627 \u062a\u0648\u062c\u062f \u062a\u0637\u0628\u064a\u0642\u0627\u062a \u0642\u0627\u0628\u0644\u0629 \u0644\u0644\u062a\u0634\u063a\u064a\u0644",
        "Select original Music app","\u0627\u062e\u062a\u0631 \u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649 \u0627\u0644\u0623\u0635\u0644\u064a",
        "Choose Music player","\u0627\u062e\u062a\u0631 \u0645\u0634\u063a\u0644 \u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649",
        "Media control unsupported on this player","\u0627\u0644\u062a\u062d\u0643\u0645 \u063a\u064a\u0631 \u0645\u062f\u0639\u0648\u0645 \u0641\u064a \u0647\u0630\u0627 \u0627\u0644\u0645\u0634\u063a\u0644",
        "System settings unavailable","\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0646\u0638\u0627\u0645 \u063a\u064a\u0631 \u0645\u062a\u0627\u062d\u0629",
        "Cannot play this USB audio file","\u062a\u0639\u0630\u0631 \u062a\u0634\u063a\u064a\u0644 \u0645\u0644\u0641 \u0627\u0644\u0635\u0648\u062a \u0647\u0630\u0627",
        "USB unavailable - choose folder","USB \u063a\u064a\u0631 \u0645\u062a\u0627\u062d - \u0627\u062e\u062a\u0631 \u0645\u062c\u0644\u062f\u0627",
        "Unable to resume USB audio","\u062a\u0639\u0630\u0631 \u0627\u0633\u062a\u0626\u0646\u0627\u0641 \u0627\u0644\u0635\u0648\u062a",
        "Player error","\u062e\u0637\u0623 \u0641\u064a \u0627\u0644\u0645\u0634\u063a\u0644",
        "No supported audio found. Choose a different folder.","\u0644\u0627 \u062a\u0648\u062c\u062f \u0645\u0644\u0641\u0627\u062a \u0635\u0648\u062a \u0645\u062f\u0639\u0648\u0645\u0629. \u0627\u062e\u062a\u0631 \u0645\u062c\u0644\u062f\u0627 \u0622\u062e\u0631.",
        "Choose USB drive","\u0627\u062e\u062a\u0631 \u0641\u0644\u0627\u0634\u0629 USB",
        "No readable USB storage found","\u0644\u0645 \u064a\u062a\u0645 \u0627\u0644\u0639\u062b\u0648\u0631 \u0639\u0644\u0649 \u0630\u0627\u0643\u0631\u0629 USB \u0642\u0627\u0628\u0644\u0629 \u0644\u0644\u0642\u0631\u0627\u0621\u0629",
        "USB not detected - browse manually","\u0644\u0645 \u064a\u062a\u0645 \u0627\u0643\u062a\u0634\u0627\u0641 USB - \u062a\u0635\u0641\u062d \u064a\u062f\u0648\u064a\u0627",
        "Cannot access USB folder","\u062a\u0639\u0630\u0631 \u0641\u062a\u062d \u0645\u062c\u0644\u062f USB",
        "Cannot access folder","\u062a\u0639\u0630\u0631 \u0641\u062a\u062d \u0627\u0644\u0645\u062c\u0644\u062f",
        "No supported audio found","\u0644\u0627 \u062a\u0648\u062c\u062f \u0645\u0644\u0641\u0627\u062a \u0635\u0648\u062a \u0645\u062f\u0639\u0648\u0645\u0629",
        "Unable to display USB files","\u062a\u0639\u0630\u0631 \u0639\u0631\u0636 \u0645\u0644\u0641\u0627\u062a USB",
        "Cannot play selected audio file","\u062a\u0639\u0630\u0631 \u062a\u0634\u063a\u064a\u0644 \u0627\u0644\u0645\u0644\u0641 \u0627\u0644\u0645\u062d\u062f\u062f",
        "Accent color","\u0644\u0648\u0646 \u0627\u0644\u062a\u0645\u064a\u064a\u0632",
        "Clock format","\u0634\u0643\u0644 \u0627\u0644\u0633\u0627\u0639\u0629",
        "ZomaOS V15.9  /  SETTINGS","ZomaOS V15.9  /  \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a",
        "THEME","\u0627\u0644\u0633\u0645\u0629",
        "Look: ","\u0627\u0644\u0645\u0638\u0647\u0631: ",
        "Accent color: ","\u0644\u0648\u0646 \u0627\u0644\u062a\u0645\u064a\u064a\u0632: ",
        "Animated theme: ","\u0627\u0644\u0633\u0645\u0629 \u0627\u0644\u0645\u062a\u062d\u0631\u0643\u0629: ",
        "SCREEN","\u0627\u0644\u0634\u0627\u0634\u0629",
        "Clock format: ","\u0634\u0643\u0644 \u0627\u0644\u0633\u0627\u0639\u0629: ",
        "Gauges: ","\u0627\u0644\u0639\u062f\u0627\u062f\u0627\u062a: ",
        "PERFORMANCE","\u0627\u0644\u0623\u062f\u0627\u0621",
        "Animation smoothness: ","\u0633\u0644\u0627\u0633\u0629 \u0627\u0644\u062d\u0631\u0643\u0629: ",
        "Display","\u0627\u0644\u0639\u0631\u0636",
        "SOURCES","\u0627\u0644\u0645\u0635\u0627\u062f\u0631",
        "USB music folder","\u0645\u062c\u0644\u062f \u0645\u0648\u0633\u064a\u0642\u0649 USB",
        "Choose the flash drive or folder to play from","\u0627\u062e\u062a\u0631 \u0627\u0644\u0641\u0644\u0627\u0634\u0629 \u0623\u0648 \u0627\u0644\u0645\u062c\u0644\u062f \u0644\u0644\u062a\u0634\u063a\u064a\u0644 \u0645\u0646\u0647",
        "Music player: ","\u0645\u0634\u063a\u0644 \u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649: ",
        "Not set","\u063a\u064a\u0631 \u0645\u062d\u062f\u062f",
        "The app the Music button opens","\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0630\u064a \u064a\u0641\u062a\u062d\u0647 \u0632\u0631 \u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649",
        "Music & Media","\u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649 \u0648\u0627\u0644\u0648\u0633\u0627\u0626\u0637",
        "CONNECTION","\u0627\u0644\u0627\u062a\u0635\u0627\u0644",
        "Data source","\u0645\u0635\u062f\u0631 \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a",
        "Demo, Bluetooth ELM327, or disconnect","\u062a\u062c\u0631\u064a\u0628\u064a \u0623\u0648 \u0628\u0644\u0648\u062a\u0648\u062b ELM327 \u0623\u0648 \u062c\u0633\u0631 \u0627\u0644\u0647\u0627\u062a\u0641 \u0623\u0648 \u0642\u0637\u0639 \u0627\u0644\u0627\u062a\u0635\u0627\u0644",
        "Phone GPS: ","GPS \u0645\u0646 \u0627\u0644\u0647\u0627\u062a\u0641: ",
        "Use the phone as GPS source for the unit","\u0627\u0633\u062a\u062e\u062f\u0645 \u0627\u0644\u0647\u0627\u062a\u0641 \u0643\u0645\u0635\u062f\u0631 GPS \u0644\u0644\u0634\u0627\u0634\u0629",
        "Read fault codes","\u0642\u0631\u0627\u0621\u0629 \u0623\u0643\u0648\u0627\u062f \u0627\u0644\u0623\u0639\u0637\u0627\u0644",
        "Stored codes with offline explanations (read only)","\u0627\u0644\u0623\u0643\u0648\u0627\u062f \u0627\u0644\u0645\u062e\u0632\u0646\u0629 \u0645\u0639 \u0634\u0631\u062d \u0628\u062f\u0648\u0646 \u0625\u0646\u062a\u0631\u0646\u062a (\u0642\u0631\u0627\u0621\u0629 \u0641\u0642\u0637)",
        "Engine temperature alert: ","\u062a\u0646\u0628\u064a\u0647 \u062d\u0631\u0627\u0631\u0629 \u0627\u0644\u0645\u062d\u0631\u0643: ",
        "Warns at 105 C or more","\u064a\u0646\u0628\u0647 \u0639\u0646\u062f 105 \u062f\u0631\u062c\u0629 \u0623\u0648 \u0623\u0643\u062b\u0631",
        "Read several values per request: ","\u0642\u0631\u0627\u0621\u0629 \u0639\u062f\u0629 \u0642\u064a\u0645 \u0641\u064a \u0627\u0644\u0637\u0644\u0628: ",
        "Faster OBD. Turn off if your adapter stops answering","OBD \u0623\u0633\u0631\u0639. \u0623\u0648\u0642\u0641\u0647 \u0644\u0648 \u0627\u0644\u062c\u0647\u0627\u0632 \u062a\u0648\u0642\u0641 \u0639\u0646 \u0627\u0644\u0631\u062f",
        "GAUGES","\u0627\u0644\u0639\u062f\u0627\u062f\u0627\u062a",
        "Gauges","\u0627\u0644\u0639\u062f\u0627\u062f\u0627\u062a",
        "Same setting as Display > Screen","\u0646\u0641\u0633 \u0627\u0644\u0625\u0639\u062f\u0627\u062f \u0641\u064a \u0627\u0644\u0639\u0631\u0636 > \u0627\u0644\u0634\u0627\u0634\u0629",
        "Road","\u0627\u0644\u0637\u0631\u064a\u0642",
        "Electric Night","\u0644\u064a\u0644 \u0643\u0647\u0631\u0628\u064a",
        "TRIP","\u0627\u0644\u0631\u062d\u0644\u0629",
        "Reset trip","\u062a\u0635\u0641\u064a\u0631 \u0627\u0644\u0631\u062d\u0644\u0629",
        "Trip reset","\u062a\u0645 \u062a\u0635\u0641\u064a\u0631 \u0627\u0644\u0631\u062d\u0644\u0629",
        "Vehicle & OBD","\u0627\u0644\u0633\u064a\u0627\u0631\u0629 \u0648OBD",
        "OIL","\u0627\u0644\u0632\u064a\u062a",
        "Oil reminder on home screen: ","\u062a\u0630\u0643\u064a\u0631 \u0627\u0644\u0632\u064a\u062a \u0641\u064a \u0627\u0644\u0634\u0627\u0634\u0629 \u0627\u0644\u0631\u0626\u064a\u0633\u064a\u0629: ",
        "Oil change interval: ","\u0645\u0633\u0627\u0641\u0629 \u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0632\u064a\u062a: ",
        "Tap to edit","\u0627\u0636\u063a\u0637 \u0644\u0644\u062a\u0639\u062f\u064a\u0644",
        "Oil service actions","\u0625\u062c\u0631\u0627\u0621\u0627\u062a \u062e\u062f\u0645\u0629 \u0627\u0644\u0632\u064a\u062a",
        "Mark oil changed, hide reminder","\u062a\u0633\u062c\u064a\u0644 \u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0632\u064a\u062a \u0648\u0625\u062e\u0641\u0627\u0621 \u0627\u0644\u062a\u0630\u0643\u064a\u0631",
        "OVERVIEW","\u0646\u0638\u0631\u0629 \u0639\u0627\u0645\u0629",
        "Maintenance center","\u0645\u0631\u0643\u0632 \u0627\u0644\u0635\u064a\u0627\u0646\u0629",
        "Odometer from OBD if the car reports it, else counted from driving","\u0627\u0644\u0639\u062f\u0627\u062f \u0645\u0646 OBD \u0644\u0648 \u0627\u0644\u0633\u064a\u0627\u0631\u0629 \u062a\u062f\u0639\u0645\u0647\u060c \u0648\u0625\u0644\u0627 \u064a\u064f\u062d\u0633\u0628 \u0645\u0646 \u0627\u0644\u0642\u064a\u0627\u062f\u0629",
        "Maintenance","\u0627\u0644\u0635\u064a\u0627\u0646\u0629",
        "SHORTCUTS (BOTTOM BAR)","\u0627\u0644\u0627\u062e\u062a\u0635\u0627\u0631\u0627\u062a (\u0627\u0644\u0634\u0631\u064a\u0637 \u0627\u0644\u0633\u0641\u0644\u064a)",
        "APPS","\u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a",
        "App center","\u0645\u0631\u0643\u0632 \u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a",
        "All installed apps","\u0643\u0644 \u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a \u0627\u0644\u0645\u062b\u0628\u062a\u0629",
        "Apps & Shortcuts","\u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a \u0648\u0627\u0644\u0627\u062e\u062a\u0635\u0627\u0631\u0627\u062a",
        "Restore defaults?","\u0627\u0633\u062a\u0639\u0627\u062f\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0629\u061f",
        "About ZomaOS","\u0639\u0646 ZomaOS",
        "ANDROID","\u0623\u0646\u062f\u0631\u0648\u064a\u062f",
        "Android settings","\u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0623\u0646\u062f\u0631\u0648\u064a\u062f",
        "Change default launcher","\u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0644\u0627\u0646\u0634\u0631 \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a",
        "Show the launcher chooser again","\u0625\u0638\u0647\u0627\u0631 \u0627\u062e\u062a\u064a\u0627\u0631 \u0627\u0644\u0644\u0627\u0646\u0634\u0631 \u0645\u0631\u0629 \u0623\u062e\u0631\u0649",
        "ZOMAOS","ZOMAOS",
        "About","\u062d\u0648\u0644",
        "Back up settings","\u0646\u0633\u062e \u0627\u062d\u062a\u064a\u0627\u0637\u064a \u0644\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a",
        "Save to USB or unit storage","\u062d\u0641\u0638 \u0639\u0644\u0649 USB \u0623\u0648 \u0630\u0627\u0643\u0631\u0629 \u0627\u0644\u0634\u0627\u0634\u0629",
        "Restore settings","\u0627\u0633\u062a\u0639\u0627\u062f\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a",
        "From ZomaOS-settings.txt on USB or storage","\u0645\u0646 \u0645\u0644\u0641 ZomaOS-settings.txt \u0639\u0644\u0649 USB \u0623\u0648 \u0627\u0644\u0630\u0627\u0643\u0631\u0629",
        "Restore ZomaOS defaults","\u0627\u0633\u062a\u0639\u0627\u062f\u0629 \u0625\u0639\u062f\u0627\u062f\u0627\u062a ZomaOS \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0629",
        "Resets theme, gauges and display options","\u064a\u0639\u064a\u062f \u0627\u0644\u0633\u0645\u0629 \u0648\u0627\u0644\u0639\u062f\u0627\u062f\u0627\u062a \u0648\u062e\u064a\u0627\u0631\u0627\u062a \u0627\u0644\u0639\u0631\u0636",
        "System","\u0627\u0644\u0646\u0638\u0627\u0645",
        "OPEN APP INFO","\u0641\u062a\u062d \u0645\u0639\u0644\u0648\u0645\u0627\u062a \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        "Look","\u0627\u0644\u0645\u0638\u0647\u0631",
        "Animation smoothness","\u0633\u0644\u0627\u0633\u0629 \u0627\u0644\u062d\u0631\u0643\u0629",
        "Unable to open app","\u062a\u0639\u0630\u0631 \u0641\u062a\u062d \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        "Oil maintenance","\u0635\u064a\u0627\u0646\u0629 \u0627\u0644\u0632\u064a\u062a",
        "Confirm oil change","\u062a\u0623\u0643\u064a\u062f \u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0632\u064a\u062a",
        "Oil service recorded","\u062a\u0645 \u062a\u0633\u062c\u064a\u0644 \u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0632\u064a\u062a",
        "Maintenance Center","\u0645\u0631\u0643\u0632 \u0627\u0644\u0635\u064a\u0627\u0646\u0629",
        "Update odometer","\u062a\u062d\u062f\u064a\u062b \u0627\u0644\u0639\u062f\u0627\u062f",
        "Enter a valid nonnegative kilometer reading","\u0627\u062f\u062e\u0644 \u0642\u0631\u0627\u0621\u0629 \u0643\u064a\u0644\u0648\u0645\u062a\u0631\u0627\u062a \u0635\u062d\u064a\u062d\u0629",
        "Seeking unavailable","\u0627\u0644\u062a\u0642\u062f\u064a\u0645 \u063a\u064a\u0631 \u0645\u062a\u0627\u062d",
        "Enable Bluetooth in Android settings first","\u0641\u0639\u0651\u0644 \u0627\u0644\u0628\u0644\u0648\u062a\u0648\u062b \u0645\u0646 \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0623\u0646\u062f\u0631\u0648\u064a\u062f \u0623\u0648\u0644\u0627",
        "Pair ELM327 in Android Bluetooth settings first","\u0627\u0642\u0631\u0646 ELM327 \u0645\u0646 \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0628\u0644\u0648\u062a\u0648\u062b \u0623\u0648\u0644\u0627",
        "Select paired OBD adapter","\u0627\u062e\u062a\u0631 \u062c\u0647\u0627\u0632 OBD \u0627\u0644\u0645\u0642\u062a\u0631\u0646",
        "Phone GPS off","\u062a\u0645 \u0625\u064a\u0642\u0627\u0641 GPS \u0627\u0644\u0647\u0627\u062a\u0641",
        "Enable Bluetooth first","\u0641\u0639\u0651\u0644 \u0627\u0644\u0628\u0644\u0648\u062a\u0648\u062b \u0623\u0648\u0644\u0627",
        "Pair your phone in Android Bluetooth settings first","\u0627\u0642\u0631\u0646 \u0627\u0644\u0647\u0627\u062a\u0641 \u0645\u0646 \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0628\u0644\u0648\u062a\u0648\u062b \u0623\u0648\u0644\u0627",
        "Select your phone","\u0627\u062e\u062a\u0631 \u0647\u0627\u062a\u0641\u0643",
        "Open the ZomaOS companion on the phone and tap Start","\u0627\u0641\u062a\u062d \u062a\u0637\u0628\u064a\u0642 ZomaOS Companion \u0639\u0644\u0649 \u0627\u0644\u0647\u0627\u062a\u0641 \u0648\u0627\u0636\u063a\u0637 \u0627\u0628\u062f\u0623",
        "Phone IP:port","\u0639\u0646\u0648\u0627\u0646 \u0627\u0644\u0647\u0627\u062a\u0641 IP:port",
        "Turn on the phone hotspot and connect the unit to it. 192.168.43.1 is the usual Android hotspot address.","\u0634\u063a\u0651\u0644 \u0647\u0648\u062a \u0633\u0628\u0648\u062a \u0627\u0644\u0647\u0627\u062a\u0641 \u0648\u0627\u0631\u0628\u0637 \u0627\u0644\u0634\u0627\u0634\u0629 \u0628\u0647. \u0627\u0644\u0639\u0646\u0648\u0627\u0646 \u0627\u0644\u0645\u0639\u062a\u0627\u062f: 192.168.43.1",
        "Allow mock locations","\u0627\u0644\u0633\u0645\u0627\u062d \u0628\u0627\u0644\u0645\u0648\u0627\u0642\u0639 \u0627\u0644\u0648\u0647\u0645\u064a\u0629",
        "Other apps (maps) see the phone GPS only if mock locations are on.","\u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a \u0627\u0644\u0623\u062e\u0631\u0649 (\u0627\u0644\u062e\u0631\u0627\u0626\u0637) \u062a\u0631\u0649 \u0645\u0648\u0642\u0639 \u0627\u0644\u0647\u0627\u062a\u0641 \u0641\u0642\u0637 \u0644\u0648 \u0627\u0644\u0645\u0648\u0627\u0642\u0639 \u0627\u0644\u0648\u0647\u0645\u064a\u0629 \u0645\u0641\u0639\u0651\u0644\u0629.",
        "1. Install the free Activity Launcher app.","1. \u062b\u0628\u0651\u062a \u062a\u0637\u0628\u064a\u0642 Activity Launcher \u0627\u0644\u0645\u062c\u0627\u0646\u064a.",
        "2. Open it, find Settings > Developer options (or Development settings) and open it.","2. \u0627\u0641\u062a\u062d\u0647 \u0648\u0627\u062f\u062e\u0644 \u0625\u0639\u062f\u0627\u062f\u0627\u062a \u0627\u0644\u0645\u0637\u0648\u0631 (Developer options) \u0645\u0646 \u062e\u0644\u0627\u0644\u0647.",
        "3. Turn on Allow mock locations (select ZomaOS if it asks for a mock location app).","3. \u0641\u0639\u0651\u0644 \"\u0627\u0644\u0633\u0645\u0627\u062d \u0628\u0627\u0644\u0645\u0648\u0627\u0642\u0639 \u0627\u0644\u0648\u0647\u0645\u064a\u0629\" (\u0627\u062e\u062a\u0631 ZomaOS \u0644\u0648 \u0637\u0644\u0628).",
        "4. Come back and pick Bluetooth or Wi-Fi above.","4. \u0627\u0631\u062c\u0639 \u0648\u0627\u062e\u062a\u0631 \u0628\u0644\u0648\u062a\u0648\u062b \u0623\u0648 Wi-Fi.",
        "Fault codes","\u0623\u0643\u0648\u0627\u062f \u0627\u0644\u0623\u0639\u0637\u0627\u0644",
        "Demo mode: no real vehicle. Connect an OBD adapter to read codes.","\u0627\u0644\u0648\u0636\u0639 \u0627\u0644\u062a\u062c\u0631\u064a\u0628\u064a: \u0644\u0627 \u062a\u0648\u062c\u062f \u0633\u064a\u0627\u0631\u0629 \u062d\u0642\u064a\u0642\u064a\u0629. \u0648\u0635\u0651\u0644 \u062c\u0647\u0627\u0632 OBD \u0644\u0642\u0631\u0627\u0621\u0629 \u0627\u0644\u0623\u0643\u0648\u0627\u062f.",
        "Connect the Bluetooth OBD adapter first","\u0648\u0635\u0651\u0644 \u062c\u0647\u0627\u0632 OBD \u0628\u0644\u0648\u062a\u0648\u062b \u0623\u0648\u0644\u0627",
        "Reading fault codes...","\u062c\u0627\u0631\u064a \u0642\u0631\u0627\u0621\u0629 \u0627\u0644\u0623\u0643\u0648\u0627\u062f...",
        "No stored fault codes.","\u0644\u0627 \u062a\u0648\u062c\u062f \u0623\u0643\u0648\u0627\u062f \u0623\u0639\u0637\u0627\u0644 \u0645\u062e\u0632\u0646\u0629.",
        "Codes are only read, never cleared.","\u0627\u0644\u0623\u0643\u0648\u0627\u062f \u062a\u064f\u0642\u0631\u0623 \u0641\u0642\u0637 \u0648\u0644\u0627 \u062a\u064f\u0645\u0633\u062d.",
        "The car did not answer. Try again with the engine running.","\u0627\u0644\u0633\u064a\u0627\u0631\u0629 \u0644\u0645 \u062a\u0631\u062f. \u062c\u0631\u0651\u0628 \u0648\u0627\u0644\u0645\u062d\u0631\u0643 \u0634\u063a\u0627\u0644.",
        "No backup found","\u0644\u0645 \u064a\u062a\u0645 \u0627\u0644\u0639\u062b\u0648\u0631 \u0639\u0644\u0649 \u0646\u0633\u062e\u0629",
        "Restore settings?","\u0627\u0633\u062a\u0639\u0627\u062f\u0629 \u0627\u0644\u0625\u0639\u062f\u0627\u062f\u0627\u062a\u061f",
        "Could not read the backup file","\u062a\u0639\u0630\u0631\u062a \u0642\u0631\u0627\u0621\u0629 \u0645\u0644\u0641 \u0627\u0644\u0646\u0633\u062e\u0629",
        "Backup saved","\u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0646\u0633\u062e\u0629",
        "Backup failed","\u0641\u0634\u0644 \u0627\u0644\u0646\u0633\u062e",
        "App not found","\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u063a\u064a\u0631 \u0645\u0648\u062c\u0648\u062f",
        "ON","\u062a\u0634\u063a\u064a\u0644",
        "OFF","\u0625\u064a\u0642\u0627\u0641",
        "Off","\u0625\u064a\u0642\u0627\u0641",
        "Show","\u0625\u0638\u0647\u0627\u0631",
        "Hide","\u0625\u062e\u0641\u0627\u0621",
        "When connected","\u0639\u0646\u062f \u0627\u0644\u0627\u062a\u0635\u0627\u0644",
        "When connected (hide without OBD data)","\u0639\u0646\u062f \u0627\u0644\u0627\u062a\u0635\u0627\u0644 (\u0625\u062e\u0641\u0627\u0621 \u0628\u062f\u0648\u0646 \u0628\u064a\u0627\u0646\u0627\u062a OBD)",
        "Day","\u0646\u0647\u0627\u0631",
        "Night","\u0644\u064a\u0644",
        "Auto (sunrise - sunset)","\u062a\u0644\u0642\u0627\u0626\u064a (\u0634\u0631\u0648\u0642 - \u063a\u0631\u0648\u0628)",
        "Electric Night","\u0644\u064a\u0644 \u0643\u0647\u0631\u0628\u0627\u0626\u064a",
        "Pearl Day","\u0646\u0647\u0627\u0631 \u0644\u0624\u0644\u0624\u064a",
        "Carbon Sport","\u0643\u0631\u0628\u0648\u0646 \u0631\u064a\u0627\u0636\u064a",
        "24 hour","24 \u0633\u0627\u0639\u0629",
        "12 hour","12 \u0633\u0627\u0639\u0629",
        "12 hour AM/PM","12 \u0633\u0627\u0639\u0629 \u0635/\u0645",
        "Smooth (30 FPS, recommended)","\u0633\u0644\u0633 (30 \u0625\u0637\u0627\u0631\u060c \u0645\u0648\u0635\u0649 \u0628\u0647)",
        "Balanced (20 FPS)","\u0645\u062a\u0648\u0627\u0632\u0646 (20 \u0625\u0637\u0627\u0631)",
        "Light (10 FPS)","\u062e\u0641\u064a\u0641 (10 \u0625\u0637\u0627\u0631\u0627\u062a)",
        "DISPLAY","\u0627\u0644\u0639\u0631\u0636",
        "MUSIC & MEDIA","\u0627\u0644\u0645\u0648\u0633\u064a\u0642\u0649 \u0648\u0627\u0644\u0648\u0633\u0627\u0626\u0637",
        "VEHICLE & OBD","\u0627\u0644\u0633\u064a\u0627\u0631\u0629 \u0648OBD",
        "APPS & SHORTCUTS","\u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a \u0648\u0627\u0644\u0627\u062e\u062a\u0635\u0627\u0631\u0627\u062a",
        "MAINTENANCE","\u0627\u0644\u0635\u064a\u0627\u0646\u0629",
        "SYSTEM","\u0627\u0644\u0646\u0638\u0627\u0645",
        "DEMO (clearly simulated)","\u062a\u062c\u0631\u064a\u0628\u064a (\u0645\u062d\u0627\u0643\u0627\u0629 \u0648\u0627\u0636\u062d\u0629)",
        "Bluetooth ELM327 (paired)","\u0628\u0644\u0648\u062a\u0648\u062b ELM327 (\u0645\u0642\u062a\u0631\u0646)",
        "Disconnect / live blank","\u0642\u0637\u0639 \u0627\u0644\u0627\u062a\u0635\u0627\u0644 / \u0625\u0641\u0631\u0627\u063a \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a",
        "Bluetooth (phone companion)","\u0628\u0644\u0648\u062a\u0648\u062b (\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0647\u0627\u062a\u0641 \u0627\u0644\u0645\u0631\u0627\u0641\u0642)",
        "Wi-Fi (phone hotspot IP:port)","Wi-Fi (\u0639\u0646\u0648\u0627\u0646 \u0647\u0648\u062a \u0633\u0628\u0648\u062a \u0627\u0644\u0647\u0627\u062a\u0641 IP:port)",
        "How to enable mock locations","\u0643\u064a\u0641 \u062a\u0641\u0639\u0651\u0644 \u0627\u0644\u0645\u0648\u0627\u0642\u0639 \u0627\u0644\u0648\u0647\u0645\u064a\u0629",
        "HOME","\u0627\u0644\u0631\u0626\u064a\u0633\u064a\u0629",
        "MUSIC","\u0645\u0648\u0633\u064a\u0642\u0649",
        "RADIO","\u0631\u0627\u062f\u064a\u0648",
        "AUTO","\u0623\u0648\u062a\u0648",
        "SET","\u0625\u0639\u062f\u0627\u062f\u0627\u062a",
        "USB FILES","\u0645\u0644\u0641\u0627\u062a USB",
        "FM RADIO","\u0631\u0627\u062f\u064a\u0648 FM",
        "ANDROID AUTO","\u0623\u0646\u062f\u0631\u0648\u064a\u062f \u0623\u0648\u062a\u0648",
        "English or Arabic","\u0627\u0644\u0625\u0646\u062c\u0644\u064a\u0632\u064a\u0629 \u0623\u0648 \u0627\u0644\u0639\u0631\u0628\u064a\u0629",
        "Language: ","\u0627\u0644\u0644\u063a\u0629: ",
        "Oil changed now (use current odometer)","\u062a\u0645 \u062a\u063a\u064a\u064a\u0631 \u0627\u0644\u0632\u064a\u062a \u0627\u0644\u0622\u0646 (\u062d\u0633\u0628 \u0627\u0644\u0639\u062f\u0627\u062f \u0627\u0644\u062d\u0627\u0644\u064a)",
        "Hide reminder (keep service data)","\u0625\u062e\u0641\u0627\u0621 \u0627\u0644\u062a\u0630\u0643\u064a\u0631 (\u0645\u0639 \u062d\u0641\u0638 \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a)",
        "Maintenance details","\u062a\u0641\u0627\u0635\u064a\u0644 \u0627\u0644\u0635\u064a\u0627\u0646\u0629",
        "Open Bluetooth Music","\u0641\u062a\u062d \u0645\u0648\u0633\u064a\u0642\u0649 \u0627\u0644\u0628\u0644\u0648\u062a\u0648\u062b",
        "Open Phone / Dial","\u0641\u062a\u062d \u0627\u0644\u0647\u0627\u062a\u0641 / \u0627\u0644\u0627\u062a\u0635\u0627\u0644",
        "Map & GPS","\u0627\u0644\u062e\u0631\u064a\u0637\u0629 \u0648 GPS",
        "Theme, clock, chips, gauges","\u0627\u0644\u062b\u064a\u0645 \u0648\u0627\u0644\u0633\u0627\u0639\u0629 \u0648\u0627\u0644\u0634\u0631\u0627\u0626\u062d \u0648\u0627\u0644\u0639\u062f\u0627\u062f\u0627\u062a",
        "Map app, phone GPS, offline map","\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u062e\u0631\u064a\u0637\u0629 \u0648GPS \u0627\u0644\u0647\u0627\u062a\u0641 \u0648\u0627\u0644\u062e\u0631\u064a\u0637\u0629 \u0628\u062f\u0648\u0646 \u0646\u062a",
        "Data source, fault codes, trip","\u0645\u0635\u062f\u0631 \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a \u0648\u0623\u0643\u0648\u0627\u062f \u0627\u0644\u0623\u0639\u0637\u0627\u0644 \u0648\u0627\u0644\u0631\u062d\u0644\u0629",
        "USB folder, player","\u0645\u062c\u0644\u062f USB \u0648\u0627\u0644\u0645\u0634\u063a\u0644",
        "Bottom bar, app center","\u0627\u0644\u0634\u0631\u064a\u0637 \u0627\u0644\u0633\u0641\u0644\u064a \u0648\u0645\u0631\u0643\u0632 \u0627\u0644\u062a\u0637\u0628\u064a\u0642\u0627\u062a",
        "Oil, odometer","\u0627\u0644\u0632\u064a\u062a \u0648\u0639\u062f\u0627\u062f \u0627\u0644\u0645\u0633\u0627\u0641\u0629",
        "Android, backup, about","\u0623\u0646\u062f\u0631\u0648\u064a\u062f \u0648\u0627\u0644\u0646\u0633\u062e \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a \u0648\u0639\u0646 \u0627\u0644\u0628\u0631\u0646\u0627\u0645\u062c",
        "NAVIGATION APP","\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0645\u0644\u0627\u062d\u0629",
        "PHONE GPS","GPS \u0627\u0644\u0647\u0627\u062a\u0641",
        "OFFLINE MAP","\u062e\u0631\u064a\u0637\u0629 \u0628\u062f\u0648\u0646 \u0646\u062a",
        "Map button: ","\u0632\u0631 \u0627\u0644\u062e\u0631\u064a\u0637\u0629: ",
        "The app the arrow button on the left opens","\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u0630\u064a \u064a\u0641\u062a\u062d\u0647 \u0632\u0631 \u0627\u0644\u0633\u0647\u0645 \u0639\u0644\u0649 \u0627\u0644\u064a\u0633\u0627\u0631",
        "Weather chip: ","\u0634\u0631\u064a\u062d\u0629 \u0627\u0644\u0637\u0642\u0633: ",
        "Weather from the phone, beside the clock","\u0627\u0644\u0637\u0642\u0633 \u0645\u0646 \u0627\u0644\u0647\u0627\u062a\u0641 \u0628\u062c\u0627\u0646\u0628 \u0627\u0644\u0633\u0627\u0639\u0629",
        "Prayer times: ","\u0645\u0648\u0627\u0642\u064a\u062a \u0627\u0644\u0635\u0644\u0627\u0629: ",
        "Next prayer countdown beside the clock. Off by default","\u0627\u0644\u0639\u062f \u0627\u0644\u062a\u0646\u0627\u0632\u0644\u064a \u0644\u0644\u0635\u0644\u0627\u0629 \u0627\u0644\u0642\u0627\u062f\u0645\u0629 \u0628\u062c\u0627\u0646\u0628 \u0627\u0644\u0633\u0627\u0639\u0629. \u0645\u063a\u0644\u0642 \u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0627",
        "Info chips: ","\u0634\u0631\u0627\u0626\u062d \u0627\u0644\u0645\u0639\u0644\u0648\u0645\u0627\u062a: ",
        "Weather (from the phone) and next prayer beside the clock","\u0627\u0644\u0637\u0642\u0633 (\u0645\u0646 \u0627\u0644\u0647\u0627\u062a\u0641) \u0648\u0627\u0644\u0635\u0644\u0627\u0629 \u0627\u0644\u0642\u0627\u062f\u0645\u0629 \u0628\u062c\u0627\u0646\u0628 \u0627\u0644\u0633\u0627\u0639\u0629",
        "Map in gauge: ","\u0627\u0644\u062e\u0631\u064a\u0637\u0629 \u0641\u064a \u0627\u0644\u0639\u062f\u0627\u062f: ",
        "Round offline map replaces the RPM gauge. Tap the gauge to switch","\u062e\u0631\u064a\u0637\u0629 \u0645\u0633\u062a\u062f\u064a\u0631\u0629 \u0628\u062f\u0644 \u0639\u062f\u0627\u062f \u0627\u0644\u062f\u0648\u0631\u0627\u0646. \u0627\u0636\u063a\u0637 \u0627\u0644\u0639\u062f\u0627\u062f \u0644\u0644\u062a\u0628\u062f\u064a\u0644",
        "Map file: ","\u0645\u0644\u0641 \u0627\u0644\u062e\u0631\u064a\u0637\u0629: ",
        "Pick a .map file (Strada folders and USB are scanned)","\u0627\u062e\u062a\u0631 \u0645\u0644\u0641 .map (\u064a\u062a\u0645 \u0641\u062d\u0635 \u0645\u062c\u0644\u062f\u0627\u062a Strada \u0648USB)",
        "Download a map","\u062a\u062d\u0645\u064a\u0644 \u062e\u0631\u064a\u0637\u0629",
        "Choose continent and country, works offline afterwards","\u0627\u062e\u062a\u0631 \u0627\u0644\u0642\u0627\u0631\u0629 \u0648\u0627\u0644\u062f\u0648\u0644\u0629\u060c \u0648\u062a\u0639\u0645\u0644 \u0628\u062f\u0648\u0646 \u0646\u062a \u0628\u0639\u062f \u0630\u0644\u0643",
        "Animated car and gauges","\u0633\u064a\u0627\u0631\u0629 \u0648\u0639\u062f\u0627\u062f\u0627\u062a \u0645\u062a\u062d\u0631\u0643\u0629",
        "Speed and RPM gauges on the home screen","\u0639\u062f\u0627\u062f\u0627 \u0627\u0644\u0633\u0631\u0639\u0629 \u0648\u0627\u0644\u062f\u0648\u0631\u0627\u0646 \u0641\u064a \u0627\u0644\u0634\u0627\u0634\u0629 \u0627\u0644\u0631\u0626\u064a\u0633\u064a\u0629",
        "Shortcut ","\u0627\u062e\u062a\u0635\u0627\u0631 ",
        "Map button opens","\u0632\u0631 \u0627\u0644\u062e\u0631\u064a\u0637\u0629 \u064a\u0641\u062a\u062d",
        "Choose continent","\u0627\u062e\u062a\u0631 \u0627\u0644\u0642\u0627\u0631\u0629",
        "Choose country","\u0627\u062e\u062a\u0631 \u0627\u0644\u062f\u0648\u0644\u0629",
        "Downloading map","\u062c\u0627\u0631\u064a \u062a\u062d\u0645\u064a\u0644 \u0627\u0644\u062e\u0631\u064a\u0637\u0629",
        "Map file","\u0645\u0644\u0641 \u0627\u0644\u062e\u0631\u064a\u0637\u0629",
        "Last error","\u0622\u062e\u0631 \u062e\u0637\u0623",
        "Show or clear the last crash or map error","\u0639\u0631\u0636 \u0623\u0648 \u0645\u0633\u062d \u0622\u062e\u0631 \u0627\u0646\u0647\u064a\u0627\u0631 \u0623\u0648 \u062e\u0637\u0623 \u0641\u064a \u0627\u0644\u062e\u0631\u064a\u0637\u0629",
        "Clear","\u0645\u0633\u062d",
        "No error recorded","\u0644\u0627 \u064a\u0648\u062c\u062f \u062e\u0637\u0623 \u0645\u0633\u062c\u0644",
        "Map button: Auto","\u0632\u0631 \u0627\u0644\u062e\u0631\u064a\u0637\u0629: \u062a\u0644\u0642\u0627\u0626\u064a",
        "Chosen map app not found, using Auto","\u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u062e\u0631\u064a\u0637\u0629 \u0627\u0644\u0645\u062e\u062a\u0627\u0631 \u063a\u064a\u0631 \u0645\u0648\u062c\u0648\u062f\u060c \u0633\u064a\u062a\u0645 \u0627\u0633\u062a\u062e\u062f\u0627\u0645 \u0627\u0644\u062a\u0644\u0642\u0627\u0626\u064a"};
    static String trLine(String line){if(AR_MAP==null){HashMap<String,String> m=new HashMap<String,String>();for(int i=0;i+1<AR_PAIRS.length;i+=2)m.put(AR_PAIRS[i],AR_PAIRS[i+1]);AR_MAP=m;}String x=AR_MAP.get(line);if(x!=null)return x;int c=line.indexOf(": ");if(c>0){String pre=AR_MAP.get(line.substring(0,c+2));if(pre!=null)return pre+trLine(line.substring(c+2));}return line;}
    static String tr(String s){if(!ar||s==null)return s;String[] parts=s.split("\\n",-1);StringBuilder sb=new StringBuilder();for(int i=0;i<parts.length;i++){if(i>0)sb.append('\n');sb.append(trLine(parts[i]));}return sb.toString();}
    static String[] trA(String[] a){if(!ar||a==null)return a;String[] o=new String[a.length];for(int i=0;i<a.length;i++)o[i]=tr(a[i]);return o;}
    void toggleLanguage(){boolean on=!getPreferences(MODE_PRIVATE).getString("lang","en").equals("ar");getPreferences(MODE_PRIVATE).edit().putString("lang",on?"ar":"en").commit();ar=on;recreate();}
    volatile double odoPending=0;
    // Keeps the stored odometer growing with real driven distance (only after the user or OBD has set a starting value).
    void odoAdd(double dk){if(dk<=0||dk>5)return;odoPending+=dk;if(odoPending>=0.2){android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);int odo=p.getInt("odo",-1);if(odo>=0){float f=p.getFloat("odo_f",odo);if((int)f!=odo)f=odo;f+=(float)odoPending;p.edit().putFloat("odo_f",f).putInt("odo",(int)f).apply();}odoPending=0;}}
    // Mode 01 PID A6 (odometer, 0.1 km units). Only newer cars answer; asked once per connection, on its own so a refusal cannot disturb the fast loop.
    void readObdOdo(InputStream in,OutputStream out){try{String ro=command(in,out,"01A6",1800).replaceAll("[^0-9A-F]","");int p=ro.indexOf("41A6");if(p>=0&&ro.length()>=p+12){long v=Long.parseLong(ro.substring(p+4,p+12),16);int km=(int)(v/10);if(km>0&&km<2000000)getPreferences(MODE_PRIVATE).edit().putInt("odo",km).putFloat("odo_f",(float)km).putBoolean("odo_obd",true).apply();}}catch(Exception ignored){}}
    // Settings backup: all ZomaOS preferences to ZomaOS-settings.txt on a writable USB drive and the unit storage.
    static final String BACKUP_NAME="ZomaOS-settings.txt";
    ArrayList<File> backupDirs(){ArrayList<File> out=new ArrayList<File>();ArrayList<String> seen=new ArrayList<String>();String[] bases={"/mnt/usb","/mnt/usb_storage","/mnt/udisk","/mnt/usbhost1","/mnt/usbhost2","/storage","/mnt/media_rw"};for(String base:bases){File root=new File(base);if(!root.isDirectory())continue;File[] ch=root.listFiles();if(ch!=null)for(File c:ch){String n=c.getName();if(c.isDirectory()&&c.canWrite()&&(n.startsWith("USBA")||n.startsWith("usb")||n.startsWith("sd")||n.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))&&!seen.contains(c.getAbsolutePath())){seen.add(c.getAbsolutePath());out.add(c);}}if(!base.equals("/storage")&&!base.equals("/mnt/media_rw")&&root.canWrite()&&!seen.contains(root.getAbsolutePath())){seen.add(root.getAbsolutePath());out.add(root);}}File ext=Environment.getExternalStorageDirectory();if(ext!=null&&ext.canWrite()&&!seen.contains(ext.getAbsolutePath()))out.add(ext);return out;}
    void encodePrefs(android.content.SharedPreferences p,String tag,StringBuilder sb){for(Map.Entry<String,?> e:p.getAll().entrySet()){Object v=e.getValue();if(v==null||v instanceof Set)continue;String t=v instanceof Boolean?"b":v instanceof Integer?"i":v instanceof Long?"l":v instanceof Float?"f":"s";sb.append(tag).append('|').append(t).append('|').append(e.getKey()).append('|').append(String.valueOf(v).replace("\\","\\\\").replace("\n","\\n")).append('\n');}}
    static String unesc(String v){StringBuilder o=new StringBuilder();for(int i=0;i<v.length();i++){char c=v.charAt(i);if(c=='\\'&&i+1<v.length()){char n=v.charAt(++i);o.append(n=='n'?'\n':n);}else o.append(c);}return o.toString();}
    void backupSettings(){StringBuilder sb=new StringBuilder("ZOMAOS-BACKUP 1\n");encodePrefs(getPreferences(MODE_PRIVATE),"P",sb);encodePrefs(getSharedPreferences("zoma_gps",MODE_PRIVATE),"G",sb);ArrayList<File> dirs=backupDirs();StringBuilder done=new StringBuilder();for(File d:dirs){try{OutputStreamWriter w=new OutputStreamWriter(new FileOutputStream(new File(d,BACKUP_NAME)),"UTF-8");w.write(sb.toString());w.close();done.append("\n").append(d.getAbsolutePath());}catch(Exception e){}}new AlertDialog.Builder(this).setTitle(done.length()>0?"Backup saved":"Backup failed").setMessage(done.length()>0?"Saved "+BACKUP_NAME+" to:"+done:"No writable USB drive or storage found. Plug in a USB drive and try again.").setPositiveButton(tr("OK"),null).show();}
    void restoreSettings(){File found=null;for(File d:backupDirs()){File f=new File(d,BACKUP_NAME);if(f.isFile()&&f.canRead()){found=f;break;}}if(found==null){new AlertDialog.Builder(this).setTitle(tr("No backup found")).setMessage("Put "+BACKUP_NAME+" on the USB drive (or unit storage) and try again.").setPositiveButton(tr("OK"),null).show();return;}final File file=found;new AlertDialog.Builder(this).setTitle(tr("Restore settings?")).setMessage("Use "+file.getAbsolutePath()+"\nThis replaces your current settings.").setPositiveButton(tr("Restore"),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));String first=br.readLine();if(first==null||!first.startsWith("ZOMAOS-BACKUP"))throw new IOException("bad file");android.content.SharedPreferences.Editor ep=getPreferences(MODE_PRIVATE).edit(),eg=getSharedPreferences("zoma_gps",MODE_PRIVATE).edit();ep.clear();eg.clear();String line;int n=0;while((line=br.readLine())!=null){String[] x=line.split("\\|",4);if(x.length<4)continue;android.content.SharedPreferences.Editor e=x[0].equals("G")?eg:ep;String k=x[2],v=unesc(x[3]);if(x[1].equals("b"))e.putBoolean(k,Boolean.parseBoolean(v));else if(x[1].equals("i"))e.putInt(k,Integer.parseInt(v));else if(x[1].equals("l"))e.putLong(k,Long.parseLong(v));else if(x[1].equals("f"))e.putFloat(k,Float.parseFloat(v));else e.putString(k,v);n++;}br.close();ep.commit();eg.commit();toast("Restored "+n+" settings");recreate();}catch(Exception ex){toast("Could not read the backup file");}}}).setNegativeButton(tr("Cancel"),null).show();}
    String btMac="";
    void connectBluetooth(final BluetoothDevice device){stop();simulated=false;resetReadings();running=true;btMac=device.getAddress();setStatus("BT CONNECTING...");worker=new Thread(new Runnable(){public void run(){int attempt=0;long[] back={0,2000,5000,5000,5000,5000};String lastErr="";try{while(running){try{BluetoothAdapter.getDefaultAdapter().cancelDiscovery();btSocket=device.createRfcommSocketToServiceRecord(SPP);btSocket.connect();attempt=0;setStatus("BT ELM327 LIVE");pollElm(btSocket.getInputStream(),btSocket.getOutputStream());}catch(Exception e){lastErr=e.getClass().getSimpleName();}try{if(btSocket!=null)btSocket.close();}catch(Exception ignored){}if(!running)break;attempt++;if(attempt>5){setStatus("BT ERROR: "+lastErr);break;}setStatus("BT RECONNECTING...");try{Thread.sleep(back[attempt]);}catch(InterruptedException ie){break;}}}finally{stop();}}});worker.start();}
    // ELM327 standard read-only OBD mode 01 PIDs; never send reset/clear DTC or manufacturer commands.
    boolean obdBatchEnabled(){android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);return p.getBoolean("obdBatch",true)&&!p.getBoolean("obdNoBatch_"+btMac,false);}
    static final int OBD_NONE=Integer.MIN_VALUE;
    static int obdLen(String pid){if(pid.equals("0D")||pid.equals("05")||pid.equals("04")||pid.equals("2F"))return 1;if(pid.equals("0C"))return 2;return -1;}
    static int obdVal(String pid,int a,int b){if(pid.equals("0D"))return a;if(pid.equals("0C"))return (a*256+b)/4;if(pid.equals("05"))return a-40;return a*100/255;}
    // Parses one multi-PID mode 01 reply into {speed,rpm,coolant,load,fuel}; OBD_NONE when a value is absent.
    static int[] parseMulti(String raw){int[] o={OBD_NONE,OBD_NONE,OBD_NONE,OBD_NONE,OBD_NONE};StringBuilder sb=new StringBuilder();for(String ln:raw.split("[\r\n]+")){String x=ln.replace(" ","").trim();if(x.length()==0)continue;int c=x.indexOf(':');if(c>=0)x=x.substring(c+1);else if(x.length()==3)continue;if(!x.matches("[0-9A-F]+"))continue;sb.append(x);}String h=sb.toString();int p=h.startsWith("41")?0:h.indexOf("41");if(p<0)return o;p+=2;while(p+2<=h.length()){String pid=h.substring(p,p+2);int n=obdLen(pid);if(n<0||p+2+2*n>h.length())break;try{int a=Integer.parseInt(h.substring(p+2,p+4),16);int b=n>1?Integer.parseInt(h.substring(p+4,p+6),16):0;int v=obdVal(pid,a,b);int k=pid.equals("0D")?0:pid.equals("0C")?1:pid.equals("05")?2:pid.equals("04")?3:4;o[k]=v;}catch(Exception e){break;}p+=2+2*n;}return o;}
    // Fast loop reads speed+RPM in one request; slow values ride along every 5th cycle. Falls back to one PID per request if the adapter ignores batching, and remembers that per adapter.
    void pollElm(InputStream in,OutputStream out)throws IOException{command(in,out,"ATZ",3500);command(in,out,"ATE0",1500);command(in,out,"ATL0",1500);command(in,out,"ATS0",1500);command(in,out,"ATH0",1500);command(in,out,"ATSP0",2000);boolean batch=obdBatchEnabled();int misses=0,bfail=0,empty=0,cycle=0,s=-1,r=-1,t=-1,l=-1,f=-1;while(running){if(dtcRequest){dtcRequest=false;readDtcs(in,out);}boolean slow=(cycle%5==0);cycle++;int cs=-1,cr=-1;if(batch){int[] v=parseMulti(command(in,out,slow?"010D0C05042F":"010D0C",1800));cs=v[0]!=OBD_NONE?v[0]:-1;cr=v[1]!=OBD_NONE?v[1]:-1;if(slow){if(v[2]!=OBD_NONE)t=v[2];if(v[3]!=OBD_NONE)l=v[3];if(v[4]!=OBD_NONE)f=v[4];}if(cs<0&&cr<0){bfail++;if(bfail>=3){batch=false;bfail=0;getPreferences(MODE_PRIVATE).edit().putBoolean("obdNoBatch_"+btMac,true).commit();}}else bfail=0;}else{cs=readPid(in,out,"010D",1);cr=readPid(in,out,"010C",2);if(slow){t=readPid(in,out,"0105",1);l=readPid(in,out,"0104",1);f=readPid(in,out,"012F",1);}}s=cs;r=cr;if(s<0&&r<0){misses++;empty++;if(misses>3)setStatus("OBD NO ECU RESPONSE");if(empty>=3){empty=0;while(in.available()>0)in.read();command(in,out,"ATSP0",2000);}}else{misses=0;empty=0;setStatus("BT ELM327 LIVE");}setSample(s,r,t,l,f);if(cycle==3)readObdOdo(in,out);try{Thread.sleep(20);}catch(InterruptedException e){break;}}}
    String command(InputStream in,OutputStream out,String cmd,int timeout)throws IOException{out.write((cmd+"\r").getBytes("US-ASCII"));out.flush();StringBuilder sb=new StringBuilder();long until=System.currentTimeMillis()+timeout;while(running&&System.currentTimeMillis()<until){if(in.available()>0){int b=in.read();if(b<0)throw new EOFException();if(b=='>')break;if(sb.length()<1500)sb.append((char)b);}else try{Thread.sleep(15);}catch(InterruptedException e){throw new IOException(e);}}return sb.toString().toUpperCase(Locale.US);}
    int readPid(InputStream in,OutputStream out,String cmd,int bytes)throws IOException{String response=command(in,out,cmd,1800).replaceAll("[^0-9A-F]"," ");String hex=response.replace(" ","");String prefix="41"+cmd.substring(2);int pos=hex.indexOf(prefix);if(pos<0||hex.length()<pos+prefix.length()+bytes*2)return -1;try{int a=Integer.parseInt(hex.substring(pos+4,pos+6),16);int b=bytes>1?Integer.parseInt(hex.substring(pos+6,pos+8),16):0;if(cmd.equals("010D"))return a;if(cmd.equals("010C"))return (a*256+b)/4;if(cmd.equals("0105"))return a-40;if(cmd.equals("0104")||cmd.equals("012F"))return a*100/255;}catch(Exception ignored){}return -1;}
    void launch(String[] packages,String fallback){for(String pkg:packages){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent!=null){startActivity(intent);return;}}if("settings".equals(fallback)){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));return;}if("apps".equals(fallback)){startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS));return;}toast("App not found");}
    void toast(String message){Toast.makeText(this,tr(message),Toast.LENGTH_LONG).show();}
    class Dashboard extends View {
        final Paint p=new Paint(3); Canvas c;
        final float[] premiumDockCenters={153f,292f,435f,578f,722f};
        // Time-integrated texture travel avoids jumps when Android drops frames.
        private long roadFrameTick=0L;
        private float roadTravelPx=0f;
        // Reusable objects: nothing below is allocated per frame (API 16 GC stutter).
        private float dashPhase=0f;
        private final RectF tmpRect=new RectF();
        private final RectF backdropRect=new RectF(0f,0f,800f,480f);
        private final Matrix roadMatrix=new Matrix();
        private Matrix roadBaseMatrix;
        private Path roadShapePath;
        private final Path fxPath=new Path();
        private final Paint fxPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint contactPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF contactOval=new RectF(308f,284f,606f,307f);
        private Shader[] contactShaders;
        private Shader underGlowShader,headGlowShader;
        private Bitmap premiumCarNight;
        private Typeface faceBold,faceNormal;
        private String cachedTitleSrc,cachedTitle,cachedArtistSrc,cachedArtist;
        RectF rf(float l,float t,float r,float b){tmpRect.set(l,t,r,b);return tmpRect;}
        Typeface face(boolean bold){
            if(faceBold==null){faceBold=Typeface.create("sans-serif",Typeface.BOLD);faceNormal=Typeface.create("sans-serif",Typeface.NORMAL);}
            return bold?faceBold:faceNormal;
        }
        final float[] premiumIconScale={1.06f,1.08f,1.10f,1.12f,1.08f};
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
        void text(String v,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(face(bold));p.setTextSize(size);c.drawText(v,x,y,p);}
        void fitText(String v,float x,float y,float size,float maxWidth,int color,boolean bold){
            fill(color);p.setTypeface(face(bold));
            p.setTextSize(size);
            if(p.measureText(v)>maxWidth)p.setTextSize(size*maxWidth/p.measureText(v));
            c.drawText(v,x,y,p);
        }
        void fitCenteredText(String v,float x,float y,float size,float maxWidth,int color,boolean bold){
            fill(color);p.setTypeface(face(bold));
            p.setTextSize(size);float measured=p.measureText(v);
            if(measured>maxWidth)p.setTextSize(size*maxWidth/measured);
            c.drawText(v,x-p.measureText(v)/2,y,p);
        }
        void centerText(String v,float x,float y,float size,int color,boolean bold){fill(color);p.setTypeface(face(bold));p.setTextSize(size);c.drawText(v,x-p.measureText(v)/2,y,p);}
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
                android.graphics.drawable.Drawable icon=cachedIcon(pkg);if(icon==null)throw new Exception();
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
            String[] nav=trA(new String[]{"HOME","MUSIC","RADIO","AUTO","OBD","APPS","SET"});
            for(int i=0;i<nav.length;i++){
                if(i==0)box(5,75,93,122,col("#087F89"));
                text(nav[i],14,108+i*43,12,Color.WHITE,true);
            }
            box(105,74,377,165,col("#244A60"));
            box(103,192,468,338,col("#18384D"));
            box(474,195,624,300,col("#18384D"));
            box(631,192,791,341,col("#1B3B50"));
            box(98,347,800,410,col("#17394C"));
            String[] actions=trA(new String[]{"USB FILES","FM RADIO","ANDROID AUTO","OBD","APPS"});
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
            String[] side=trA(new String[]{"HOME","MUSIC","RADIO","AUTO","OBD","APPS","SET"});
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
            text(getPreferences(MODE_PRIVATE).getString("last_audio_source","").equals("fm")?(musicActive()?"RADIO PLAYING":"RADIO SELECTED"):"OPEN FACTORY RADIO",119,341,12,muted,false);
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
        // Standalone speed widget for the three static themes: small round glass gauge, accent taken from the theme.
        // (mx,my) = centre. Sits in the gap between the dial and the car, a little below the dial's centre line.
        // Demo mark only in demo; real OBD stays clean.
        void drawSpeedCapsule(float mx,float my,int accent,boolean day){
            final float R=33f,k=R/29f;
            p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);
            p.setColor(day?Color.argb(180,10,28,48):Color.argb(165,6,11,20));
            c.drawCircle(mx,my,R,p);
            final RectF arc=new RectF(mx-R+4,my-R+4,mx+R-4,my+R-4);
            final float fr=speed<0?0f:Math.max(0f,Math.min(1f,speed/200f));
            final int ac=fr>.65f?col("#FFB040"):accent;
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(1.2f);p.setColor(Color.argb(150,Color.red(accent),Color.green(accent),Color.blue(accent)));
            c.drawCircle(mx,my,R,p);
            p.setStrokeWidth(3f);p.setColor(Color.argb(60,255,255,255));
            c.drawArc(arc,135,270,false,p);
            if(fr>.01f){
                p.setStrokeWidth(6f);p.setColor(Color.argb(50,Color.red(ac),Color.green(ac),Color.blue(ac)));
                c.drawArc(arc,135,270*fr,false,p);
                p.setStrokeWidth(3f);p.setColor(ac);
                c.drawArc(arc,135,270*fr,false,p);
            }
            p.setStyle(Paint.Style.FILL);
            centerText(speed>=0?String.valueOf(speed):"--",mx,my+5.5f*k,19*k,Color.WHITE,true);
            centerText("km/h",mx,my+16.5f*k,7.5f*k,Color.argb(210,200,220,235),false);
            if(simulated)centerText("DEMO",mx,my-9.5f*k,7.5f*k,col("#FFCE65"),true);
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
            {
                // Hands are ALWAYS drawn so the theme keeps its analog identity. Speed lives in its own
                // glass capsule between the clock and the car (see drawSpeedCapsule), never inside the dial.
                int handColor=day?col("#E5F4FF"):Color.WHITE;
                premiumHand(cx,cy,(float)(hour*Math.PI/6-Math.PI/2),49,9,handColor);
                premiumHand(cx,cy,(float)(minute*Math.PI/30-Math.PI/2),70,6,handColor);
                premiumHand(cx,cy,(float)(sec*Math.PI/30-Math.PI/2),77,1.8f,accent);
                fill(col("#233748"));c.drawCircle(cx,cy,6,p);
                fill(col("#EAF6FF"));c.drawCircle(cx,cy,2.8f,p);
            }
            if(speedWidgetOn()&&(speed>=0||simulated))drawSpeedCapsule(cx+158f,cy+44f,accent,day);
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
            // Names under the five dock icons (the artwork has icons only), so FM RADIO etc. are readable. Cheap: five short strings.
            for(int di=0;di<5;di++){
                String dl=dockLabel(di);if(dl.length()>13)dl=dl.substring(0,12)+".";
                centerText(dl,(dockEdges[di]+dockEdges[di+1])/2f,455f,10f,day?col("#1B3550"):Color.WHITE,true);
            }
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
            // The original theme JPG is cleaned at build time; do not paint tiles over it.
            int color=day?col("#1679C4"):col("#C5EDFF");
            p.reset();p.setAntiAlias(true);p.setColor(color);
            for(int row=0;row<2;row++)for(int column=0;column<2;column++){
                float l=x-17+column*20,t=y-19+row*20;
                c.drawRoundRect(new RectF(l,t,l+14,t+14),2,2,p);
            }
        }
        // Lightweight vector-only premium scene: no video, OpenGL, image frames or allocations of bitmaps.
        // Isolated from the approved classic themes; touch hotspots and music controls remain unchanged.
        void wheelGlint(float cx,float cy,float rx,float ry,float phase){
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.1f);
            p.setColor(Color.argb(95,170,218,236));
            for(int spoke=0;spoke<5;spoke++){
                float a=phase+spoke*6.2831853f/5f;
                c.drawLine(cx+(float)Math.cos(a)*rx*.38f,cy+(float)Math.sin(a)*ry*.38f,
                    cx+(float)Math.cos(a)*rx*.86f,cy+(float)Math.sin(a)*ry*.86f,p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        final float[] WHEEL_X={302f,381f,590f};
        final float[] WHEEL_Y={278f,300f,302f};
        final float[] WHEEL_R={19f,27f,23f};
        final PorterDuffXfermode ADD_MODE=new PorterDuffXfermode(PorterDuff.Mode.ADD);
        // Night version of the car, baked once: darker body, cyan wash on the lower
        // panels and a soft rim light on the right edge, so it sits in the scene.
        // Moving lane dashes on the road centre line plus light streaks along the neon
        // edges. phase runs 0..1; depth is squared so objects speed up toward the viewer.
        // Night redesign lanes: 8 straight light lanes toward a shallow vanishing point on the left.
        // Pulses travel away from the viewer toward the vanishing point, so the car reads as driving forward.
        final float[] V6_LX={60f,200f,330f,470f,640f,830f,1100f,1500f};
        final int[][] V6_LC={{110,180,255},{120,190,255},{100,175,255},{90,170,255},{130,195,255},{150,205,255},{150,205,255},{120,190,255}};
        final float[] V6_LW={.55f,.8f,.6f,.5f,.8f,.95f,1f,.8f};
        private float wheelTravel;
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
        // Soft contact shadows under each tyre plus a faint body shadow, tinted blue-black to sit in the night scene.
        // One 100px radial gradient is reused for every ellipse by scaling the canvas.
        private Shader v6ShadowShader;
        // Night redesign car: graded press-photo cutout, fixed ground contact (no bob), rims
        // rotate in place through 12 pre-baked states with a short arc blur.
        private ColorMatrixColorFilter v6DayGrade;
        private final Rect v6Src=new Rect();
        // V16 hybrid cockpit: fixed 800x480 composition; motion uses inexpensive Canvas primitives.
        // The factory master stays untouched underneath so music, dock, and touch areas keep working.
        // Premium cockpit layers: car/road artwork contains NO baked gauges.
        // Two display-only chips in the clock's row, same fill and shape as the clock chip.
        void drawInfoChips(){
            long now=System.currentTimeMillis();
            double la=GpsService.lastLat,lo=GpsService.lastLon;
            if(Double.isNaN(la)||Double.isNaN(lo)){la=30.0444;lo=31.2357;}
            if(prayerOn){
            int[] np=nextPrayer(now,la,lo);
            String[] en={"FAJR","DHUHR","ASR","MAGHRIB","ISHA"};
            String[] arn={"\u0627\u0644\u0641\u062c\u0631","\u0627\u0644\u0638\u0647\u0631","\u0627\u0644\u0639\u0635\u0631","\u0627\u0644\u0645\u063a\u0631\u0628","\u0627\u0644\u0639\u0634\u0627\u0621"};
            String cd=(np[1]/60)+":"+(np[1]%60<10?"0":"")+(np[1]%60);
            final int gold=Color.rgb(231,197,139),cyan=Color.rgb(45,227,255);
            fill(Color.argb(130,4,15,25));
            c.drawRoundRect(new RectF(571,8,671,46),10,10,p);
            fitCenteredText(ar?arn[np[0]]:en[np[0]],621,22,11,86,gold,true);
            centerText(cd,621,41,18,Color.WHITE,true);
            }
            final int cyan=Color.rgb(45,227,255);
            boolean wx=infoChips&&GpsService.wxTemp>-900&&now-GpsService.wxAt<45L*60000L;
            if(wx){
                fill(Color.argb(130,4,15,25));
                c.drawRoundRect(new RectF(463,8,563,46),10,10,p);
                String top=GpsService.wxArea.length()>0?GpsService.wxArea.toUpperCase(Locale.ENGLISH):wxWord(GpsService.wxCode);
                fitCenteredText(top,513,22,11,92,cyan,true);
                centerText(GpsService.wxTemp+"\u00b0C",513,41,18,Color.WHITE,true);
            }
        }
        // Gauge polish: eased needle/arc, gradient arc with glow and tip, red/amber warning zone that pulses when entered.
        private final float[] gShown=new float[2];
        private final Shader[] gShader=new Shader[2];
        private float gaugeDt=.033f;private boolean gShaderDay=false;
        private final Bitmap[] gDial=new Bitmap[2];private final int[] gDialKey=new int[2];
        void ensureDial(int gi,int maxReading,int color,float zoneStart,int zone){
            final int key=maxReading*131+color*7+(enNow?1:0)+(enDayNow?2:0);
            if(gDial[gi]!=null&&gDialKey[gi]==key)return;
            try{
                final float K=1.5f;final int S=(int)(168*K);
                if(gDial[gi]!=null)gDial[gi].recycle();
                Bitmap bm=Bitmap.createBitmap(S,S,Bitmap.Config.ARGB_8888);
                Canvas bc=new Canvas(bm);bc.scale(K,K);bc.translate(84f,84f);
                Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG);bp.setTypeface(faceBold!=null?faceBold:Typeface.DEFAULT_BOLD);
                final int cr=Color.red(color),cg=Color.green(color),cb=Color.blue(color);
                // glass disc, slightly lighter towards the top
                if(enDayNow){bp.setStyle(Paint.Style.FILL);bp.setColor(Color.argb(200,255,255,255));bc.drawCircle(0,0,78f,bp);}
                else for(int k=0;k<6;k++){bp.setStyle(Paint.Style.FILL);bp.setColor(Color.argb(170,5+k,16+k*2,29+k*3));bc.drawCircle(0,0,78f-k*1.2f,bp);}
                if(enNow){
                    bp.setStyle(Paint.Style.STROKE);
                    for(int g=0;g<4;g++){bp.setStrokeWidth(14f-g*3.5f);bp.setColor(Color.argb(22+g*16,cr,cg,cb));bc.drawCircle(0,0,80f,bp);}
                }
                bp.setStyle(Paint.Style.STROKE);bp.setStrokeWidth(1.8f);bp.setColor(Color.argb(230,cr,cg,cb));bc.drawCircle(0,0,80f,bp);
                bp.setStrokeWidth(.8f);bp.setColor(enDayNow?Color.argb(120,150,175,200):Color.argb(150,30,110,130));bc.drawCircle(0,0,74f,bp);
                RectF ar=new RectF(-68,-68,68,68);
                bp.setStrokeWidth(5f);bp.setStrokeCap(Paint.Cap.BUTT);bp.setColor(enDayNow?Color.argb(120,150,170,195):Color.argb(150,30,60,80));bc.drawArc(ar,145,250,false,bp);
                bp.setColor(Color.argb(120,Color.red(zone),Color.green(zone),Color.blue(zone)));bc.drawArc(ar,145+250*zoneStart,250*(1f-zoneStart),false,bp);
                final boolean rpm=gi==1;
                final int minor=rpm?500:10,major=rpm?1000:20,labelEvery=rpm?1000:40;
                for(int v=0;v<=maxReading;v+=minor){
                    final double ang=Math.toRadians(145+250.0*v/maxReading);final float ox=(float)Math.cos(ang),oy=(float)Math.sin(ang);
                    final boolean mj=v%major==0;
                    bp.setStrokeWidth(mj?2.2f:1.1f);bp.setColor(v/(float)maxReading>=zoneStart-.001f?Color.argb(230,Color.red(zone),Color.green(zone),Color.blue(zone)):mj?(enDayNow?Color.argb(235,30,55,85):Color.argb(235,205,235,245)):(enDayNow?Color.argb(170,90,110,135):Color.argb(150,110,150,170)));
                    float r0=mj?53f:57f;bc.drawLine(ox*r0,oy*r0,ox*62f,oy*62f,bp);
                }
                bp.setStyle(Paint.Style.FILL);bp.setTextSize(8f);bp.setTextAlign(Paint.Align.CENTER);bp.setColor(enDayNow?Color.argb(235,50,75,100):Color.argb(235,150,190,205));
                for(int v=0;v<=maxReading;v+=labelEvery){
                    final double ang=Math.toRadians(145+250.0*v/maxReading);
                    bc.drawText(String.valueOf(rpm?v/1000:v),(float)Math.cos(ang)*44f,(float)Math.sin(ang)*44f+3f,bp);
                }
                // soft glass highlight on the upper part
                bp.setColor(Color.argb(14,255,255,255));bc.drawOval(new RectF(-58,-76,58,-24),bp);
                gDial[gi]=bm;gDialKey[gi]=key;
            }catch(Throwable err){gDial[gi]=null;android.util.Log.w("ZomaOS","Dial bitmap failed",err);}
        }
        // Built from primitive Canvas geometry: no missing Unicode/font glyphs on API 16.
        // Font-independent media controls for the factory Android 4.1 display.
        // One shared tile style for the side rail and the bottom dock (night scene): same surface, border and glow.
        boolean enNow=false;
        final int[] EN_RAIL={Color.rgb(42,223,250),Color.rgb(255,95,160),Color.rgb(70,220,120),Color.rgb(255,170,60),Color.rgb(200,215,235)};
        int enLight(int col){return Color.rgb(Math.min(255,Color.red(col)+60),Math.min(255,Color.green(col)+60),Math.min(255,Color.blue(col)+60));}
        // Soft colored halo plus a glass disc behind an icon.
        void enBadge(float x,float y,float r,int col){
            glowPaint.setShader(null);glowPaint.setXfermode(null);glowPaint.setStyle(Paint.Style.FILL);
            for(int k=0;k<4;k++){glowPaint.setColor(Color.argb(14+k*5,Color.red(col),Color.green(col),Color.blue(col)));c.drawCircle(x,y,r+8f-k*2.5f,glowPaint);}
            fill(Color.argb(70,Color.red(col),Color.green(col),Color.blue(col)));c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(Color.argb(170,Color.red(col),Color.green(col),Color.blue(col)));c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.FILL);
        }
        int tileTint=0;
        final int[] EN_TINT={Color.rgb(160,90,255),Color.rgb(255,140,50),Color.rgb(60,140,255),Color.rgb(60,210,110),Color.rgb(240,190,60)};
        // Independent premium skin: no legacy JPG is drawn in this mode.
        // All interactive regions deliberately retain V15 coordinates.
        void drawApproved(){
            drawMasterV15();
        }
        void drawV11(){drawApproved();}
        // Welcome screen: black, a light band sweeps across and wakes the logo, name and greeting. Shaders built once.
        private Shader welcomeSweep;private int slowFrames=0;
        boolean drawWelcome(){
            long el=android.os.SystemClock.uptimeMillis()-welcomeStart;
            if(welcomeStart==0L||el>=7000L){welcomeStart=0L;return false;}
            long t0=System.nanoTime();
            fill(Color.BLACK);c.drawRect(0,0,800,480,p);
            float lit=Math.max(0f,Math.min(1f,(el-900f)/2600f));
            float fade=el>6000L?1f-(el-6000L)/1000f:1f;
            float sx=-120f+Math.max(0f,Math.min(1f,(el-600f)/3000f))*1040f;
            if(el>=600L&&el<3800L){
                if(welcomeSweep==null)welcomeSweep=new LinearGradient(-90f,0f,90f,0f,new int[]{0,Color.argb(120,60,150,255),0},new float[]{0f,.5f,1f},Shader.TileMode.CLAMP);
                c.save();c.translate(sx,0f);p.setStyle(Paint.Style.FILL);p.setShader(welcomeSweep);c.drawRect(-90f,0f,90f,480f,p);p.setShader(null);c.restore();
            }
            int al=(int)(255*(0.12f+0.88f*lit)*fade);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6f);p.setColor(Color.argb(al,60,200,255));c.drawCircle(400f,150f,52f,p);
            centerText("Z",400f,172f,64f,Color.argb(al,255,255,255),true);
            centerText("ZomaOS",400f,254f,50f,Color.argb(al,235,245,250),true);
            String greet=(ar?"\u0623\u0647\u0644\u0627\u064b":"Welcome")+(userName.length()>0?(ar?" ":", ")+userName:"");
            int ga=(int)(255*Math.max(0f,Math.min(1f,(el-1800f)/2200f))*fade);
            centerText(greet,400f,308f,26f,Color.argb(ga,255,200,90),true);
            // On a slow unit, drop the welcome instead of making the launcher stutter.
            if((System.nanoTime()-t0)/1000000L>60L){if(++slowFrames>=3)welcomeStart=0L;}else slowFrames=0;
            return true;
        }
        @Override protected void onDraw(Canvas canvas){
            c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);
            if(welcomeStart!=0L&&drawWelcome()){c.restore();postInvalidateDelayed(50);return;}
            drawV11();c.restore();
            // Keep the configurable conservative frame cap on API 16.
            // postInvalidateOnAnimationDelayed does not exist on View;
            // postInvalidateDelayed is the compatible scheduling API.
            postInvalidateDelayed((premiumMotion&&overlays<=0)?(1000/premiumFps):1000);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(welcomeStart!=0L){welcomeStart=0L;invalidate();return true;}
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
                boolean onSeek;
                onSeek=y>=341&&y<=355&&x>=(appearance==1?203:186)&&x<=(appearance==1?510:518);
                if(onSeek&&localPlayer!=null){draggingSeek=true;seekMusic(x);return true;}
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
            // One compact Settings control; no redundant HOME button.
            if(premiumMotion&&y>=0&&y<=58){
                if(x>=8&&x<=98){showZomaSettings();return true;}
            }
            if(premiumMotion&&(infoChips||prayerOn)&&y<=50&&x>=455&&x<=672)return true; // info chips are display only
            // Header quick links open the actual system controls (not decorative labels).
            if(y>=0&&y<=40){
                if(x>=525&&x<580){try{startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));}catch(Throwable settingsError){launch(new String[]{},"settings");}return true;}
                if(x>=580&&x<640){try{startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Throwable settingsError){launch(new String[]{},"settings");}return true;}
            }
            // Premium has four navigation icons at y=117,183,249,315,
            // and a separate settings gear at the top. Touch zones must
            // align with visible icons, even on resistive 800x480 panels.
            if(premiumMotion&&x<105){
                if(y>=90&&y<150)return true; // Home
                if(y>=150&&y<216){openNavApp();return true;}
                if(y>=216&&y<282){musicHub();return true;}
                if(y>=282&&y<348){openFactoryPhone();return true;}
                return true;
            }
            // Legacy V15 artwork retains its original five-icon sidebar.
            if(!premiumMotion&&x<107&&y>=65&&y<354){
                if(y<125)return true;
                if(y<183){openAndroidAuto();return true;}
                if(y<240){musicHub();return true;}
                if(y<298){openFactoryPhone();return true;}
                showZomaSettings();return true;
            }
            // Media controls are inside the live 130..767 x 260..350 panel.
            if(y>=310&&y<=380&&x>=521&&x<=744){
                if(x<562)toggleShuffle();
                else if(x<609){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else if(fmMode())radioKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);else openRememberedUsb();}
                else if(x<655){if(fmMode()&&localPlayer==null)radioKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);else togglePlayback();}
                else if(x<703){if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else if(fmMode())radioKey(KeyEvent.KEYCODE_MEDIA_NEXT);else openRememberedUsb();}
                else cycleRepeat();
                return true;
            }
            if(y>=28&&y<=285&&x>=105&&x<=357){return true;}
            if(y>=310&&y<=380&&x>=130&&x<521){if(fmMode())openRadio();else openRememberedUsb();return true;}
            // Do not open Settings from the decorative top status/header area.
            return true;
        }

    }

    /** Receives NMEA from a phone (Bluetooth SPP or TCP over Wi-Fi) and publishes it as the system "gps" test provider. */
    public static class GpsService extends Service {
        public static volatile String status = "GPS OFF";
        public static volatile double lastLat = Double.NaN, lastLon = Double.NaN;
        public static volatile int wxTemp = -999, wxCode = -1;
        public static volatile String wxArea = "";
        public static volatile long wxAt = 0;
        public static volatile double lat, lon, speedKmh, bearing;
        public static volatile int sats;
        public static volatile long fixAt;
        public static volatile boolean mockOk = true;
        static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        volatile boolean run;
        volatile long lastRx;
        Thread worker, watchdog;
        volatile BluetoothSocket bt;
        volatile Socket tcp;
        LocationManager lm;
        boolean providerReady;

        public static boolean fresh() { return fixAt > 0 && System.currentTimeMillis() - fixAt < 5000; }

        @Override public IBinder onBind(Intent i) { return null; }

        @Override public int onStartCommand(Intent intent, int flags, int startId) {
            stopWork();
            SharedPreferences p = getSharedPreferences("zoma_gps", MODE_PRIVATE);
            final String mode = p.getString("mode", "off");
            final String mac = p.getString("mac", "");
            final String host = p.getString("host", "");
            if (mode.equals("off")) { status = "GPS OFF"; stopSelf(); return START_NOT_STICKY; }
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            run = true;
            lastRx = System.currentTimeMillis();
            worker = new Thread(new Runnable() { public void run() { loop(mode, mac, host); } });
            worker.start();
            watchdog = new Thread(new Runnable() { public void run() {
                while (run) {
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                    if (run && System.currentTimeMillis() - lastRx > 20000) closeLink();
                }
            } });
            watchdog.start();
            return START_STICKY;
        }

        void closeLink() {
            try { if (bt != null) bt.close(); } catch (Exception ignored) {}
            try { if (tcp != null) tcp.close(); } catch (Exception ignored) {}
        }

        void stopWork() {
            run = false;
            closeLink();
            if (worker != null) worker.interrupt();
            if (watchdog != null) watchdog.interrupt();
            removeProvider();
        }

        @Override public void onDestroy() { stopWork(); status = "GPS OFF"; super.onDestroy(); }

        void loop(String mode, String mac, String host) {
            while (run) {
                try {
                    BufferedReader in;
                    status = "GPS CONNECTING...";
                    if (mode.equals("bt")) {
                        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
                        if (ad == null) throw new Exception("No Bluetooth API");
                        ad.cancelDiscovery();
                        BluetoothDevice d = ad.getRemoteDevice(mac);
                        bt = d.createRfcommSocketToServiceRecord(SPP);
                        bt.connect();
                        in = new BufferedReader(new InputStreamReader(bt.getInputStream(), "US-ASCII"));
                    } else {
                        String[] hp = host.split(":");
                        int port = 10110;
                        try { if (hp.length > 1) port = Integer.parseInt(hp[1].trim()); } catch (Exception ignored) {}
                        ArrayList<String> cands = new ArrayList<String>();
                        String typed = hp[0].trim();
                        if (typed.length() > 0 && !typed.equalsIgnoreCase("auto")) cands.add(typed);
                        String gw = gatewayIp();
                        if (gw != null && !cands.contains(gw)) cands.add(gw);
                        if (cands.isEmpty()) throw new Exception("unit is not on Wi-Fi");
                        Exception last = null;
                        Socket got = null;
                        for (String c : cands) {
                            if (!run) break;
                            status = "GPS CONNECTING " + c + ":" + port;
                            Socket sk = new Socket();
                            try { sk.connect(new InetSocketAddress(c, port), 4000); got = sk; break; }
                            catch (Exception ce) { last = ce; try { sk.close(); } catch (Exception ignored) {} }
                        }
                        if (got == null) throw new Exception("no answer from " + cands + " port " + port + (last != null ? " (" + last.getClass().getSimpleName() + ")" : ""));
                        tcp = got;
                        in = new BufferedReader(new InputStreamReader(tcp.getInputStream(), "US-ASCII"));
                    }
                    lastRx = System.currentTimeMillis();
                    status = "PHONE LINKED, WAITING FOR FIX";
                    setupProvider();
                    String line;
                    while (run && (line = in.readLine()) != null) {
                        lastRx = System.currentTimeMillis();
                        handle(line.trim());
                    }
                } catch (Exception e) {
                    if (run) { String em = String.valueOf(e.getMessage()); if (em.length() > 100) em = em.substring(0, 100); status = "GPS LINK LOST (" + e.getClass().getSimpleName() + ": " + em + "), RETRYING"; }
                }
                closeLink();
                if (!run) break;
                try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
            }
        }

        String gatewayIp() {
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getSystemService(Context.WIFI_SERVICE);
                int g = wm.getDhcpInfo().gateway;
                if (g == 0) return null;
                return (g & 0xff) + "." + ((g >> 8) & 0xff) + "." + ((g >> 16) & 0xff) + "." + ((g >> 24) & 0xff);
            } catch (Exception e) { return null; }
        }

        static boolean checksumOk(String s) {
            int star = s.indexOf('*');
            if (star < 0 || star + 3 > s.length()) return true;
            int x = 0;
            for (int i = 1; i < star; i++) x ^= s.charAt(i);
            try { return x == Integer.parseInt(s.substring(star + 1, star + 3), 16); } catch (Exception e) { return false; }
        }

        static double coord(String v, String hemi) {
            if (v.length() < 4) return Double.NaN;
            int dot = v.indexOf('.');
            if (dot < 3) return Double.NaN;
            double deg = Double.parseDouble(v.substring(0, dot - 2));
            double min = Double.parseDouble(v.substring(dot - 2));
            double r = deg + min / 60.0;
            return (hemi.equals("S") || hemi.equals("W")) ? -r : r;
        }

        void handle(String s) {
            if (!s.startsWith("$") || s.length() < 10 || !checksumOk(s)) return;
            int star = s.indexOf('*');
            String body = star > 0 ? s.substring(0, star) : s;
            String[] f = body.split(",", -1);
            if (f[0].equals("$ZMWX") && f.length >= 4) {
                try { wxTemp = Integer.parseInt(f[1].trim()); wxCode = Integer.parseInt(f[2].trim()); wxArea = f[3].trim(); wxAt = System.currentTimeMillis(); } catch (Exception ignored) {}
                return;
            }
            String type = f[0].length() >= 6 ? f[0].substring(3) : "";
            try {
                if (type.equals("GGA") && f.length > 7) {
                    try { sats = Integer.parseInt(f[7]); } catch (Exception ignored) {}
                } else if (type.equals("RMC") && f.length > 8 && f[2].equals("A")) {
                    double la = coord(f[3], f[4]), lo = coord(f[5], f[6]);
                    if (Double.isNaN(la) || Double.isNaN(lo)) return;
                    double kn = f[7].length() > 0 ? Double.parseDouble(f[7]) : 0;
                    double brg = f[8].length() > 0 ? Double.parseDouble(f[8]) : 0;
                    lastLat = la; lastLon = lo; lat = la; lon = lo; speedKmh = kn * 1.852; bearing = brg; fixAt = System.currentTimeMillis();
                    status = "PHONE GPS LIVE";
                    publish();
                }
            } catch (Exception ignored) {}
        }

        void setupProvider() {
            try {
                try { lm.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
                lm.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
                lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                providerReady = true;
                mockOk = true;
            } catch (SecurityException e) {
                providerReady = false;
                mockOk = false;
                status = "ENABLE MOCK LOCATIONS IN DEVELOPER OPTIONS";
            } catch (Exception e) {
                providerReady = false;
            }
        }

        void publish() {
            if (!providerReady) return;
            try {
                Location l = new Location(LocationManager.GPS_PROVIDER);
                l.setLatitude(lat); l.setLongitude(lon);
                l.setSpeed((float) (speedKmh / 3.6)); l.setBearing((float) bearing);
                l.setAccuracy(8f); l.setTime(System.currentTimeMillis());
                if (android.os.Build.VERSION.SDK_INT >= 17) l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                lm.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
                lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, l);
            } catch (SecurityException e) {
                providerReady = false; mockOk = false;
                status = "ENABLE MOCK LOCATIONS IN DEVELOPER OPTIONS";
            } catch (Exception ignored) {}
        }

        void removeProvider() {
            try { if (lm != null && providerReady) lm.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            providerReady = false;
        }
    }
}
