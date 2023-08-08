package com.fongmi.android.tv.player;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;

import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class Players implements Player.Listener, IMediaPlayer.Listener, AnalyticsListener, ParseCallback {

    public static final int SYS = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private IjkVideoView ijkPlayer;
    private StringBuilder builder;
    private Formatter formatter;
    private ExoPlayer exoPlayer;
    private ParseJob parseJob;
    private Runnable runnable;
    private int errorCode;
    private int timeout;
    private int retry;
    private int decode;
    private int player;

    public static boolean isExo(int type) {
        return type == EXO;
    }

    public static boolean isHard() {
        return Setting.getDecode() == HARD;
    }

    public boolean isExo() {
        return player == EXO;
    }

    public boolean isIjk() {
        return player == SYS || player == IJK;
    }

    public Players init() {
        player = Setting.getPlayer();
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        timeout = Constant.TIMEOUT_PLAY;
        formatter = new Formatter(builder, Locale.getDefault());
        return this;
    }

    public void set(PlayerView exo, IjkVideoView ijk) {
        releaseExo();
        releaseIjk();
        setupExo(exo);
        setupIjk(ijk);
    }

    private void setupExo(PlayerView view) {
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setRenderersFactory(ExoUtil.buildRenderersFactory()).setTrackSelector(ExoUtil.buildTrackSelector()).build();
        exoPlayer.addAnalyticsListener(this);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
    }

    private void setupIjk(IjkVideoView view) {
        ijkPlayer = view.render(Setting.getRender()).decode(decode);
        ijkPlayer.addListener(this);
        ijkPlayer.setPlayer(player);
    }

    public ExoPlayer exo() {
        return exoPlayer;
    }

    public IjkVideoView ijk() {
        return ijkPlayer;
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        this.player = player;
    }

    public int getDecode() {
        return decode;
    }

    public void setDecode(int decode) {
        this.decode = decode;
    }

    public void reset() {
        removeTimeoutCheck();
        this.errorCode = 0;
        this.retry = 0;
        stopParse();
    }

    public int addRetry() {
        ++retry;
        return retry;
    }

    public String stringToTime(long time) {
        return Util.getStringForTime(builder, formatter, time);
    }

    public float getSpeed() {
        return isExo() ? exoPlayer.getPlaybackParameters().speed : ijkPlayer.getSpeed();
    }

    public long getPosition() {
        return isExo() ? exoPlayer.getCurrentPosition() : ijkPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return isExo() ? exoPlayer.getDuration() : ijkPlayer.getDuration();
    }

    public long getBuffered() {
        return isExo() ? exoPlayer.getBufferedPosition() : ijkPlayer.getBufferedPosition();
    }

    public boolean isPlaying() {
        return isExo() ? exoPlayer != null && exoPlayer.isPlaying() : ijkPlayer != null && ijkPlayer.isPlaying();
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player)[player];
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        exoPlayer.setPlaybackSpeed(speed);
        ijkPlayer.setSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed == 5 ? 0.25f : speed + addon;
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void togglePlayer() {
        stop();
        setPlayer(isExo() ? SYS : ++player);
    }

    public void nextPlayer() {
        stop();
        setPlayer(isExo() ? IJK : EXO);
    }

    public void toggleDecode() {
        setDecode(decode == HARD ? SOFT : HARD);
        Setting.putDecode(decode);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        if (isExo()) exoPlayer.seekTo(getPosition() + time);
        else if (isIjk()) ijkPlayer.seekTo(getPosition() + time);
    }

    public void seekTo(long time, boolean force) {
        if (time == 0 && !force) return;
        if (isExo()) exoPlayer.seekTo(time);
        else if (isIjk()) ijkPlayer.seekTo(time);
    }

    public void play() {
        if (isExo()) exoPlayer.play();
        else if (isIjk()) ijkPlayer.start();
    }

    public void pause() {
        if (isExo()) pauseExo();
        else if (isIjk()) pauseIjk();
    }

    public void stop() {
        reset();
        if (isExo()) stopExo();
        else if (isIjk()) stopIjk();
    }

    public void release() {
        stopParse();
        if (isExo()) releaseExo();
        else if (isIjk()) releaseIjk();
    }

    public boolean isRelease() {
        return exoPlayer == null || ijkPlayer == null;
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000;
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    public boolean haveTrack(int type) {
        if (isExo()) {
            return ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
        } else {
            return ijkPlayer.haveTrack(type);
        }
    }

    public void start(Channel channel) {
        if (channel.getUrl().isEmpty()) {
            ErrorEvent.url();
        } else {
            setMediaSource(channel);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.isError()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getUrl().isEmpty()) {
            ErrorEvent.url();
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            stopParse();
            parseJob = ParseJob.create(this).start(result, useParse);
            this.timeout = timeout;
        } else {
            this.timeout = timeout;
            setMediaSource(result);
        }
    }

    private int getVideoWidth() {
        return isExo() ? exoPlayer.getVideoSize().width : ijkPlayer.getVideoWidth();
    }

    private int getVideoHeight() {
        return isExo() ? exoPlayer.getVideoSize().height : ijkPlayer.getVideoHeight();
    }

    private void pauseExo() {
        exoPlayer.pause();
    }

    private void pauseIjk() {
        ijkPlayer.pause();
    }

    private void stopExo() {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
    }

    private void stopIjk() {
        ijkPlayer.stop();
    }

    private void releaseExo() {
        if (exoPlayer == null) return;
        exoPlayer.removeListener(this);
        exoPlayer.release();
        exoPlayer = null;
    }

    private void releaseIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.release();
        ijkPlayer = null;
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
    }

    private void setMediaSource(Result result) {
        SpiderDebug.log(errorCode + "," + result.getRealUrl());
        if (isIjk()) ijkPlayer.setMediaSource(IjkUtil.getSource(result));
        if (isExo()) exoPlayer.setMediaSource(ExoUtil.getSource(result, errorCode));
        if (isExo()) exoPlayer.prepare();
        setTimeoutCheck(result.getRealUrl());
    }

    private void setMediaSource(Channel channel) {
        SpiderDebug.log(errorCode + "," + channel.getUrl());
        if (isIjk()) ijkPlayer.setMediaSource(IjkUtil.getSource(channel));
        if (isExo()) exoPlayer.setMediaSource(ExoUtil.getSource(channel, errorCode));
        if (isExo()) exoPlayer.prepare();
        setTimeoutCheck(channel.getUrl());
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        SpiderDebug.log(errorCode + "," + url);
        if (isIjk()) ijkPlayer.setMediaSource(IjkUtil.getSource(headers, url));
        if (isExo()) exoPlayer.setMediaSource(ExoUtil.getSource(headers, url, errorCode));
        if (isExo()) exoPlayer.prepare();
        setTimeoutCheck(url);
    }

    private void setTimeoutCheck(String url) {
        App.post(runnable, timeout);
        PlayerEvent.url(url);
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    private void setTrack(Track item) {
        if (item.isExo(player)) setTrackExo(item);
        if (item.isIjk(player)) setTrackIjk(item);
    }

    private void setTrackExo(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setTrackIjk(Track item) {
        if (item.isSelected()) {
            ijkPlayer.selectTrack(item.getType(), item.getTrack());
        } else {
            ijkPlayer.deselectTrack(item.getType(), item.getTrack());
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        setMediaSource(headers, url);
        if (TextUtils.isEmpty(from)) return;
        Notify.show(ResUtil.getString(R.string.parse_from, from));
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        this.errorCode = error.errorCode;
        ErrorEvent.format(2);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        switch (state) {
            case Player.STATE_READY:
                PlayerEvent.ready();
                break;
            case Player.STATE_BUFFERING:
            case Player.STATE_ENDED:
            case Player.STATE_IDLE:
                PlayerEvent.state(state);
                break;
        }
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                PlayerEvent.state(Player.STATE_BUFFERING);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                PlayerEvent.ready();
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        ErrorEvent.format(1);
        return true;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        PlayerEvent.ready();
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        PlayerEvent.state(Player.STATE_ENDED);
    }
}
