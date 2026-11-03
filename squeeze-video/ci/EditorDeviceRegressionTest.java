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
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->service.setListener(snapshot->{
            if(snapshot.phase==CompressionService.Phase.COMPLETED
                    || snapshot.phase==CompressionService.Phase.SAVE_FAILED
                    || snapshot.phase==CompressionService.Phase.FAILED) {
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
}
