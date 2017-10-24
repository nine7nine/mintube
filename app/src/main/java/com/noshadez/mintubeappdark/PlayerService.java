package com.noshadez.mintubeappdark;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;

import com.noshadez.mintubeappdark.AsyncTask.ImageLoadTask;
import com.noshadez.mintubeappdark.AsyncTask.LoadDetailsTask;
import com.noshadez.mintubeappdark.CustomViews.CircularImageView;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.noshadez.mintubeappdark.WebPlayer.*;

/**
 * Created by shyam on 12/2/16.
 */
public class PlayerService extends Service implements View.OnClickListener{

    private static Context mContext;
    private static PlayerService playerService;
    private static WindowManager windowManager;
    private static LinearLayout serviceHead, serviceClose, serviceCloseBackground, playerView;
    private FrameLayout webPlayerFrame;
    private static WindowManager.LayoutParams servHeadParams, servCloseParams, servCloseBackParams, playerViewParams;
    private WindowManager.LayoutParams param_player, params, param_close, param_close_back, parWebView;
    private RelativeLayout viewToHide;
    private static WebPlayer webPlayer;
    private static String VID_ID = "";
    private static String PLIST_ID = "";
    private static boolean isVideoPlaying = true;
    private boolean visible = true;
    private static RemoteViews viewBig;
    private static RemoteViews viewSmall;
    private static NotificationManager notificationManager;
    private static Notification notification;
    private static ImageView playerHeadImage;
    private int playerHeadCenterX, playerHeadCenterY, closeMinX, closeMinY, closeMaxX;
    private int scrnWidth, scrnHeight, defaultPlayerWidth,playerWidth, playerHeight, playerHeadSize, xAtHiding, yAtHiding, xOnAppear, yOnAppear = 0;

    //is inside the close button so to stop video
    private boolean isInsideClose = false;
    //is width entire to show video properly
    private boolean isEntireWidth = false;
    //Next Video to check whether next video is played or not
    private static boolean nextVid = false;
    //Replay Video if it's ended
    private static boolean replayVid = false;
    private static boolean replayPlaylist = false;

    private ImageView repeatTypeImg;
    private ImageView entireWidthImg;
    private SharedPreferences sharedPref;
    private static int noItemsInPlaylist, currVideoIndex;

    //Loop for Playlist
    private static boolean isLoopSetPlayList = false;
    //Don't Update head's Position and Hide icons and dropDown Image
    private boolean updateHead = true;

    public static void setPlayingStatus(int playingStatus) {
        if(playingStatus == -1){
            nextVid = true;
        }
        if(playingStatus == 3){
            Log.d("Status", "Buffering");
            String quality = Constants.getPlaybackQuality();
            Log.d("Quality", quality);
            loadScript(JavaScript.resetPlaybackQuality(quality));
        }
        if(playingStatus == 1){
            isVideoPlaying = true;
            viewBig.setImageViewResource(R.id.pause_play_video, R.drawable.ic_pause);
            viewSmall.setImageViewResource(R.id.pause_play_video, R.drawable.ic_pause);
            notificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
            if(nextVid){
                nextVid = false;
                loadScript(JavaScript.getVidUpdateNotiContent());
            }
            if(VID_ID.length() < 1){
                Log.d("If lenght", "Less that 1");
                loadScript(JavaScript.getVidUpdateNotiContent());
            }

            //Also Update if playlist is set for loop
            if(Constants.linkType == 1 && Constants.repeatType == 1 && !isLoopSetPlayList){
                Log.d("Setting ", "Playlist on Loop");
                loadScript(JavaScript.setLoopPlaylist());
                isLoopSetPlayList = true;
            }
        }
        else if(playingStatus == 2) {
            isVideoPlaying = false;
            viewBig.setImageViewResource(R.id.pause_play_video, R.drawable.ic_play);
            viewSmall.setImageViewResource(R.id.pause_play_video, R.drawable.ic_play);
            notificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        }
        else if(playingStatus == 0) {
            if(Constants.linkType == 1) {
                Log.d("Repeat Type ", Constants.repeatType + "");
                if(Constants.repeatType == 2){
                    loadScript(JavaScript.prevVideo());
                }
                //If not repeating then set notification icon to repeat when playlist ends
                if(Constants.repeatType == 0){
                    isPlaylistEnded();
                }
            }
            else {
                if(Constants.repeatType > 0){
                    loadScript(JavaScript.playVideoScript());
                }
                else {
                    if(Constants.finishOnEnd){
                        playerService.destroyServiceOnFinish();
                    }
                    else {
                        replayVid = true;
                        viewBig.setImageViewResource(R.id.pause_play_video, R.drawable.ic_replay);
                        viewSmall.setImageViewResource(R.id.pause_play_video, R.drawable.ic_replay);
                        notificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
                    }
                }
            }
        }
    }

