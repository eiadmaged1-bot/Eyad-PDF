package com.squeezelab.video;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class EditorDeviceRegressionTest {
    @Rule public final ServiceTestRule services = new ServiceTestRule();
    private static final String TAG = "EYAD_QA";

    private static File fixture(String name) throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File target = new File(app.getFilesDir(), "qa-" + name);
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        assertTrue("Fixture missing: " + name, target.length() > 100);
        return target;
    }
    private static Object field(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static int clips(Activity activity) {
        return ((ArrayList<?>) field(activity,"clips")).size();
    }
    private static void deliverClips(StudioActivity activity, Uri first, Uri second) {
        try {
            Intent data = new Intent();
            ClipData selection = ClipData.newRawUri("first", first);
            selection.addItem(new ClipData.Item(second));
            data.setClipData(selection);
            Method callback = StudioActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);
            callback.setAccessible(true);
            callback.invoke(activity,2080,Activity.RESULT_OK,data);
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private static void waitForClipCount(ActivityScenario<StudioActivity> scenario, int expected) throws Exception {
        for (int n = 0; n < 75; n++) {
            AtomicReference<Integer> count=new AtomicReference<>(0);
            scenario.onActivity(activity -> count.set(clips(activity)));
            if(count.get()==expected)return;
            Thread.sleep(200);
        }
        AtomicReference<Integer> count = new AtomicReference<>(0);
        scenario.onActivity(activity -> count.set(clips(activity)));
        fail("Imported " + count.get() + " of " + expected + " valid MP4 clips");
    }

    @Test public void importsDecodesAndPlaysTwoClipsWithoutBlackPreview() throws Exception {
        File a=fixture("clip-red.mp4"),b=fixture("clip-blue.mp4");
        AtomicReference<String> playbackError = new AtomicReference<>("");
        CountDownLatch renderedFrame=new CountDownLatch(1);
        CountDownLatch enteredSecondClip=new CountDownLatch(1);
        try(ActivityScenario<StudioActivity> scenario=ActivityScenario.launch(StudioActivity.class)) {
            scenario.onActivity(activity -> deliverClips(activity,Uri.fromFile(a),Uri.fromFile(b)));
            waitForClipCount(scenario,2);
            scenario.onActivity(activity -> {
                ExoPlayer exo=(ExoPlayer)field(activity,"exo");
                assertNotNull("ExoPlayer missing",exo);
                exo.addListener(new Player.Listener() {
                    @Override public void onRenderedFirstFrame() { renderedFrame.countDown(); }
                    @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item,int reason) {
                        if(exo.getCurrentMediaItemIndex()==1)enteredSecondClip.countDown();
                    }
                    @Override public void onPlayerError(PlaybackException error) {
                        playbackError.set(error.getErrorCodeName());
                    }
                });
                Button play=(Button)field(activity,"playAllButton");
                assertTrue("Play All disabled",play.isEnabled());
                play.performClick();
                Log.i(TAG,"Playlist started with 2 real H.264/AAC MP4 files");
            });
            assertTrue("Decoder did not render a frame within 25 seconds: "+playbackError.get(),
                    renderedFrame.await(25,TimeUnit.SECONDS));
            assertTrue("Playback never reached second clip: "+playbackError.get(),
                    enteredSecondClip.await(25,TimeUnit.SECONDS));
            assertEquals("Playback error occurred", "", playbackError.get());
            Log.i(TAG,"PASS: decoded frames and transitioned from first to second clip");
            scenario.recreate();
            waitForClipCount(scenario,2);
            Log.i(TAG,"PASS: project restored with two imported clips after Activity recreation");
        }
    }

    @Test public void exportsTwoClipsWithVisibleCourseLogo() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File a=fixture("clip-red.mp4"),b=fixture("clip-blue.mp4"),logo=fixture("course-logo.png");
        JSONArray clips=new JSONArray();
        for(File file:new File[]{a,b}) {
            clips.put(new JSONObject()
                .put("uri",Uri.fromFile(file).toString())
                .put("title",file.getName())
                .put("duration",4000).put("start",0).put("end",3000)
                .put("bytes",file.length()).put("width",320).put("height",240)
                .put("rotation",0).put("crop",0));
        }
        Intent binding=new Intent(app,CompressionService.class);
        IBinder binder=services.bindService(binding);
        CompressionService service=((CompressionService.LocalBinder)binder).getService();
        CountDownLatch finished=new CountDownLatch(1);
        AtomicReference<CompressionService.Snapshot> outcome=new AtomicReference<>();
        AtomicBoolean started=new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->service.setListener(snapshot->{
            if(snapshot.phase==CompressionService.Phase.EXPORTING)started.set(true);
            if(started.get() && (snapshot.phase==CompressionService.Phase.COMPLETED
                    || snapshot.phase==CompressionService.Phase.SAVE_FAILED
                    || snapshot.phase==CompressionService.Phase.FAILED)) {
                outcome.set(snapshot);finished.countDown();
            }
        }));
        Intent export=new Intent(app,CompressionService.class).setAction(CompressionService.ACTION_EXPORT);
        export.putExtra(CompressionService.EXTRA_STUDIO_JSON,clips.toString());
        export.putExtra(CompressionService.EXTRA_SOURCE_BYTES,a.length()+b.length());
        export.putExtra(CompressionService.EXTRA_SHORT_SIDE,240);
        export.putExtra(CompressionService.EXTRA_VIDEO_BITRATE,500000);
        export.putExtra(CompressionService.EXTRA_AUDIO_BITRATE,64000);
        export.putExtra(CompressionService.EXTRA_HEVC,false);
        export.putExtra(CompressionService.EXTRA_STUDIO_VOLUME,100);
        export.putExtra(CompressionService.EXTRA_STUDIO_LOGO,Uri.fromFile(logo).toString());
        export.putExtra(CompressionService.EXTRA_LOGO_X,.72f);
        export.putExtra(CompressionService.EXTRA_LOGO_Y,-.76f);
        export.putExtra(CompressionService.EXTRA_LOGO_SIZE,38);
        export.putExtra(CompressionService.EXTRA_LOGO_OPACITY,100);
        app.startForegroundService(export);
        assertTrue("Export did not finish within 150 seconds",finished.await(150,TimeUnit.SECONDS));
        CompressionService.Snapshot result=outcome.get();
        assertNotNull(result);
        Log.i(TAG,"Export status="+result.phase+" message="+result.message+" bytes="+result.outputBytes
            +" diagnostics="+result.diagnostics);
        assertEquals("Export or Gallery save failed: "+result.message,
                CompressionService.Phase.COMPLETED,result.phase);
        assertNotNull("No output video URI",result.savedUri);
        assertTrue("Output MP4 empty",result.outputBytes>1000);
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        try {
            reader.setDataSource(app,result.savedUri);
            String length=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            assertNotNull("No duration metadata",length);
            assertTrue("Export unexpectedly short: "+length,Long.parseLong(length)>=4800);
            Bitmap frame=reader.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            assertNotNull("Could not decode exported frame",frame);
            int hits=0;
            for(int y=0;y<frame.getHeight()/2;y+=2)for(int x=frame.getWidth()/2;x<frame.getWidth();x+=2){
                int color=frame.getPixel(x,y);
                int r=android.graphics.Color.red(color),g=android.graphics.Color.green(color),bl=android.graphics.Color.blue(color);
                if(r>120&&g>115&&bl<120)hits++;
            }
            Log.i(TAG,"Course watermark yellow pixel hits in top-right quadrant: "+hits);
            assertTrue("Watermark not visibly embedded in exported video: "+hits+" yellow pixels",hits>=10);
            frame.recycle();
        } finally {reader.release();}
        Log.i(TAG,"PASS: 2 clips combined, output playable and course watermark visible in encoded frame");
    }

    private static void invokeEditor(StudioActivity activity,String name,Class<?>[] signature,Object... args){
        try{
            Method m=StudioActivity.class.getDeclaredMethod(name,signature);
            m.setAccessible(true);m.invoke(activity,args);
        }catch(Exception e){throw new RuntimeException("Editor operation "+name+" failed",e);}
    }
    private static void importOne(StudioActivity activity,Uri uri){
        try{
            Intent result=new Intent().setData(uri);
            Method m=StudioActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);
            m.setAccessible(true);m.invoke(activity,2080,Activity.RESULT_OK,result);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @Test public void repeatedSeeksReuseExistingPlayerPlaylist() throws Exception {
        File green=fixture("clip-green.mp4");
        try(ActivityScenario<StudioActivity> scenario=ActivityScenario.launch(StudioActivity.class)){
            scenario.onActivity(activity->{
                ((ArrayList<?>)field(activity,"clips")).clear();
                try{
                    Field selection=StudioActivity.class.getDeclaredField("selected");
                    selection.setAccessible(true);selection.setInt(activity,-1);
                }catch(Exception ex){throw new RuntimeException(ex);}
                invokeEditor(activity,"save",new Class<?>[]{});
                importOne(activity,Uri.fromFile(green));
            });
            waitForClipCount(scenario,1);
            scenario.onActivity(activity->{
                Class<?>[] signature={int.class,long.class,boolean.class,boolean.class};
                invokeEditor(activity,"previewFrom",signature,0,500L,false,false);
                int first=(Integer)field(activity,"previewPrepareCount");
                assertTrue("A new player playlist was never prepared",first>=1);
                invokeEditor(activity,"previewFrom",signature,0,1100L,false,false);
                invokeEditor(activity,"previewFrom",signature,0,1700L,false,false);
                invokeEditor(activity,"previewFrom",signature,0,2100L,false,false);
                assertEquals("Every seek re-prepared ExoPlayer rather than reusing the playlist",
                    first,((Integer)field(activity,"previewPrepareCount")).intValue());
            });
        }
        Log.i(TAG,"PASS: repeated seeks did not rebuild decoder playlist");
    }

    @Test public void savedIntroMainOutroAndLogoExportInCorrectOrder() throws Exception {
        File red=fixture("clip-red.mp4"),green=fixture("clip-green.mp4");
        File blue=fixture("clip-blue.mp4"),logo=fixture("course-logo.png");
        AtomicReference<String> project=new AtomicReference<>("");
        try(ActivityScenario<StudioActivity> scenario=ActivityScenario.launch(StudioActivity.class)){
            scenario.onActivity(activity->{
                ((ArrayList<?>)field(activity,"clips")).clear();
                try{
                    Field selected=StudioActivity.class.getDeclaredField("selected");
                    selected.setAccessible(true);selected.setInt(activity,-1);
                }catch(Exception e){throw new RuntimeException(e);}
                invokeEditor(activity,"save",new Class<?>[]{});
                deliverClips(activity,Uri.fromFile(red),Uri.fromFile(blue));
            });
            waitForClipCount(scenario,2);
            scenario.onActivity(activity->{
                invokeEditor(activity,"saveTemplate",new Class<?>[]{boolean.class},true);
                invokeEditor(activity,"select",new Class<?>[]{int.class},1);
                invokeEditor(activity,"saveTemplate",new Class<?>[]{boolean.class},false);
                invokeEditor(activity,"remove",new Class<?>[]{int.class},1);
                invokeEditor(activity,"remove",new Class<?>[]{int.class},0);
                importOne(activity,Uri.fromFile(green));
            });
            waitForClipCount(scenario,1);
            scenario.onActivity(activity->
                invokeEditor(activity,"insertTemplate",new Class<?>[]{boolean.class},true));
            waitForClipCount(scenario,2);
            scenario.onActivity(activity->
                invokeEditor(activity,"insertTemplate",new Class<?>[]{boolean.class},false));
            waitForClipCount(scenario,3);
            scenario.onActivity(activity->{
                try{
                    JSONArray array=new JSONArray();
                    for(Object clip:(ArrayList<?>)field(activity,"clips")){
                        Method json=clip.getClass().getDeclaredMethod("json");
                        json.setAccessible(true);array.put((JSONObject)json.invoke(clip));
                    }
                    assertTrue("First clip must be saved intro",array.getJSONObject(0).getString("uri").contains("clip-red"));
                    assertTrue("Main lecture must be in middle",array.getJSONObject(1).getString("uri").contains("clip-green"));
                    assertTrue("Last clip must be saved outro",array.getJSONObject(2).getString("uri").contains("clip-blue"));
                    project.set(array.toString());
                }catch(Exception e){throw new RuntimeException(e);}
            });
            scenario.recreate();
            waitForClipCount(scenario,3);
        }

        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        IBinder binder=services.bindService(new Intent(app,CompressionService.class));
        CompressionService service=((CompressionService.LocalBinder)binder).getService();
        CountDownLatch finished=new CountDownLatch(1);
        AtomicReference<CompressionService.Snapshot> outcome=new AtomicReference<>();
        AtomicBoolean started=new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->service.setListener(snapshot->{
            if(snapshot.phase==CompressionService.Phase.EXPORTING)started.set(true);
            if(started.get() && (snapshot.phase==CompressionService.Phase.COMPLETED
                ||snapshot.phase==CompressionService.Phase.FAILED
                ||snapshot.phase==CompressionService.Phase.SAVE_FAILED)){
                outcome.set(snapshot);finished.countDown();
            }
        }));
        Intent request=new Intent(app,CompressionService.class).setAction(CompressionService.ACTION_EXPORT);
        request.putExtra(CompressionService.EXTRA_STUDIO_JSON,project.get());
        request.putExtra(CompressionService.EXTRA_SHORT_SIDE,240);
        request.putExtra(CompressionService.EXTRA_SOURCE_BYTES,red.length()+green.length()+blue.length());
        request.putExtra(CompressionService.EXTRA_VIDEO_BITRATE,500000);
        request.putExtra(CompressionService.EXTRA_AUDIO_BITRATE,64000);
        request.putExtra(CompressionService.EXTRA_HEVC,false);
        request.putExtra(CompressionService.EXTRA_STUDIO_VOLUME,100);
        request.putExtra(CompressionService.EXTRA_STUDIO_LOGO,Uri.fromFile(logo).toString());
        request.putExtra(CompressionService.EXTRA_LOGO_X,.72f);
        request.putExtra(CompressionService.EXTRA_LOGO_Y,-.76f);
        request.putExtra(CompressionService.EXTRA_LOGO_SIZE,38);
        request.putExtra(CompressionService.EXTRA_LOGO_OPACITY,100);
        app.startForegroundService(request);
        assertTrue("Intro/main/outro export timeout",finished.await(180,TimeUnit.SECONDS));
        CompressionService.Snapshot result=outcome.get();
        assertNotNull(result);
        assertEquals("Export failed: "+result.message,CompressionService.Phase.COMPLETED,result.phase);
        assertNotNull(result.savedUri);
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        try{
            reader.setDataSource(app,result.savedUri);
            long duration=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue("Three 4-second clips did not make 12-second export: "+duration,duration>=11500);
            long[] positions={2000000L,6000000L,10000000L};
            int[] expected={0,1,2};
            for(int segment=0;segment<3;segment++){
                Bitmap frame=reader.getFrameAtTime(positions[segment],MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                assertNotNull("No decoded frame for segment "+segment,frame);
                int pixel=frame.getPixel(frame.getWidth()/3,frame.getHeight()/2);
                int r=android.graphics.Color.red(pixel);
                int g=android.graphics.Color.green(pixel);
                int b=android.graphics.Color.blue(pixel);
                if(expected[segment]==0)assertTrue("Intro frame is not red: "+r+","+g+","+b,r>120&&r>g*2&&r>b*2);
                if(expected[segment]==1)assertTrue("Main frame is not green: "+r+","+g+","+b,g>65&&g>r*2&&g>b*2);
                if(expected[segment]==2)assertTrue("Outro frame is not blue: "+r+","+g+","+b,b>110&&b>r*2&&b>g*2);
                int yellow=0;
                for(int y=0;y<frame.getHeight()/2;y+=2)
                    for(int x=frame.getWidth()/2;x<frame.getWidth();x+=2){
                        int color=frame.getPixel(x,y);
                        if(android.graphics.Color.red(color)>120
                            &&android.graphics.Color.green(color)>115
                            &&android.graphics.Color.blue(color)<120)yellow++;
                    }
                assertTrue("Course logo missing in "+new String[]{"intro","main","outro"}[segment]
                    +" ("+yellow+" yellow pixels)",yellow>=10);
                frame.recycle();
            }
        }finally{reader.release();}
        Log.i(TAG,"PASS: saved intro, main, outro in order; course watermark on all three exported sections");
    }
}
