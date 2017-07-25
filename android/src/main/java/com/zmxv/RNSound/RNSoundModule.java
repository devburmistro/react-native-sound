package com.zmxv.RNSound;

import android.os.Build;
import android.os.Handler;
import android.net.Uri;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import android.util.Log;

public class RNSoundModule extends ReactContextBaseJavaModule {
  Map<Integer, SimpleExoPlayer> playerPool = new HashMap<>();
  ReactApplicationContext context;
  final static Object NULL = null;

  public RNSoundModule(ReactApplicationContext context) {
    super(context);
    this.context = context;
  }

  private static String getDefaultUserAgent() {
    StringBuilder result = new StringBuilder(64);
    result.append("Dalvik/");
    result.append(System.getProperty("java.vm.version")); // such as 1.1.0
    result.append(" (Linux; U; Android ");

    String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
    result.append(version.length() > 0 ? version : "1.0");

    // add the model for the release build
    if ("REL".equals(Build.VERSION.CODENAME)) {
      String model = Build.MODEL;
      if (model.length() > 0) {
        result.append("; ");
        result.append(model);
      }
    }
    String id = Build.ID; // "MASTER" or "M4-rc20"
    if (id.length() > 0) {
      result.append(" Build/");
      result.append(id);
    }
    result.append(")");
    return result.toString();
  }


  @Override
  public String getName() {
    return "RNSound";
  }

  @ReactMethod
  public void prepare(final String urlString, final Integer key, final Callback callback) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.stop();
      player.release();
    }
    TrackSelector trackSelector = new DefaultTrackSelector();
    LoadControl loadControl = new DefaultLoadControl();

    player = ExoPlayerFactory.newSimpleInstance(context, trackSelector, loadControl);

    // Create source
    ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, getDefaultUserAgent(), bandwidthMeter);
    Handler mainHandler = new Handler();
    MediaSource audioSource = new ExtractorMediaSource(Uri.parse(urlString), dataSourceFactory, extractorsFactory, mainHandler, null);
    try {
      player.prepare(audioSource);
    } catch (Exception exception) {
              Log.e("RNSoundModule", "Exception", exception);

        WritableMap e = Arguments.createMap();
        e.putInt("code", -1);
        e.putString("message", exception.getMessage());
        callback.invoke(e);
        return;
    }

    final SimpleExoPlayer finalPlayer = player;
    player.addListener(new ExoPlayer.EventListener() {
      boolean hasfired = false;
      @Override
      public void onTimelineChanged(Timeline timeline, Object manifest) {}

      @Override
      public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

      @Override
      public void onLoadingChanged(boolean isLoading) {
        System.out.println("NATIVE loading finished " + isLoading + " duration is " + finalPlayer.getDuration());
        if (hasfired) { return; }
        if (isLoading == false) {
          WritableMap props = Arguments.createMap();
          props.putDouble("duration", finalPlayer.getDuration() * .001);
          callback.invoke(NULL, props);
          hasfired = true;
        }
      }

      @Override
      public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {}

      @Override
      public void onPlayerError(ExoPlaybackException error) {}

      @Override
      public void onPositionDiscontinuity() {}
    });

    this.playerPool.put(key, player);

//    WritableMap props = Arguments.createMap();
//    props.putDouble("duration", player.getDuration() * .001);
//    callback.invoke(NULL, props);
  }

  @ReactMethod
  public void play(final Integer key, final Callback callback) {
    final SimpleExoPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(false);
      return;
    }
    player.addListener(new ExoPlayer.EventListener() {
      @Override
      public void onTimelineChanged(Timeline timeline, Object manifest) {}

      @Override
      public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

      @Override
      public void onLoadingChanged(boolean isLoading) {}

      @Override
      public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        System.out.println("NATIVE player state changed:" + playbackState );
        if (playbackState ==  ExoPlayer.STATE_ENDED) {
          callback.invoke(true);
        }
      }

      @Override
      public void onPlayerError(ExoPlaybackException error) {
        callback.invoke(false);
      }

      @Override
      public void onPositionDiscontinuity() {

      }
    });
    player.setPlayWhenReady(true);
  }

  @ReactMethod
  public void pause(final Integer key) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setPlayWhenReady(false);
    }
  }

  @ReactMethod
  public void stop(final Integer key) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.stop();
      player.seekTo(0);
    }
  }

  @ReactMethod
  public void release(final Integer key) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.stop();
      player.release();
      this.playerPool.remove(key);
    }
  }

  @ReactMethod
  public void setVolume(final Integer key, final Float left, final Float right) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.setVolume((left + right) / 2.0f);
    }
  }

  @ReactMethod
  public void setLooping(final Integer key, final Boolean looping) {
//    SimpleExoPlayer player = this.playerPool.get(key);
//    if (player != null) {
//      player.setLooping(looping);
//    }
  }

  @ReactMethod
  public void setCurrentTime(final Integer key, final Float sec) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player != null) {
      player.seekTo((int)Math.round(sec * 1000));
    }
  }

  @ReactMethod
  public void getCurrentTime(final Integer key, final Callback callback) {
    SimpleExoPlayer player = this.playerPool.get(key);
    if (player == null) {
      callback.invoke(-1, false);
      return;
    }
    callback.invoke(player.getCurrentPosition() * .001);
  }

  @ReactMethod
  public void enable(final Boolean enabled) {
    // no op
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("IsAndroid", true);
    return constants;
  }
}

