package com.zoma.os;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.view.*;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private Cockpit cockpit;
    @Override public void onCreate(Bundle state) { super.onCreate(state); cockpit = new Cockpit(); setContentView(cockpit); }
    private void launch(String[] packages, String fallback) {
        PackageManager pm=getPackageManager();
        for(String p:packages) {
            Intent i=pm.getLaunchIntentForPackage(p);
            if(i!=null) { startActivity(i); return; }
        }
        if("settings".equals(fallback)) { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); return; }
        if("apps".equals(fallback)) { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)); return; }
        Toast.makeText(this,"App not found. Open Apps to check package name.",Toast.LENGTH_LONG).show();
    }
    private class Cockpit extends View {
        Paint p=new Paint(3); Canvas c;
        Cockpit(){ super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        void color(int value){p.setColor(value);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(2);}
        int rgb(String s){return Color.parseColor(s);}
        void text(String s,float x,float y,int size,int col,boolean bold){color(col);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(size);c.drawText(s,x,y,p);}
        void line(float x,float y,float xx,float yy,int col,float width){color(col);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
        void rect(float l,float t,float r,float b,int col){color(col);c.drawRect(l,t,r,b,p);}
        @Override protected void onDraw(Canvas canvas){
            c=canvas;c.save();c.scale(getWidth()/800f,getHeight()/480f);
            c.drawColor(rgb("#081321"));
            rect(0,0,800,53,rgb("#0D2035"));
            text("ZOMA OS",24,34,23,rgb("#70D3FF"),true);
            text(new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),692,34,22,Color.WHITE,true);
            text("GEELY X7  |  DIGITAL COCKPIT",260,34,15,rgb("#8AA5BB"),false);
            // Road-perspective background
            Path road=new Path();road.moveTo(315,367);road.lineTo(386,132);road.lineTo(414,132);road.lineTo(485,367);road.close();color(rgb("#132B44"));c.drawPath(road,p);
            line(310,369,386,132,rgb("#58BDF8"),4);line(490,369,414,132,rgb("#58BDF8"),4);
            for(int i=0;i<5;i++){float y=164+i*i*8;float delta=(y-132)*0.085f;line(400-delta,y,400+delta,y,rgb("#426783"),2);}
            // Vehicle
            color(rgb("#CADAE7"));c.drawRoundRect(new RectF(371,277,429,358),15,15,p);
            color(rgb("#294F70"));c.drawRoundRect(new RectF(377,291,423,322),8,8,p);
            rect(377,343,388,348,rgb("#F35F68"));rect(412,343,423,348,rgb("#F35F68"));
            // Speed arc
            color(rgb("#35506A"));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);c.drawArc(new RectF(289,82,511,304),155,230,false,p);
            p.setStyle(Paint.Style.FILL);
            text("--",374,212,62,Color.WHITE,true);text("km/h",376,242,18,rgb("#6CCBFF"),false);
            text("SPEED",52,108,17,rgb("#8AA5BB"),true);text("--",52,164,47,Color.WHITE,true);text("GPS / OBD NOT CONNECTED",52,193,13,rgb("#F4BC65"),false);
            text("ENGINE RPM",600,108,17,rgb("#8AA5BB"),true);text("--",600,164,47,Color.WHITE,true);text("OBD NOT CONNECTED",600,193,13,rgb("#F4BC65"),false);
            text("TRIP",52,264,17,rgb("#8AA5BB"),true);text("-- km",52,301,27,Color.WHITE,true);
            text("COOLANT",600,264,17,rgb("#8AA5BB"),true);text("-- °C",600,301,27,Color.WHITE,true);
            rect(0,392,800,480,rgb("#0D2035"));
            String[] names={"ANDROID AUTO","MUSIC","MAPS","APPS","SETTINGS"};
            for(int i=0;i<5;i++){float left=12+i*158;rect(left,402,left+148,467,rgb(i==0?"#17638B":"#19334D"));text(names[i],left+9,442,i==0?16:17,Color.WHITE,true);}
            c.restore();postInvalidateDelayed(1000);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX()*800f/getWidth(),y=e.getY()*480f/getHeight();
            if(y>392){int index=(int)(x/158f);switch(index){
                case 0: launch(new String[]{"me.ri3d.openauto","com.janri3d.openauto","com.openauto"},"");break;
                case 1: launch(new String[]{"com.soundcloud.android","com.android.music"},"");break;
                case 2: launch(new String[]{"com.google.android.apps.maps","com.navitproject.navit"},"");break;
                case 3: launch(new String[]{},"apps");break;
                case 4: launch(new String[]{},"settings");break;
            }}return true;
        }
    }
}
