package com.viimixdj.app;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Environment;
import android.content.ContentValues;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.WindowManager;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.util.Base64;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Toast;
import android.view.ViewGroup;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.ConcurrentHashMap;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.Settings;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    WebView web;
    static final int REQ=72;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=28) getWindow().getAttributes().layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        goImmersive();
        web=new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setVisibility(View.INVISIBLE);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowFileAccessFromFileURLs(true);
        web.getSettings().setAllowUniversalAccessFromFileURLs(true);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        try {
            audioMgr=(AudioManager)getSystemService(AUDIO_SERVICE);
            audioCb=new AudioDeviceCallback(){
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d){ routeChanged(d,true); }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d){ routeChanged(d,false); }
            };
            audioMgr.registerAudioDeviceCallback(audioCb,new Handler(Looper.getMainLooper()));
        } catch(Exception ignored) {}
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String u){ pageDone=true; tryReveal(); }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){
                try { ViewGroup p=(ViewGroup)v.getParent(); if(p!=null) p.removeView(v); v.destroy(); } catch(Exception ignored) {}
                Toast.makeText(MainActivity.this,"That song needed more memory than this phone has. The app restarted.",Toast.LENGTH_LONG).show();
                recreate(); return true;
            }
        });
        try { File[] old=getCacheDir().listFiles(); if(old!=null) for(File f:old) if(f.getName().startsWith("vmx_dec_")) f.delete(); } catch(Exception ignored) {}
        web.addJavascriptInterface(new MusicBridge(), "AndroidMusic");
        setContentView(web);
        if (hasPermission()) loadPage(); else requestPermissions(new String[]{permission()}, REQ);
    }
    void goImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    boolean pageDone=false, hasFocus=false, revealed=false;
    /* Show the page only once it has loaded AND the system bars are hidden, so the layout never resizes in front of the user. */
    void tryReveal() {
        if(revealed||!pageDone||!hasFocus) return;
        goImmersive();
        web.postDelayed(new Runnable(){ public void run(){
            if(revealed||!hasFocus) return;
            revealed=true; goImmersive();
            web.evaluateJavascript("window.dispatchEvent(new Event('resize'))",null);
            web.setVisibility(View.VISIBLE);
        }},400);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); hasFocus=f; if(f){ goImmersive(); tryReveal(); } }
    String permission() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE; }
    boolean hasPermission() { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED; }
    boolean pageLoaded=false, deniedOnce=false, toSettings=false;
    void loadPage() { pageLoaded=true; web.loadUrl("file:///android_asset/index.html"); }
    void tellPermission(boolean ok) { web.evaluateJavascript("window.__onAudioPermission&&window.__onAudioPermission("+ok+")",null); }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r!=REQ) return;
        boolean ok=hasPermission(); if(!ok) deniedOnce=true;
        if(!pageLoaded) loadPage(); else tellPermission(ok);
    }
    AudioManager audioMgr; AudioDeviceCallback audioCb;
    static String outName(AudioDeviceInfo d) {
        switch(d.getType()) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "wired headphones";
            case AudioDeviceInfo.TYPE_USB_HEADSET: case AudioDeviceInfo.TYPE_USB_DEVICE: case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB audio";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "phone speaker";
            default: return "audio device";
        }
    }
    void routeChanged(AudioDeviceInfo[] ds,final boolean added) {
        String nm=null;
        for(AudioDeviceInfo d:ds) { if(d.isSink()&&d.getType()!=AudioDeviceInfo.TYPE_BUILTIN_SPEAKER&&d.getType()!=AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) { nm=outName(d); break; } }
        if(nm==null||!pageLoaded) return;
        final String name=nm;
        web.postDelayed(new Runnable(){ public void run(){ web.evaluateJavascript("window.__onAudioRoute&&window.__onAudioRoute("+added+","+JSONObject.quote(name)+")",null); }},500);
    }
    @Override protected void onDestroy() {
        try { if(audioMgr!=null&&audioCb!=null) audioMgr.unregisterAudioDeviceCallback(audioCb); } catch(Exception ignored) {}
        super.onDestroy();
    }
    @Override protected void onResume() { super.onResume(); if(toSettings){ toSettings=false; if(pageLoaded&&hasPermission()) tellPermission(true); } }
    class MusicBridge {
        OutputStream saveOut; Uri saveUri; File saveFile; String saveLabel="";
        @JavascriptInterface public synchronized String beginSave(String name,String mime) {
            abortSave();
            try {
                name=name.replaceAll("[\\\\/:*?\"<>|]","-");
                if(Build.VERSION.SDK_INT>=29) {
                    ContentValues cv=new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME,name); cv.put(MediaStore.MediaColumns.MIME_TYPE,mime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Music/VII MIX DJ"); cv.put(MediaStore.MediaColumns.IS_PENDING,1);
                    Uri u=null; saveLabel="Music/VII MIX DJ/"+name;
                    try { u=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cv); } catch(Exception e) { u=null; }
                    if(u==null) {
                        cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/VII MIX DJ"); saveLabel="Download/VII MIX DJ/"+name;
                        u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
                    }
                    if(u==null) return "could not create file";
                    saveUri=u; saveOut=getContentResolver().openOutputStream(u);
                } else {
                    File dir=getExternalFilesDir(Environment.DIRECTORY_MUSIC); if(dir==null) return "no storage";
                    dir.mkdirs(); saveFile=new File(dir,name); saveOut=new FileOutputStream(saveFile); saveLabel=saveFile.getAbsolutePath();
                }
                return saveOut==null?"could not open file":"";
            } catch(Exception e) { abortSave(); return String.valueOf(e.getMessage()); }
        }
        @JavascriptInterface public synchronized String writeChunk(String b64) {
            try { if(saveOut==null) return "no open file"; saveOut.write(Base64.decode(b64,Base64.DEFAULT)); return ""; }
            catch(Exception e) { return String.valueOf(e.getMessage()); }
        }
        @JavascriptInterface public synchronized String endSave() {
            try {
                if(saveOut==null) return "ERROR no open file";
                saveOut.flush(); saveOut.close(); saveOut=null;
                if(saveUri!=null) { ContentValues cv=new ContentValues(); cv.put(MediaStore.MediaColumns.IS_PENDING,0); getContentResolver().update(saveUri,cv,null,null); }
                String l=saveLabel; saveUri=null; saveFile=null; return l;
            } catch(Exception e) { return "ERROR "+e.getMessage(); }
        }
        @JavascriptInterface public synchronized void abortSave() {
            try { if(saveOut!=null) saveOut.close(); } catch(Exception ignored) {}
            saveOut=null;
            try { if(saveUri!=null) getContentResolver().delete(saveUri,null,null); } catch(Exception ignored) {}
            try { if(saveFile!=null) saveFile.delete(); } catch(Exception ignored) {}
            saveUri=null; saveFile=null;
        }
        @JavascriptInterface public boolean hasAudioPermission() { return MainActivity.this.hasPermission(); }
        @JavascriptInterface public void requestPermission() {
            runOnUiThread(new Runnable(){ public void run(){
                if(MainActivity.this.hasPermission()) return;
                if(deniedOnce&&!shouldShowRequestPermissionRationale(permission())) {
                    toSettings=true;
                    Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null));
                    startActivity(i);
                } else requestPermissions(new String[]{permission()},REQ);
            }});
        }
        @JavascriptInterface public void startScan() {
            new Thread(new Runnable(){ public void run(){
                final String json=buildRows(true);
                web.post(new Runnable(){ public void run(){ web.evaluateJavascript("window.__onAndroidSongs("+json+")",null); }});
            }}).start();
        }
        @JavascriptInterface public String listSongs() { return buildRows(false); }

        /* ---------- library building ---------- */
        String buildRows(boolean deep) {
            JSONArray a=new JSONArray();
            if(!hasPermission()) return a.toString();
            Set<String> paths=new HashSet<String>(), sigs=new HashSet<String>();
            List<String> all=null;
            if(deep) {
                try {
                    all=new ArrayList<String>();
                    int[] budget={80000};
                    for(File r:roots()) findAudio(r,true,0,all,budget);
                    queryMedia(new JSONArray(),paths,new HashSet<String>());
                    List<String> missing=new ArrayList<String>();
                    for(String p:all) if(!paths.contains(p)) missing.add(p);
                    if(missing.size()>3000) missing=missing.subList(0,3000);
                    if(!missing.isEmpty()) {
                        final CountDownLatch l=new CountDownLatch(missing.size());
                        MediaScannerConnection.scanFile(MainActivity.this,missing.toArray(new String[0]),null,new MediaScannerConnection.OnScanCompletedListener(){
                            public void onScanCompleted(String p,Uri u){ l.countDown(); }});
                        try { l.await(15,TimeUnit.SECONDS); } catch(InterruptedException ignored) {}
                    }
                } catch(Throwable t) { }
                paths.clear();
            }
            queryMedia(a,paths,sigs);
            if(deep) {
                if(all!=null) for(String p:all) if(!paths.contains(p)) addFileRow(a,new File(p),sigs);
            }
            return a.toString();
        }
        void queryMedia(JSONArray a,Set<String> paths,Set<String> sigs) {
            Uri u=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            ArrayList<String> cl=new ArrayList<String>(Arrays.asList(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.MIME_TYPE,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATE_ADDED,MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.DISPLAY_NAME,MediaStore.Audio.Media.IS_RINGTONE,MediaStore.Audio.Media.IS_ALARM,MediaStore.Audio.Media.IS_NOTIFICATION));
            if(Build.VERSION.SDK_INT>=29) cl.add(MediaStore.MediaColumns.RELATIVE_PATH);
            try(Cursor c=getContentResolver().query(u,cl.toArray(new String[0]),null,null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")) {
                if(c==null) return;
                int iId=c.getColumnIndex(MediaStore.Audio.Media._ID),iT=c.getColumnIndex(MediaStore.Audio.Media.TITLE),iAr=c.getColumnIndex(MediaStore.Audio.Media.ARTIST),iAl=c.getColumnIndex(MediaStore.Audio.Media.ALBUM),iSz=c.getColumnIndex(MediaStore.Audio.Media.SIZE),iMi=c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE),iDu=c.getColumnIndex(MediaStore.Audio.Media.DURATION),iDa=c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED),iDp=c.getColumnIndex(MediaStore.Audio.Media.DATA),iNm=c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME),iRi=c.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE),iAm=c.getColumnIndex(MediaStore.Audio.Media.IS_ALARM),iNo=c.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION),iRp=c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                while(c.moveToNext()) {
                    try {
                        if((iRi>=0&&c.getInt(iRi)!=0)||(iAm>=0&&c.getInt(iAm)!=0)||(iNo>=0&&c.getInt(iNo)!=0)) continue;
                        long dur=iDu>=0?c.getLong(iDu):0;
                        if(dur>0&&dur<5000) continue;
                        String dp=iDp>=0?c.getString(iDp):null, nm=iNm>=0?c.getString(iNm):null, title=iT>=0?c.getString(iT):null;
                        if(nm==null&&dp!=null) nm=dp.substring(dp.lastIndexOf('/')+1);
                        if(title==null||title.trim().isEmpty()) title=nm==null?"Unknown track":stripExt(nm);
                        long size=iSz>=0?c.getLong(iSz):0;
                        JSONObject o=new JSONObject();
                        o.put("id",c.getLong(iId)); o.put("title",title); o.put("artist",iAr>=0?c.getString(iAr):null);
                        o.put("album",iAl>=0?c.getString(iAl):null); o.put("size",size); o.put("mime",iMi>=0?c.getString(iMi):null);
                        o.put("duration",dur); o.put("added",iDa>=0?c.getLong(iDa):0); o.put("name",nm==null?"":nm);
                        o.put("folder",folderOf(dp,iRp>=0?c.getString(iRp):null));
                        a.put(o);
                        if(dp!=null) paths.add(dp);
                        if(nm!=null) sigs.add(nm.toLowerCase(Locale.ROOT)+"|"+size);
                    } catch(Exception ignored) {}
                }
            } catch(Exception ignored) {}
        }
        void addFileRow(JSONArray a,File f,Set<String> sigs) {
            try {
                String nm=f.getName(); long size=f.length(); if(size<=0) return;
                JSONObject o=new JSONObject();
                o.put("id","f:"+f.getAbsolutePath()); o.put("title",stripExt(nm)); o.put("artist",""); o.put("album","");
                o.put("size",size); o.put("mime",mimeOf(nm)); o.put("duration",0); o.put("added",f.lastModified()/1000); o.put("name",nm);
                o.put("folder",prettyDir(f.getParent()==null?"":f.getParent()));
                a.put(o); sigs.add(nm.toLowerCase(Locale.ROOT)+"|"+size);
            } catch(Exception ignored) {}
        }
        /* ---------- file-system helpers ---------- */
        List<File> roots() {
            LinkedHashSet<File> s=new LinkedHashSet<File>();
            File p=Environment.getExternalStorageDirectory(); if(p!=null) s.add(p);
            File[] ex=getExternalFilesDirs(null);
            if(ex!=null) for(File f:ex) { if(f==null) continue; String ap=f.getAbsolutePath(); int k=ap.indexOf("/Android/"); if(k>0) s.add(new File(ap.substring(0,k))); }
            return new ArrayList<File>(s);
        }
        void findAudio(File dir,boolean top,int depth,List<String> out,int[] budget) {
            File[] fs=dir.listFiles(); if(fs==null||depth>14) return;
            for(File f:fs) {
                if(budget[0]<=0) return;
                String n=f.getName();
                if(f.isDirectory()) { if(n.startsWith(".")||(top&&n.equals("Android"))) continue; findAudio(f,false,depth+1,out,budget); }
                else if(isAudioName(n)&&f.length()>30*1024) { out.add(f.getAbsolutePath()); budget[0]--; }
            }
        }
        boolean isAudioName(String n) {
            String l=n.toLowerCase(Locale.ROOT); int k=l.lastIndexOf('.'); if(k<0) return false;
            String e=l.substring(k+1);
            return e.equals("mp3")||e.equals("m4a")||e.equals("aac")||e.equals("wav")||e.equals("flac")||e.equals("ogg")||e.equals("oga")||e.equals("opus")||e.equals("aif")||e.equals("aiff")||e.equals("mka");
        }
        String stripExt(String n) { int k=n.lastIndexOf('.'); return k>0?n.substring(0,k):n; }
        String mimeOf(String n) {
            String l=n.toLowerCase(Locale.ROOT);
            if(l.endsWith(".mp3")) return "audio/mpeg"; if(l.endsWith(".m4a")) return "audio/mp4"; if(l.endsWith(".aac")) return "audio/aac";
            if(l.endsWith(".wav")) return "audio/wav"; if(l.endsWith(".flac")) return "audio/flac"; if(l.endsWith(".opus")) return "audio/ogg";
            if(l.endsWith(".ogg")||l.endsWith(".oga")) return "audio/ogg"; if(l.endsWith(".aif")||l.endsWith(".aiff")) return "audio/x-aiff";
            return "audio/*";
        }
        String folderOf(String dataPath,String relPath) {
            if(dataPath!=null) { int k=dataPath.lastIndexOf('/'); if(k>0) return prettyDir(dataPath.substring(0,k)); }
            if(relPath!=null&&!relPath.isEmpty()) { String r=relPath; if(r.endsWith("/")) r=r.substring(0,r.length()-1); return r.isEmpty()?"Internal storage":r; }
            return "Unknown folder";
        }
        String prettyDir(String dir) {
            Matcher m=Pattern.compile("^/storage/emulated/\\d+(/(.*))?$").matcher(dir);
            if(m.matches()) { String s=m.group(2); return s==null||s.isEmpty()?"Internal storage":s; }
            if(dir.startsWith("/storage/")) { String r=dir.substring(9); int k=r.indexOf('/'); return k<0?"SD card":"SD card/"+r.substring(k+1); }
            if(dir.startsWith("/sdcard/")) return dir.substring(8);
            return dir.isEmpty()?"Unknown folder":dir;
        }

        /* ---------- chunked reading: no size cap, no giant base64 string ---------- */
        String curKey=null; FileChannel curCh=null; RandomAccessFile curRaf=null; InputStream curIn=null;
        synchronized boolean openSong(String id) {
            if(id.equals(curKey)&&curCh!=null) return true;
            closeSong();
            try {
                if(id.startsWith("f:")) { curRaf=new RandomAccessFile(id.substring(2),"r"); curCh=curRaf.getChannel(); }
                else {
                    Uri u=id.startsWith("u:")?Uri.parse(id.substring(2)):Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id);
                    ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(u,"r"); if(pfd==null) return false;
                    ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                    curIn=in; curCh=in.getChannel();
                }
                curKey=id; return true;
            } catch(Exception e) { closeSong(); return false; }
        }
        @JavascriptInterface public synchronized void closeSong() {
            try { if(curRaf!=null) curRaf.close(); } catch(Exception ignored) {}
            try { if(curIn!=null) curIn.close(); } catch(Exception ignored) {}
            curRaf=null; curIn=null; curCh=null; curKey=null;
        }
        @JavascriptInterface public synchronized double songSize(String id) {
            if(!hasPermission()||!openSong(id)) return -1;
            try { return (double)curCh.size(); } catch(Exception e) { return -1; }
        }
        @JavascriptInterface public synchronized String songChunk(String id,double offset,int len) {
            if(!hasPermission()||!openSong(id)) return "";
            try {
                ByteBuffer bb=ByteBuffer.allocate(len); long pos=(long)offset;
                while(bb.hasRemaining()) { int n=curCh.read(bb,pos); if(n<=0) break; pos+=n; }
                if(bb.position()==0) return "";
                return Base64.encodeToString(bb.array(),0,bb.position(),Base64.NO_WRAP);
            } catch(Exception e) { return ""; }
        }

        /* ---------- big-file decoder: Android decodes to a small mono file, the page never holds the full-size audio ---------- */
        final ConcurrentHashMap<Integer,DecodeJob> jobs=new ConcurrentHashMap<Integer,DecodeJob>(); int jobSeq=0;
        @JavascriptInterface public int startDecode(final String id,final double budget) {
            if(!hasPermission()) return -1;
            final DecodeJob j=new DecodeJob(); final int jid;
            synchronized(jobs) { jid=++jobSeq; jobs.put(jid,j); }
            j.out=new File(getCacheDir(),"vmx_dec_"+jid+".pcm");
            new Thread(new Runnable(){ public void run(){
                try { decodeJob(id,(long)budget,j); j.state=j.cancel?3:1; }
                catch(Throwable t) { j.err=String.valueOf(t.getMessage()); j.state=2; try { j.out.delete(); } catch(Exception ignored) {} }
            }}).start();
            return jid;
        }
        @JavascriptInterface public String decodeStatus(int jid) {
            DecodeJob j=jobs.get(jid);
            if(j==null) return "{\"s\":2,\"err\":\"no job\"}";
            return "{\"s\":"+j.state+",\"p\":"+j.pct+",\"n\":"+j.frames+",\"r\":"+j.rate+",\"path\":"+JSONObject.quote(j.out.getAbsolutePath())+",\"err\":"+JSONObject.quote(j.err)+"}";
        }
        @JavascriptInterface public void cancelDecode(int jid) { DecodeJob j=jobs.get(jid); if(j!=null) j.cancel=true; }
        @JavascriptInterface public void finishDecode(int jid) {
            DecodeJob j=jobs.remove(jid);
            if(j!=null) { j.cancel=true; closeSong(); try { j.out.delete(); } catch(Exception ignored) {} }
        }
        void decodeJob(String id,long budget,DecodeJob j) throws Exception {
            MediaExtractor ex=new MediaExtractor(); MediaCodec codec=null; FileOutputStream fo=null;
            try {
                if(id.startsWith("f:")) ex.setDataSource(id.substring(2));
                else { Uri u=id.startsWith("u:")?Uri.parse(id.substring(2)):Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id); ex.setDataSource(MainActivity.this,u,null); }
                int ti=-1; MediaFormat fmt=null;
                for(int i=0;i<ex.getTrackCount();i++) { MediaFormat f=ex.getTrackFormat(i); String m=f.getString(MediaFormat.KEY_MIME); if(m!=null&&m.startsWith("audio/")) { ti=i; fmt=f; break; } }
                if(ti<0) throw new Exception("no audio track in this file");
                ex.selectTrack(ti);
                String mime=fmt.getString(MediaFormat.KEY_MIME);
                int srcRate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
                int ch=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):2; if(ch<1) ch=1;
                long durUs=fmt.containsKey(MediaFormat.KEY_DURATION)?fmt.getLong(MediaFormat.KEY_DURATION):0;
                double durSec=durUs>0?durUs/1e6:3600;
                int[] cand={44100,32000,24000,22050,16000,12000,8000};
                int outRate=8000; for(int c:cand) { if(c<=srcRate&&durSec*c<=budget) { outRate=c; break; } }
                if(outRate>srcRate) outRate=srcRate;
                j.rate=outRate;
                codec=MediaCodec.createDecoderByType(mime); codec.configure(fmt,null,null,0); codec.start();
                fo=new FileOutputStream(j.out);
                Resamp rs=new Resamp(srcRate/(double)outRate,fo);
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                boolean inDone=false,outDone=false,isFloat=false; int rate=srcRate;
                while(!outDone&&!j.cancel) {
                    if(!inDone) {
                        int ii=codec.dequeueInputBuffer(10000);
                        if(ii>=0) {
                            ByteBuffer ib=codec.getInputBuffer(ii); int n=ex.readSampleData(ib,0);
                            if(n<0) { codec.queueInputBuffer(ii,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone=true; }
                            else { long ts=ex.getSampleTime(); codec.queueInputBuffer(ii,0,n,ts,0); if(durUs>0) j.pct=(int)Math.min(99,ts*100/durUs); ex.advance(); }
                        }
                    }
                    int oi=codec.dequeueOutputBuffer(info,10000);
                    if(oi>=0) {
                        if(info.size>0) {
                            ByteBuffer ob=codec.getOutputBuffer(oi); ob.position(info.offset); ob.limit(info.offset+info.size); ob.order(ByteOrder.LITTLE_ENDIAN);
                            if(isFloat) { FloatBuffer fb=ob.asFloatBuffer(); int n=fb.remaining()/ch; for(int f=0;f<n;f++) { double sum=0; for(int c=0;c<ch;c++) sum+=fb.get(); rs.push(sum/ch*32768.0); } }
                            else { ShortBuffer sb=ob.asShortBuffer(); int n=sb.remaining()/ch; for(int f=0;f<n;f++) { int sum=0; for(int c=0;c<ch;c++) sum+=sb.get(); rs.push((double)sum/ch); } }
                        }
                        codec.releaseOutputBuffer(oi,false);
                        if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) outDone=true;
                    } else if(oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat of=codec.getOutputFormat();
                        if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch=Math.max(1,of.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                        isFloat=of.containsKey("pcm-encoding")&&of.getInteger("pcm-encoding")==4;
                        if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE)&&rs.frames==0&&rs.i==0) {
                            rate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                            if(outRate>rate) outRate=rate;
                            rs=new Resamp(rate/(double)outRate,fo); j.rate=outRate;
                        }
                    }
                }
                rs.flush(); fo.close(); fo=null;
                j.frames=rs.frames; j.rate=outRate; j.pct=100;
            } finally {
                try { if(codec!=null) { codec.stop(); codec.release(); } } catch(Exception ignored) {}
                try { ex.release(); } catch(Exception ignored) {}
                try { if(fo!=null) fo.close(); } catch(Exception ignored) {}
            }
        }
    }

    static class DecodeJob { volatile int state=0,pct=0,rate=0; volatile long frames=0; volatile String err=""; volatile boolean cancel=false; File out; }
    /* box-filter downsampler: mono samples in, 16-bit little-endian samples out */
    static class Resamp {
        final double ratio; double nextEdge,binSum; long i,frames; final byte[] buf=new byte[65536]; int bn; final OutputStream fo;
        Resamp(double ratio,OutputStream fo) { this.ratio=ratio; this.nextEdge=ratio; this.fo=fo; }
        void push(double x) throws IOException {
            double start=i,end=i+1;
            while(true) {
                if(end<nextEdge-1e-9) { binSum+=x*(end-start); break; }
                binSum+=x*(nextEdge-start); emit(binSum/ratio); binSum=0; start=nextEdge; nextEdge+=ratio;
                if(start>=end-1e-9) break;
            }
            i++;
        }
        void emit(double v) throws IOException {
            int s=(int)Math.round(v); if(s>32767) s=32767; if(s<-32768) s=-32768;
            buf[bn++]=(byte)s; buf[bn++]=(byte)(s>>8); frames++;
            if(bn>=buf.length) { fo.write(buf,0,bn); bn=0; }
        }
        void flush() throws IOException { if(bn>0) { fo.write(buf,0,bn); bn=0; } }
    }
}