    private void destroyServiceOnFinish() {
        Log.i("Trying To Destroy ", "...");
        stopForeground(true);
        stopSelf();
        stopService(new Intent(mContext, PlayerService.class));
    }

    private static void isPlaylistEnded() {
        loadScript(JavaScript.isPlaylistEnded());
    }

    public static void setNoItemsInPlaylist(int noItemsInPlaylist) {
        PlayerService.noItemsInPlaylist = noItemsInPlaylist;
    }

    public static void setCurrVideoIndex(int currVideoIndex) {
        PlayerService.currVideoIndex = currVideoIndex;
    }

    private static Context getAppContext(){
        return mContext;
    }

    public static void compare() {
        Log.d("Compairing", PlayerService.currVideoIndex + " " + PlayerService.noItemsInPlaylist);
        if(PlayerService.currVideoIndex == PlayerService.noItemsInPlaylist -1){
            Log.d("Playlist ", "Ended");
            replayPlaylist = true;
            viewBig.setImageViewResource(R.id.pause_play_video, R.drawable.ic_replay);
            viewSmall.setImageViewResource(R.id.pause_play_video, R.drawable.ic_replay);
            notificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onCreate() {

        mContext = this.getApplicationContext();
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        playerService = this;
        if(intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_WEB_ACTION)) {
            Log.d("Service ", "Started!");
            sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            Constants.repeatType = sharedPref.getInt(getString(R.string.repeat_type), 0);
            doThis(intent);

        }
        else if(intent.getAction().equals(Constants.ACTION.STOPFOREGROUND_WEB_ACTION)){
            Log.i("Trying To Destroy ", "...");
            stopForeground(true);
            stopSelf();
            stopService(new Intent(this, PlayerService.class));
        } else if(intent.getAction().equals(Constants.ACTION.PAUSE_PLAY_ACTION)){
            if (isVideoPlaying) {
                if (replayVid || replayPlaylist) {
                    if (Constants.linkType == 1) {
                        Log.i("Trying to ", "Replay Playlist");
                        loadScript(JavaScript.replayPlaylistScript());
                        replayPlaylist = false;
                    } else {
                        Log.i("Trying to ", "Replay Video");
                        loadScript(JavaScript.playVideoScript());
                        replayVid = false;
                    }
                } else {
                    Log.i("Trying to ", "Pause Video");
                    loadScript(JavaScript.pauseVideoScript());
                }
            } else {
                Log.i("Trying to ", "Play Video");
                loadScript(JavaScript.playVideoScript());
            }
        }
        else if(intent.getAction().equals(Constants.ACTION.NEXT_ACTION)){
            Log.d("Trying to ", "Play Next");
            if(Constants.linkType == 0){
                loadScript(JavaScript.seekToZero());
            }
            else {
                loadScript(JavaScript.nextVideo());
                nextVid = true;
            }
        }
        else if(intent.getAction().equals(Constants.ACTION.PREV_ACTION)){
            Log.d("Trying to ", "Play Previous");
            if(Constants.linkType == 0){
                loadScript(JavaScript.seekToZero());
            }
            else {
                loadScript(JavaScript.prevVideo());
                nextVid = true;
            }
        }

        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        isVideoPlaying = true;
        Constants.linkType = 0;
        Log.i("Status", "Destroyed!");
        if (playerView != null) {
            if(FullscreenWebPlayer.active){
                FullscreenWebPlayer.fullScreenAct.onBackPressed();
            }
            windowManager.removeView(playerView);
            windowManager.removeView(serviceHead);
            windowManager.removeView(serviceClose);
            webPlayer.destroy();
        }
    }

    public static void startVid(String vId, String pId) {
        PlayerService.VID_ID = vId;
        PlayerService.PLIST_ID = pId;
        if(pId == null) {
            setImageTitleAuthor(vId);
            loadScript(JavaScript.loadVideoScript(vId));
        }
        else{
            Log.d("Starting ", "Playlist.");
            loadScript(JavaScript.loadPlaylistScript(pId));
            setImageTitleAuthor(vId);
        }
    }

    /////-----------------*****************----------------onStartCommand---------------*****************-----------
    private void doThis(Intent intent) {

        Bundle b = intent.getExtras();

        if (b != null) {
            PlayerService.VID_ID = b.getString("VID_ID");
            PlayerService.PLIST_ID = b.getString("PLAYLIST_ID");
        }

        //Notification
        viewBig = new RemoteViews(
                this.getPackageName(),
                R.layout.notification_large
        );

        viewSmall = new RemoteViews(
                this.getPackageName(),
                R.layout.notification_small
        );

        //Intent to do things
        Intent doThings = new Intent(this, PlayerService.class);

        //Notification
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)

                .setSmallIcon(R.drawable.ic_status_bar)

                .setVisibility(Notification.VISIBILITY_PUBLIC)

                .setContent(viewSmall)

                // Automatically dismiss the notification when it is touched.
                .setAutoCancel(false);

        notification = builder.build();
        notification.bigContentView = viewBig;

        //Set Image and Headings
        setImageTitleAuthor(VID_ID);

        //stop Service using doThings Intent
        viewSmall.setOnClickPendingIntent(R.id.stop_service,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.STOPFOREGROUND_WEB_ACTION), 0));

        viewBig.setOnClickPendingIntent(R.id.stop_service,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.STOPFOREGROUND_WEB_ACTION), 0));

        //Pause, Play Video using doThings Intent
        viewSmall.setOnClickPendingIntent(R.id.pause_play_video,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.PAUSE_PLAY_ACTION) , 0));

        viewBig.setOnClickPendingIntent(R.id.pause_play_video,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.PAUSE_PLAY_ACTION), 0));

        //Next Video using doThings Intent
        viewSmall.setOnClickPendingIntent(R.id.next_video,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.NEXT_ACTION) , 0));

        viewBig.setOnClickPendingIntent(R.id.next_video,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.NEXT_ACTION), 0));

        //Previous Video using doThings Intent
        viewBig.setOnClickPendingIntent(R.id.previous_video,
                PendingIntent.getService(getApplicationContext(), 0,
                        doThings.setAction(Constants.ACTION.PREV_ACTION), 0));

        //Start Foreground Service
        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);

        //View
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        //Initialize Layout Parameters For All View
        InitParams();

        LayoutInflater inflater = (LayoutInflater) this.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);

        //Service Head
        serviceHead = (LinearLayout) inflater.inflate(R.layout.service_head, null, false);
        playerHeadImage = (ImageView) serviceHead.findViewById(R.id.song_icon);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        windowManager.addView(serviceHead, params);

        //Player View
        playerView = (LinearLayout) inflater.inflate(R.layout.player_webview, null, false);
        viewToHide = (RelativeLayout) playerView.findViewById(R.id.view_to_hide);
        webPlayerFrame = (FrameLayout) playerView.findViewById(R.id.web_player_frame);

        webPlayer = new WebPlayer(this);
        webPlayer.setupPlayer();

        viewToHide.addView(getPlayer(), parWebView);

        //------------------------------Got Player Id--------------------------------------------------------
        //noinspection MismatchedQueryAndUpdateOfCollection
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("Referer", "https://www.youtube.com/v/");
        if(Constants.linkType == 1) {
            Log.d("Starting ", "Playlist!!!");
            ConstantStrings.setPList(PLIST_ID);
            webPlayer.loadDataWithUrl(ConstantStrings.getPlayListHTML()
            );
        }
        else {
            ConstantStrings.setVid(VID_ID);
            Log.d("Starting ", "Single Video!!!");
            webPlayer.loadDataWithUrl(ConstantStrings.getVideoHTML()
            );
        }

        param_player.gravity = Gravity.TOP | Gravity.START;
        param_player.x = 0;
        param_player.y = playerHeadSize;
        windowManager.addView(playerView, param_player);

        //ChatHead Size
        ViewTreeObserver vto = serviceHead.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                serviceHead.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                playerHeadSize = serviceHead.getMeasuredHeight();

                Log.d("ChatHead Size", String.valueOf(playerHeadSize));
                param_player.y = playerHeadSize;
                xOnAppear = - playerHeadSize / 4;
                windowManager.updateViewLayout(playerView, param_player);
            }
        });

        //Player Width and Height
        vto = playerView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                playerView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                playerWidth = playerView.getMeasuredWidth();
                defaultPlayerWidth = playerWidth;
                playerHeight = playerView.getMeasuredHeight();
                Log.d("Player W and H ", playerWidth + " " + playerHeight);
            }
        });


        //Player Controls
        repeatTypeImg = (ImageView) playerView.findViewById(R.id.repeat_type);
        ImageView fullScreenImg = (ImageView) playerView.findViewById(R.id.fullscreen);

        //update Repeat Type Onclick
        updateRepeatTypeImage();
        repeatTypeImg.setOnClickListener(this);

        //Handle Full Screen
        fullScreenImg.setOnClickListener(this);

        //Chat Head Close
        serviceCloseBackground = (LinearLayout) inflater.inflate(R.layout.service_close_background, null, false);

        param_close_back.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        serviceCloseBackground.setVisibility(View.GONE);
        windowManager.addView(serviceCloseBackground, param_close_back);

        serviceClose = (LinearLayout) inflater.inflate(R.layout.service_close, null, false);

        param_close.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        serviceClose.setVisibility(View.GONE);
        windowManager.addView(serviceClose, param_close);

        final CircularImageView closeImage = (CircularImageView) serviceClose.findViewById(R.id.close_image);

        //-----------------Handle Click-----------------------------
        playerHeadImage.setOnClickListener(this);

        //getting Screen Width and Height
        WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        scrnWidth = size.x;
        scrnHeight = size.y;

        //-----------------Handle Touch-----------------------------

        //if just a click no need to show the close button
        final boolean[] needToShow = {true};

        playerHeadImage.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY, finalTouchX, finalTouchY;

            @Override
            public boolean onTouch(View v, final MotionEvent event) {
                if(isEntireWidth) {
                    playerWidth = scrnWidth;
                }
                else{
                    playerWidth = defaultPlayerWidth;
                }
                final WindowManager.LayoutParams params = (WindowManager.LayoutParams) serviceHead.getLayoutParams();
                WindowManager.LayoutParams param_player = (WindowManager.LayoutParams) playerView.getLayoutParams();
                serviceCloseBackground.setVisibility(View.VISIBLE);
                final Handler handleLongTouch = new Handler();
                final Runnable setVisible = new Runnable() {
                    @Override
                    public void run() {
                        if(needToShow[0]) {
                            serviceClose.setVisibility(View.VISIBLE);
                        }
                    }
                };
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        needToShow[0] = true;
                        handleLongTouch.postDelayed(setVisible, 100);
                        return true;
                    case MotionEvent.ACTION_UP:
                        finalTouchX = event.getRawX();
                        finalTouchY = event.getRawY();
                        needToShow[0] = false;
                        handleLongTouch.removeCallbacksAndMessages(null);
                        serviceCloseBackground.setVisibility(View.GONE);
                        serviceClose.setVisibility(View.GONE);
                        if (isClicked(initialTouchX, finalTouchX, initialTouchY, finalTouchY)) {
                            playerHeadImage.performClick();
                        }
                        else {
                            //stop if inside the close Button
                            if(isInsideClose){
                                Log.i("Inside Close ", "...");
                                stopForeground(true);
                                stopSelf();
                                stopService(new Intent(PlayerService.this, PlayerService.class));
                            }
                            else if (!visible) {
                                if (params.x > scrnWidth / 2) {
                                    params.x = scrnWidth - playerHeadSize + playerHeadSize / 4;
                                } else {
                                    params.x = -playerHeadSize / 4;
                                }
                                windowManager.updateViewLayout(serviceHead, params);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int newX, newY;
                        newX = initialX + (int) (event.getRawX() - initialTouchX);
                        newY = initialY + (int) (event.getRawY() - initialTouchY);
                        if (visible) {
                            if (newX < 0) {
                                param_player.x = 0;
                                params.x = 0;
                            }
                            else {
                                param_player.x = newX;
                                params.x = newX;
                            }
                            if (newY < 0) {
                                param_player.y = playerHeadSize;
                                params.y = 0;
                            } else if (playerHeight + newY + playerHeadSize > scrnHeight) {
                                //Continue with the drag and don't update head params
                                // set updateHead = true - to avoide disappearing
                                updateHead = true;
                                // Disable hideplayer && params to keep player on screen
                                //hidePlayer();
                                //params.y = newY;
                            } else {
                                param_player.y = newY + playerHeadSize;
                                params.y = newY;
                            }
                            windowManager.updateViewLayout(serviceHead, params);
                            //update player params if visible
                            if(visible)
                                windowManager.updateViewLayout(playerView, param_player);
                        }
                        else {
                            if(newY + playerHeadSize > scrnHeight){
                                params.y = scrnHeight - playerHeadSize;
                            }
                            else{
                                params.y = newY;
                            }
                            params.x = newX;
                            int [] t = new int[2];

                            windowManager.updateViewLayout(serviceHead, params);
                        }
                        return true;
                }
                return false;
            }

            private boolean isClicked(float startX, float endX, float startY, float endY) {
                float differenceX = Math.abs(startX - endX);
                float differenceY = Math.abs(startY - endY);
                return !(differenceX >= 5 || differenceY >= 5);
            }
        });
    }
    //Update Image of Repeat Type Button
    private void updateRepeatTypeImage() {
        if(Constants.repeatType == 0){
            repeatTypeImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_repeat_none));
        }
        else if(Constants.repeatType == 1){
            repeatTypeImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_repeat));
        }
        else if(Constants.repeatType == 2){
            repeatTypeImg.setImageDrawable(getResources().getDrawable(R.drawable.ic_repeat_one));
        }
    }

    //Set Image and Headings in Notification
    public static void setImageTitleAuthor(String videoId) {

        Log.d("Setting ", "Image, Title, Author");

        try {
            Bitmap bitmap = new ImageLoadTask("https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg").execute().get();
            String details;
            details = new LoadDetailsTask(
                    "https://www.youtube.com/oembed?url=https://www.youtu.be/watch?v=" + videoId + "&format=json")
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR).get();
            JSONObject detailsJson = new JSONObject(details);
            String title = detailsJson.getString("title");
            String author = detailsJson.getString("author_name");

            viewBig.setImageViewBitmap(R.id.thumbnail, bitmap);
            viewSmall.setImageViewBitmap(R.id.thumbnail, bitmap);
//            playerHeadImage.setImageBitmap(bitmap);

            viewBig.setTextViewText(R.id.title, title);

            viewBig.setTextViewText(R.id.author, author);
            viewSmall.setTextViewText(R.id.author, author);

            notificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification);

        } catch (InterruptedException | ExecutionException | MalformedURLException | JSONException e) {
            e.printStackTrace();
        }
    }

    public static void addStateChangeListener() {
        loadScript(JavaScript.onPlayerStateChangeListener());
    }
    private boolean isInsideClose() {
        if(playerHeadCenterX >= closeMinX && playerHeadCenterX <= closeMaxX){
            if(playerHeadCenterY >= closeMinY){
                return true;
            }
        }
        return false;
    }
    private int getStatusBarHeight() {
        return (int) Math.ceil(25 * getApplicationContext().getResources().getDisplayMetrics().density);
    }

    //Play video again on exit full screen
    public static void startAgain() {
        windowManager.addView(serviceHead, servHeadParams);
        windowManager.addView(serviceClose, servCloseParams);
        windowManager.addView(serviceCloseBackground, servCloseBackParams);
        windowManager.addView(playerView, playerViewParams);
        loadScript(JavaScript.playVideoScript());
    }


    //Clicks Handled
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            //Handle Hiding of player
            case R.id.song_icon:
                Log.d("Clicked", "Click!");
                if (visible) {
                    //Make head sticky with the edge so update head params
                    updateHead = true;
                    hidePlayer();
                } else {
                   showPlayer();
                }
                break;
            //Handle Full Screen
            case R.id.fullscreen:
                loadScript(JavaScript.pauseVideoScript());
                Intent fullScreenIntent = new Intent(getAppContext(), FullscreenWebPlayer.class);
                fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //remove Views
                windowManager.removeView(serviceHead);
                servHeadParams = (WindowManager.LayoutParams) serviceHead.getLayoutParams();
                windowManager.removeView(serviceClose);
                servCloseParams = (WindowManager.LayoutParams) serviceClose.getLayoutParams();
                windowManager.removeView(serviceCloseBackground);
                servCloseBackParams = (WindowManager.LayoutParams) serviceCloseBackground.getLayoutParams();
                windowManager.removeView(playerView);
                playerViewParams = (WindowManager.LayoutParams) playerView.getLayoutParams();
                //start full Screen Player
                mContext.startActivity(fullScreenIntent);
                break;
            //Handle Repeat Settings
            case R.id.repeat_type:
                SharedPreferences.Editor editor = sharedPref.edit();
                if (Constants.repeatType == 0) {
                    editor.putInt(getString(R.string.repeat_type), 1);
                    editor.apply();
                    Constants.repeatType = 1;
                    if (Constants.linkType == 1) {
                        loadScript(JavaScript.setLoopPlaylist());
                    }
                    updateRepeatTypeImage();
                } else if (Constants.repeatType == 1) {
                    editor.putInt(getString(R.string.repeat_type), 2);
                    editor.apply();
                    Constants.repeatType = 2;
                    if (Constants.linkType == 1) {
                        loadScript(JavaScript.unsetLoopPlaylist());
                    }
                    updateRepeatTypeImage();
                } else if (Constants.repeatType == 2) {
                    editor.putInt(getString(R.string.repeat_type), 0);
                    editor.apply();
                    Constants.repeatType = 0;
                    if (Constants.linkType == 1) {
                        loadScript(JavaScript.unsetLoopPlaylist());
                    }
                    updateRepeatTypeImage();
                }
                break;
            default:
                break;
        }
    }

    private void showPlayer() {
        viewToHide.setVisibility(View.VISIBLE);
        //Store current to again hidden icon will come here
        if(params.x > 0) {
            xOnAppear = scrnWidth - playerHeadSize + playerHeadSize / 4;
        }
        else{
            xOnAppear = - playerHeadSize / 4;
        }
        yOnAppear = params.y;
        //Update the icon and player to player's hidden position
        params.x = xAtHiding;
        params.y = yAtHiding;
        param_player.x = xAtHiding;
        param_player.y = yAtHiding + playerHeadSize;
        windowManager.updateViewLayout(playerView, param_player);
        windowManager.updateViewLayout(serviceHead, params);
        visible = true;
    }

    private void hidePlayer() {
//        Log.d("Head x , y ", params.x + " " + params.y);
//        Log.d("Player x , y ", param_player.x + " " + param_player.y);
//        Log.d("Head Size", String.valueOf(playerHeadImage.getHeight()));
        xAtHiding = params.x;
        yAtHiding = params.y;
        //To hide the Player View
        final WindowManager.LayoutParams tmpPlayerParams = new WindowManager.LayoutParams(
                100,
                100,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        tmpPlayerParams.x = scrnWidth;
        tmpPlayerParams.y = scrnHeight;
        windowManager.updateViewLayout(playerView, tmpPlayerParams);
        viewToHide.setVisibility(View.GONE);
        if(updateHead) {
            params.x = xOnAppear;
            params.y = yOnAppear;
            windowManager.updateViewLayout(serviceHead, params);
        }
        visible = false;
    }

    //Layout Params Initialized
    private void InitParams() {
        //Service Head Params
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        //Web Player Params
        parWebView = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        );

        //Player View Params
        param_player = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        //Close Backgroung Params
        param_close_back = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        //Close Image Params
        param_close = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

    }
}