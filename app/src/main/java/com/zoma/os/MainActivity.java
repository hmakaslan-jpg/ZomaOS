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
    boolean resumeAttempted=false;
    final Handler resumeHandler=new Handler();
    final Runnable resumeTick=new Runnable(){public void run(){saveUsbPlaybackState();resumeHandler.postDelayed(this,5000);}};
    Bitmap concept; Bitmap dayConcept; Bitmap albumArt; boolean draggingSeek=false; boolean showAnalog=true; int selectedPage=0;
    AudioManager audioManager; boolean hasAudioFocus=false; boolean clock12=false; int appearance=0;
    AudioManager.OnAudioFocusChangeListener focusListener=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){pauseAudio();}}};
    // User-configurable dock. Empty preference means the original built-in action.
    final String[] dockDefaults={"USB MUSIC","RADIO FM","ANDROID AUTO","OBD","FILE BROWSER"};
    final int[] dockEdges={90,225,359,512,642,797};
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
        if(index==0)openRememberedUsb();
        else if(index==1)openRadio();
        else if(index==2){stopAudio();launch(new String[]{"me.ri3d.openauto"},"");}
        else if(index==3)showMode();
        else if(index==4)openFileBrowser();
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
    int masterDockIndex(float x){if(x<65||x>=795)return -1;return Math.min(4,(int)((x-65)/146));}
    int localIndex=-1;
    boolean localMode=false;


    int centerMode=0; // 0 road, 1 analog, 2 digital
    int theme=0; // 0 cyan, 1 amber, 2 emerald
    final Handler ui=new Handler();
    final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override public void onCreate(Bundle b){super.onCreate(b);centerMode=getPreferences(MODE_PRIVATE).getInt("centerMode",0);theme=getPreferences(MODE_PRIVATE).getInt("theme",0);appearance=getPreferences(MODE_PRIVATE).getInt("appearance",0);if(appearance<0||appearance>2)appearance=0;carbonClock=getPreferences(MODE_PRIVATE).getBoolean("carbonClock",true);loadN14Skin();showAnalog=getPreferences(MODE_PRIVATE).getBoolean("showAnalog",true);clock12=getPreferences(MODE_PRIVATE).getBoolean("clock12",false);audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);dashboard=new Dashboard();setContentView(dashboard);startMusicDiscovery();restoreUsbFolder();resumeHandler.postDelayed(resumeTick,5000);resumeLastUsbOnce();}
    // Load only the selected scene and eight small themed assets. No GIF or frame loop.
    void recycleN14(Bitmap b){if(b!=null&&!b.isRecycled())b.recycle();}
    void loadN14Skin(){
        recycleN14(masterArtwork); masterArtwork=null;
        int masterId=appearance==2?R.drawable.master_carbon:appearance==1?R.drawable.master_pearl:R.drawable.master_electric;
        masterArtwork=BitmapFactory.decodeResource(getResources(),masterId);
        recycleN14(v147CarScene);v147CarScene=null;
        int carRes=appearance==2?R.drawable.v147_car_scene_carbon:appearance==1?R.drawable.v147_car_scene_pearl:R.drawable.v147_car_scene_electric;
        v147CarScene=BitmapFactory.decodeResource(getResources(),carRes);
        if(premiumIcons[0]==null){int[] iconIds={R.drawable.premium_icon_0,R.drawable.premium_icon_1,R.drawable.premium_icon_2,R.drawable.premium_icon_3,R.drawable.premium_icon_4,R.drawable.premium_icon_5,R.drawable.premium_icon_6,R.drawable.premium_icon_7,R.drawable.premium_icon_8};for(int i=0;i<iconIds.length;i++)premiumIcons[i]=BitmapFactory.decodeResource(getResources(),iconIds[i]);}
        // Keep large Carbon-only overlays out of RAM when Electric or Pearl is selected.
        if(appearance==2){
            if(carbonGaugePanel==null)carbonGaugePanel=BitmapFactory.decodeResource(getResources(),R.drawable.carbon_gauge_panel);
            if(carbonMusicPanel==null)carbonMusicPanel=BitmapFactory.decodeResource(getResources(),R.drawable.carbon_music_panel);
            if(carbonDockPanel==null)carbonDockPanel=BitmapFactory.decodeResource(getResources(),R.drawable.carbon_dock_panel);
            if(carbonSidePanel==null)carbonSidePanel=BitmapFactory.decodeResource(getResources(),R.drawable.carbon_side_panel);
            if(carbonAnalogDial==null)carbonAnalogDial=BitmapFactory.decodeResource(getResources(),R.drawable.carbon_analog_dial);
        }else{
            recycleN14(carbonGaugePanel);carbonGaugePanel=null;
            recycleN14(carbonMusicPanel);carbonMusicPanel=null;
            recycleN14(carbonDockPanel);carbonDockPanel=null;
            recycleN14(carbonSidePanel);carbonSidePanel=null;
            recycleN14(carbonAnalogDial);carbonAnalogDial=null;
        }
        recycleN14(n14Scene);recycleN14(n14SceneDay);recycleN14(n14SceneTwilight);recycleN14(n14SceneCarbon);
        n14Scene=null;n14SceneDay=null;n14SceneTwilight=null;n14SceneCarbon=null;
        Bitmap[] old={n14Header,n14Nav,n14Selected,n14Time,n14Music,n14Source,n14Dock,n14Clock};
        for(Bitmap b:old)recycleN14(b);
        int[] ids;
        if(appearance==1){
            n14SceneDay=BitmapFactory.decodeResource(getResources(),R.drawable.n14_scene_day);
            ids=new int[]{R.drawable.n14_header_day,R.drawable.n14_nav_rail_day,R.drawable.n14_nav_selected_day,R.drawable.n14_time_card_day,R.drawable.n14_music_card_day,R.drawable.n14_source_card_day,R.drawable.n14_dock_card_day,R.drawable.n14_clock_face_day};
        }else if(appearance==2){
            n14SceneCarbon=BitmapFactory.decodeResource(getResources(),R.drawable.n14_scene_carbon);
            ids=new int[]{R.drawable.n14_header_carbon,R.drawable.n14_nav_rail_carbon,R.drawable.n14_nav_selected_carbon,R.drawable.n14_time_card_carbon,R.drawable.n14_music_card_carbon,R.drawable.n14_source_card_carbon,R.drawable.n14_dock_card_carbon,R.drawable.n14_clock_face_carbon};
        }else{
            n14Scene=BitmapFactory.decodeResource(getResources(),R.drawable.n14_scene);
            ids=new int[]{R.drawable.n14_header,R.drawable.n14_nav_rail,R.drawable.n14_nav_selected,R.drawable.n14_time_card,R.drawable.n14_music_card,R.drawable.n14_source_card,R.drawable.n14_dock_card,R.drawable.n14_clock_face};
        }
        n14Header=BitmapFactory.decodeResource(getResources(),ids[0]);
        n14Nav=BitmapFactory.decodeResource(getResources(),ids[1]);
        n14Selected=BitmapFactory.decodeResource(getResources(),ids[2]);
        n14Time=BitmapFactory.decodeResource(getResources(),ids[3]);
        n14Music=BitmapFactory.decodeResource(getResources(),ids[4]);
        n14Source=BitmapFactory.decodeResource(getResources(),ids[5]);
        n14Dock=BitmapFactory.decodeResource(getResources(),ids[6]);
        n14Clock=BitmapFactory.decodeResource(getResources(),ids[7]);
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
        File[] files=folder.listFiles();if(files==null)return;
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
            trackTitle=f.getName();trackArtist="USB / "+f.getParentFile().getName();metadataSource="Zoma USB player";
            try{MediaMetadataRetriever m=new MediaMetadataRetriever();m.setDataSource(f.getAbsolutePath());String t=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);String a=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);if(t!=null&&t.length()>0)trackTitle=t;if(a!=null&&a.length()>0)trackArtist=a;byte[] art=m.getEmbeddedPicture();if(art!=null)albumArt=BitmapFactory.decodeByteArray(art,0,art.length);m.release();}catch(Exception ignored){}
            localPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer m){nextUsb(1);}});dashboard.invalidate();
        }catch(Throwable e){releaseLocalPlayer();toast("Cannot play this USB audio file");}
    }
    void nextUsb(int direction){
        if(usbTracks.isEmpty())return;
        // Dedicated track navigation: never alter the playback position within the same track.
        int base=usbIndex<0?(direction>0?-1:0):usbIndex;
        int target=(base+(direction>0?1:-1)+usbTracks.size())%usbTracks.size();
        playUsb(target);
    }
    void togglePlayback(){if(localPlayer==null){openRememberedUsb();return;}try{
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
    void chooseAppearance(){final String[] choices={"Electric Night", "Pearl Day", "Carbon Sport"};new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(choices,appearance,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){appearance=n;getPreferences(MODE_PRIVATE).edit().putInt("appearance",n).apply();loadN14Skin();dashboard.invalidate();d.dismiss();if(n==2)chooseCarbonGauge();}}).setNegativeButton("Cancel",null).show();}
    void chooseClockFormat(){final String[] choices={"24-hour", "12-hour AM/PM"};new AlertDialog.Builder(this).setTitle("Clock format").setSingleChoiceItems(choices,clock12?1:0,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){clock12=n==1;getPreferences(MODE_PRIVATE).edit().putBoolean("clock12",clock12).apply();dashboard.invalidate();d.dismiss();}}).setNegativeButton("Cancel",null).show();}
    String clockText(){return new SimpleDateFormat(clock12?"hh:mm a":"HH:mm",Locale.ENGLISH).format(new Date());}
    void showZomaSettings(){
        final String[] items={"Appearance • Night / Daylight", "Accent colors", "Clock • 12 / 24 hour", "Analog clock • Show / hide", "USB music folder", "Music player selection", "App Center", "Maintenance Center", "Oil reminder • Show / hide", "Oil interval (km)", "OBD connection", "Android system settings", "Restore ZomaOS defaults"};
        final Dialog dialog=new Dialog(this);dialog.setTitle("ZomaOS V12.4 • Settings");
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(16,10,16,10);panel.setBackgroundColor(Color.rgb(16,39,59));
        TextView heading=new TextView(this);heading.setText("ZOMAOS V12.4   /   SETTINGS");heading.setTextSize(22);heading.setTextColor(Color.WHITE);heading.setPadding(12,8,8,14);panel.addView(heading);
        ListView list=new ListView(this);list.setCacheColorHint(Color.TRANSPARENT);list.setDividerHeight(1);list.setScrollingCacheEnabled(false);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,items){@Override public View getView(int pos,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(pos,convert,parent);v.setTextColor(Color.WHITE);v.setTextSize(17);v.setPadding(14,10,8,10);return v;}};
        list.setAdapter(adapter);panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(this);close.setText("BACK TO DASHBOARD");panel.addView(close);close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> p,View v,int n,long id){dialog.dismiss();switch(n){
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
    // Coordinates are in the same 800x480 virtual canvas as drawLiveProgress.
    static final float MUSIC_SEEK_LEFT=197f;
    static final float MUSIC_SEEK_RIGHT=434f;
    void seekMusic(float x){
        if(localPlayer==null)return;
        try{
            int duration=localPlayer.getDuration();
            if(duration>0){
                float fraction=Math.max(0f,Math.min(1f,(x-MUSIC_SEEK_LEFT)/(MUSIC_SEEK_RIGHT-MUSIC_SEEK_LEFT)));
                localPlayer.seekTo((int)(duration*fraction));
                dashboard.invalidate();
            }
        }catch(Exception e){toast("Seeking unavailable");}
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
            c.drawBitmap(b,null,new RectF(l,t,r,bottom),p);
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
                case 3: // Android Auto arrow
                    Path ar=new Path();ar.moveTo(cx,cy-11);ar.lineTo(cx+10,cy+9);ar.lineTo(cx,cy+4);ar.lineTo(cx-10,cy+9);ar.close();c.drawPath(ar,p);break;
                case 4: // OBD engine
                    c.drawRect(cx-9,cy-5,cx+8,cy+7,p);c.drawLine(cx-5,cy-9,cx+4,cy-9,p);c.drawLine(cx-11,cy-2,cx-13,cy-2,p);c.drawLine(cx+9,cy+1,cx+13,cy+1,p);break;
                case 5: // apps
                    for(int i=0;i<4;i++){float x=cx-10+(i%2)*12,y=cy-9+(i/2)*12;c.drawRect(x,y,x+8,y+8,p);}break;
                case 6: // settings
                    c.drawCircle(cx,cy,8,p);c.drawCircle(cx,cy,3,p);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(cx+(float)Math.cos(angle)*9,cy+(float)Math.sin(angle)*9,cx+(float)Math.cos(angle)*12,cy+(float)Math.sin(angle)*12,p);}break;
                case 7: // folder
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
            else {centerText(speed>=0&&!simulated?String.valueOf(speed):"--",cx,cy+9,39,Color.WHITE,true);centerText("km/h",cx,cy+30,12,Color.WHITE,false);if(speed<0||simulated)centerText("NO LIVE OBD",cx,cy+47,9,red,false);}
        }
        void drawCarbonSport(){
            int red=col("#FF2738"),fg=Color.WHITE,muted=col("#B9C2D0");
            n14Image(n14SceneCarbon,0,0,800,480);
            n14Image(v147CarScene,450,48,800,248);
            n14Image(n14Header,0,0,800,40);
            n14Image(carbonSidePanel,4,42,83,469);
            String[] menu={"Home","Media","Radio","Auto","OBD","Apps","Settings"};
            for(int i=0;i<7;i++){
                float yy=50+i*56;
                if(i==0){p.reset();p.setAntiAlias(true);p.setColor(col("#9A0C15"));c.drawRoundRect(new RectF(8,yy,78,yy+49),9,9,p);}
                premiumIcon(i,43,yy+18,29);
                centerText(menu[i],43,yy+39,10,fg,false);
            }
            text("ZomaOS  NEXUS V14.8",93,26,17,fg,true);
            text("USB",565,25,11,muted,true);text("BT",617,25,11,muted,true);text("Wi-Fi",661,25,11,muted,true);
            fitCenteredText(clockText(),749,24,17,88,fg,true);
            n14Image(carbonGaugePanel,89,47,424,231);
            if(carbonClock){
                n14Image(carbonAnalogDial,166,47,344,225);
                n14ClockHands(255,136,red);
            }else carbonDial(255,136,79,false,red);
            centerText(carbonClock?"ANALOG CLOCK":"SPEEDOMETER",255,222,12,fg,true);
            text("GEELY X7",679,65,17,fg,true);
            // Auxiliary gauges intentionally inactive until connected to real vehicle sensors
            centerText("FUEL",117,110,11,muted,true);centerText("--",117,145,21,fg,true);
            centerText("TEMP",396,110,11,muted,true);centerText("--",396,145,21,fg,true);
            n14Image(carbonMusicPanel,91,236,788,350);
            if(albumArt!=null){fill(Color.WHITE);c.drawBitmap(albumArt,null,new RectF(107,249,173,329),p);}
            else premiumIcon(1,138,286,65);
            String song=(usbMode||localMode)?trackTitle:"Choose USB music";
            if(song==null||song.length()==0)song="Choose USB music";
            fitText(song,188,265,15,226,fg,true);
            String artist=(usbMode||localMode)?trackArtist:"No track selected";
            fitText(artist==null?"":artist,188,290,11,225,muted,false);
            drawLiveProgress(MUSIC_SEEK_LEFT,316,MUSIC_SEEK_RIGHT,usbMode||localMode);
            premiumIcon(8,444,274,23);
            centerText("PREV",495,318,12,fg,true);
            centerText(localPlayer!=null&&localPlayer.isPlaying()?"PAUSE":"PLAY",587,318,14,red,true);
            centerText("NEXT",691,318,12,fg,true);
            for(int i=0;i<5;i++){
                float l=dockEdges[i]+3,r=dockEdges[i+1]-4;
                n14Image(carbonDockPanel,l,363,r,435);
                premiumIcon(i==0?8:i==1?2:i==2?3:i==3?4:7,(l+r)/2,388,43);
                fitCenteredText(dockLabel(i),(l+r)/2,416,12,r-l-8,fg,true);
            }
            centerText("CARBON SPORT",420,456,14,red,true);
        }

        void drawMasterV15(){
            boolean day=appearance==1;
            int accent=appearance==2?col("#FF2738"):day?col("#1373CA"):col("#14CAFF");
            int fg=day?col("#172A40"):Color.WHITE;
            n14Image(masterArtwork,0,0,800,480);
            float cx=day?224:228, cy=day?174:154; // calibrated to the center of the master clock bezel
            Calendar now=Calendar.getInstance();
            float sec=now.get(Calendar.SECOND),minute=now.get(Calendar.MINUTE)+sec/60f,hour=now.get(Calendar.HOUR)+minute/60f;
            if(carbonClock){
                hand(cx,cy,(float)(hour*Math.PI/6-Math.PI/2),day?46:55,5,fg);
                hand(cx,cy,(float)(minute*Math.PI/30-Math.PI/2),day?65:76,3,fg);
                hand(cx,cy,(float)(sec*Math.PI/30-Math.PI/2),day?73:85,1,accent);
                fill(Color.BLACK);c.drawCircle(cx,cy,7,p);fill(accent);c.drawCircle(cx,cy,4,p);
            }else{
                centerText(speed>=0&&!simulated?String.valueOf(speed):"--",cx,cy+11,37,fg,true);
                centerText("km/h",cx,cy+29,11,fg,false);
            }
            fitCenteredText(clockText(),760,26,18,67,fg,true);
            String title=(usbMode||localMode)?trackTitle:"Choose USB music";
            String artist=(usbMode||localMode)?trackArtist:"USB MUSIC";
            if(title==null||title.length()==0)title="Choose USB music";
            if(artist==null)artist="";
            fitText(title,day?190:188,day?318:286,day?11:15,day?165:219,fg,true);
            fitText(artist,day?190:188,day?338:306,day?9:11,day?165:219,fg,false);
            // Static premium art is intentionally not animated; only hands/progress update.
        }

        void drawApproved(){
            if(masterArtwork!=null){drawMasterV15();return;}
            if(appearance==2){drawCarbonSport();return;}
            boolean day=appearance==1;
            int edge=appearance==1?col("#1265B0"):appearance==2?col("#FF3C4B"):col("#1DD6FF");
            int fg=day?col("#162A3C"):Color.WHITE;
            int muted=day?col("#315A7D"):col("#B6D0E7");
            // First real native asset integration: independent decorative assets only.
            // No clock text, song titles, seek state, or button labels are baked into these PNGs.
            n14Image(appearance==1?n14SceneDay:appearance==2?n14SceneCarbon:n14Scene,0,0,800,480);
            n14Image(v147CarScene,450,48,800,248);
            n14Image(n14Header,0,0,800,46);
            n14Image(n14Nav,4,49,91,459);
            n14Image(n14Selected,9,58,85,115);
            n14Image(n14Time,100,65,385,177);
            n14Image(n14Music,100,196,445,352);
            n14Image(n14Source,451,207,615,319);
            String[] menu={"Home","Media","Radio","Auto","OBD","Apps","Settings"};
            String[] symbols={"⌂","♫","▣","▲","◇","▦","⚙"};
            for(int i=0;i<7;i++){
                float top=58+i*56;
                premiumIcon(i,46,top+22,27);
                centerText(menu[i],46,top+45,11,fg,false);
            }
            text("ZomaOS  NEXUS V14.8",107,29,19,fg,true);
            text("USB",556,29,12,muted,true);text("BT",609,29,12,muted,true);text("Wi-Fi",656,29,12,muted,true);
            fitCenteredText(clockText(),744,24,17,108,fg,true);
            fitCenteredText(clockText(),218,122,46,222,fg,true);
            centerText(new SimpleDateFormat("EEE, MMM d, yyyy",Locale.ENGLISH).format(new Date()),227,152,13,muted,false);
            centerText("Weather --",336,151,11,muted,false);
            text("GEELY X7",688,87,17,fg,true);
            // Album art is genuinely dynamic. No fake song metadata is printed in the artwork.
            if(albumArt!=null){fill(Color.WHITE);c.drawBitmap(albumArt,null,new RectF(115,213,182,281),p);}
            else liteIcon(1,148,247,edge);
            String song=(usbMode||localMode)?trackTitle:"Choose USB music";
            if(song==null||song.length()==0)song="Choose USB music";
            fitText(song,197,225,16,238,fg,true);
            String artist=(usbMode||localMode)?trackArtist:"No track selected";
            if(artist==null)artist="";
            fitText(artist,197,248,12,237,muted,false);
            drawLiveProgress(MUSIC_SEEK_LEFT,274,MUSIC_SEEK_RIGHT,usbMode||localMode);
            centerText("PREV",221,326,14,fg,true);
            centerText(localPlayer!=null&&localPlayer.isPlaying()?"PAUSE":"PLAY",294,326,14,edge,true);
            centerText("NEXT",368,326,14,fg,true);
            text("Source",474,230,17,fg,true);
            text(usbMode?"USB MUSIC":localMode?"LOCAL MUSIC":"Select USB",474,255,15,fg,true);
            text(usbMode?"USB playback active":localMode?"Local playback active":"No source selected",474,277,11,muted,false);
            if(showAnalog){
                n14Image(n14Clock,631,182,787,338);
                if(appearance==2&&!carbonClock){
                    centerText(speed>=0&&!simulated?String.valueOf(speed):"--",709,259,40,fg,true);
                    centerText("km/h",709,283,14,edge,true);
                    if(speed<0||simulated)centerText("NO LIVE OBD",709,302,9,fg,false);
                }else n14ClockHands(709,260,edge);
            }
            String[] icons={"♫","▣","▲","◇","▱"};
            for(int i=0;i<5;i++){
                float l=dockEdges[i]+3,r=dockEdges[i+1]-4;
                n14Image(n14Dock,l,364,r,423);
                premiumIcon(i==0?8:i==1?2:i==2?3:i==3?4:7,(l+r)/2,386,36);
                fitCenteredText(dockLabel(i),(l+r)/2,410,12,r-l-10,fg,true);
            }
            fitCenteredText(appearance==0?"Electric Night":appearance==1?"Pearl Day":"Carbon Sport",403,450,18,235,edge,true);
        }
        void drawV11(){drawApproved();}
        @Override protected void onDraw(Canvas canvas){
            c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);
            drawV11();c.restore();postInvalidateDelayed(1000); // 1 FPS for clock seconds; no 30/60 FPS continuous scene animation
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();
            int a=e.getAction();
            if(a==MotionEvent.ACTION_DOWN){
                if(y>=352&&y<=454&&x>=65&&x<795){
                    pressedDock=masterDockIndex(x);dockLongPressed=false;
                    final int idx=pressedDock;
                    pendingDockLongPress=new Runnable(){public void run(){if(pressedDock==idx){dockLongPressed=true;editDock(idx);}}};
                    dockHandler.postDelayed(pendingDockLongPress,650);return true;
                }
                if(y>=(appearance==2?306:263)&&y<=(appearance==2?326:293)&&x>=MUSIC_SEEK_LEFT-6&&x<=MUSIC_SEEK_RIGHT+6&&localPlayer!=null){draggingSeek=true;seekMusic(x);return true;}
                return true;
            }
            if(a==MotionEvent.ACTION_CANCEL){
                if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);
                pendingDockLongPress=null;pressedDock=-1;draggingSeek=false;return true;
            }
            if(a==MotionEvent.ACTION_MOVE){
                if(draggingSeek){seekMusic(x);return true;}
                if(pressedDock>=0&&(y<352||y>454||masterDockIndex(x)!=pressedDock)){
                    if(pendingDockLongPress!=null)dockHandler.removeCallbacks(pendingDockLongPress);
                    pendingDockLongPress=null;pressedDock=-1;
                }
                return true;
            }
            if(a!=MotionEvent.ACTION_UP)return true;
            if(pendingDockLongPress!=null){dockHandler.removeCallbacks(pendingDockLongPress);pendingDockLongPress=null;}
            if(pressedDock>=0){int i=pressedDock;pressedDock=-1;if(!dockLongPressed)activateDock(i);return true;}
            if(draggingSeek){draggingSeek=false;seekMusic(x);return true;}
            if(y<48&&x>=600&&x<640){openSystemSettings("android.settings.BLUETOOTH_SETTINGS");return true;}
            if(y<48&&x>=645&&x<701){openSystemSettings("android.settings.WIFI_SETTINGS");return true;}
            if(x<88&&y>=58&&y<450){
                int menuIndex=Math.min(6,(int)((y-58)/56));
                if(menuIndex==0)return true;
                if(menuIndex==1){musicHub();return true;}
                if(menuIndex==2){openRadio();return true;}
                if(menuIndex==3){activateDock(2);return true;}
                if(menuIndex==4){showMode();return true;}
                if(menuIndex==5){showAppCenter();return true;}
                showZomaSettings();return true;
            }
            // V15 master artwork interaction map (800x480).
            if(y>=263&&y<=342&&x>=525&&x<=718){
                if(x<593){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else openRememberedUsb();}
                else if(x<650)togglePlayback();
                else {if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else openRememberedUsb();}
                return true;
            }
            if(y>=58&&y<=260&&x>=116&&x<=336){chooseCarbonGauge();return true;}
            // Controls on music card, matching the approved reference's positions.
            if(appearance==2&&y>=292&&y<=350&&x>=458&&x<=766){if(x<542){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else openRememberedUsb();}else if(x<642)togglePlayback();else {if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else openRememberedUsb();}return true;}
            if(y>=300&&y<=349&&x>=194&&x<=450&&appearance!=2){
                if(x<258){if(usbMode)nextUsb(-1);else if(localMode)nextLocal(-1);else openRememberedUsb();}
                else if(x<331)togglePlayback();
                else {if(usbMode)nextUsb(1);else if(localMode)nextLocal(1);else openRememberedUsb();}
                return true;
            }
            if(appearance==2&&y>=54&&y<=231&&x>=170&&x<=342){chooseCarbonGauge();return true;}
            if(y>=196&&y<=349&&x>=101&&x<194){if(usbMode)showUsbTracks();else openRememberedUsb();return true;}
            if(appearance!=2&&y>=205&&y<=301&&x>=459&&x<620){openRememberedUsb();return true;}
            if(y<50&&x<380){showZomaSettings();return true;}
            return true;
        }

    }
}
