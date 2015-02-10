package com.invano.fingerlock;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.invano.fingerlock.util.LogFile;
import com.invano.fingerlock.util.Util;
import com.samsung.android.sdk.pass.SpassFingerprint;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Cozzi Emanuele on 23/01/15 at 09:53.
 */

public class LockService extends Service implements View.OnKeyListener, FingerprintScan.FingerprintScanListener {

    private static final String TAG = LockService.class.getName();

    private static final boolean DEBUG = true;

    private static final int NOTIFICATION_ID = 0xEEAA90;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private View mRootView;
    private ImageView mIconImageView;
    private TextView mLabelTextView;
    private TextView mUnlockMsg;
    private TextView mSwipeFingerprintMsg;
    private ImageView mAnimImage;

    private String mAppPackageName;
    private int mBackgroundColor;
    private FingerprintScan mFingerprint;

    private HashSet<String> mUnlockedApps = new HashSet<String>();

    /**
     * BroadcastReceiver signalled when a locked app goes from foreground
     * to background. Its job is to tear down the LockService.
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (DEBUG)
                Log.d(TAG, "onReceive() called - action: " + intent.getAction());

            if ("com.invano.fingerlock.UNLOCK_FINISHED".equals(intent.getAction())) {

                mUnlockedApps.remove(intent.getStringExtra(Util.LOCK));

                if (mUnlockedApps.isEmpty()) {
                    stopSelf();
                }
            }
            else if ("com.invano.fingerlock.UNLOCK_STARTED".equals(intent.getAction())) {

                mUnlockedApps.add(intent.getStringExtra(Util.LOCK));
            }

        }
    };

    /**
     * We don't care of binding since this Service in meant only
     * to display a view, locking and/or 2unlocking a package
     *
     * @param intent of the application component
     * @return null
     */
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG)
            Log.d(TAG, "onCreate() called");

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mRootView = initView();
        mIconImageView = (ImageView) mRootView.findViewById(R.id.icon_locked);
        mLabelTextView = (TextView) mRootView.findViewById(R.id.label_locked);
        mUnlockMsg = (TextView) mRootView.findViewById(R.id.unlock_msg_textview);
        mSwipeFingerprintMsg = (TextView) mRootView.findViewById(R.id.textViewSpass);
        mAnimImage = (ImageView) mRootView.findViewById(R.id.fingerprintImage);

        Notification notification = new Notification.Builder(LockService.this)
                .setContentTitle(getString(R.string.app_name))
                        //TODO convert hard coded strings
                //.setContentText("Monitoring...")
                .setTicker("FingerLock activated")
                .setSmallIcon(R.drawable.ic_stat_ico_web)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.invano.fingerlock.UNLOCK_FINISHED");
        filter.addAction("com.invano.fingerlock.UNLOCK_STARTED");
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {

        if (DEBUG)
            Log.d(TAG, "onDestroy() called");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (DEBUG)
            Log.d(TAG, "onStartCommand() called");

        mAppPackageName = intent.getStringExtra(Util.LOCK);
        mBackgroundColor = intent.getIntExtra(Util.BACKGROUND_COLOR, 0x000000);

        if (DEBUG)
            Log.d(TAG, "onStartCommand() package: " + mAppPackageName
                    + ", color: " + Integer.toHexString(mBackgroundColor));

        mUnlockedApps.add(mAppPackageName);

        mWindowManager.addView(mRootView, mLayoutParams);
        adjustView();

        mFingerprint = new FingerprintScan(getApplicationContext(), this);
        mFingerprint.initialize();

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();

        switch (rotation) {

            case Surface.ROTATION_0:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                mAnimImage.setLayoutParams(layoutParams);
                mAnimImage.setRotation(0);
                break;
            case Surface.ROTATION_90:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
                mAnimImage.setLayoutParams(layoutParams);
                mAnimImage.setRotation(270);
                break;
            case Surface.ROTATION_270:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START);
                layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
                mAnimImage.setLayoutParams(layoutParams);
                mAnimImage.setRotation(90);
                break;
            default:
                break;
        }
    }

    /**
     * Initialize layout parameters and the overlay view
     *
     * @return the root view inflated
     */
    private View initView() {

        if (DEBUG)
            Log.d(TAG, "initView() called");

        View root;

        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        mLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;

        LayoutInflater li = LayoutInflater.from(this);

        root = li.inflate(R.layout.lock_fake_activity, null);
        root.setOnKeyListener(this);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        return root;
    }

    /**
     * The inflated root view is adjusted depending on the calling application.
     * Set icon and label starting from the package name and then animate
     * the background until the icon predominant color is matched.
     */
    private void adjustView() {

        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo;

        Drawable mIcon;
        String mLabel;
        try {
            applicationInfo = packageManager.getApplicationInfo(mAppPackageName, 0);
            mIcon = packageManager.getApplicationIcon(applicationInfo);
            mLabel = packageManager.getApplicationLabel(applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "adjustView() NameNotFoundException - using launcher icon and package name");
            mIcon = getResources().getDrawable(R.drawable.ic_launcher);
            mLabel = mAppPackageName;
        }

        mIconImageView.setImageDrawable(mIcon);
        mLabelTextView.setText(mLabel);

        Bitmap icBitmap = Util.drawableToBitmap(mIcon);

        //Palette API. Generate a dark vibrant swatch asynchronously.
        Palette.generateAsync(icBitmap, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                int swatchRgb;
                if (swatch != null) {
                    swatchRgb = swatch.getRgb();
                    mLabelTextView.setTextColor(swatch.getTitleTextColor());
                    mUnlockMsg.setTextColor(swatch.getBodyTextColor());
                    mSwipeFingerprintMsg.setTextColor(swatch.getBodyTextColor());
                } else {
                    swatchRgb = Color.BLACK;
                }

                if (DEBUG)
                    Log.d(TAG, "adjustView() called: color= " + swatchRgb);

                ObjectAnimator backgroundColorAnimator = ObjectAnimator.ofObject(
                        mRootView,              //view
                        "backgroundColor",      //property
                        new ArgbEvaluator(),
                        mBackgroundColor,       //from
                        swatchRgb);             //to

                backgroundColorAnimator.setDuration(1000);
                backgroundColorAnimator.start();
            }
        });

    }

    /**
     * Remove the root view from top and if not unlocked bring the user
     * to the home launcher.
     *
     * @param unlocked whether the app should be unlocked or not.
     */
    private void unlock(boolean unlocked) {

        mWindowManager.removeView(mRootView);

        if (!unlocked) {
            final Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);

            stopSelf();
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {

        if (DEBUG)
            Log.d(TAG, "onKey() called - event: " + event.toString());

        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {

            mFingerprint.cancelIdentify();
        }

        return true;
    }

    @Override
    public void onIdentifySucceded() {

        if (DEBUG)
            Log.d(TAG, "onIdentifySucceded() called");

        unlock(true);
    }

    @Override
    public void onIdentifyStarted() {

        if (DEBUG)
            Log.d(TAG, "onIdentifyStarted() called");

        mAnimImage.setImageResource(R.drawable.scan_dot);
    }

    @Override
    public void onIdentifyReady() {

        if (DEBUG)
            Log.d(TAG, "onIdentifyReady() called");
    }

    @Override
    public void onIdentifyFailed(int status) {

        if (DEBUG)
            Log.d(TAG, "onIdentifyFailed() called");

        mAnimImage.setImageResource(R.drawable.scan_mismatch);
        setFailedAnimationEnd();

        Log.d("TAG", "onIdentifyFailed() result: " + FingerprintScan.getEventStatusName(status));

        if (status == SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED) {

            mSwipeFingerprintMsg.setText(getString(R.string.spass_auth_failed));
            mFingerprint = new FingerprintScan(LockService.this, this);
            mFingerprint.initialize();
        } else if (status == SpassFingerprint.STATUS_QUALITY_FAILED) {

            mSwipeFingerprintMsg.setText(getString(R.string.spass_quality_failed));
            mFingerprint = new FingerprintScan(LockService.this, this);
            mFingerprint.initialize();

        } else if (status == SpassFingerprint.STATUS_SENSOR_FAILED) {
            mSwipeFingerprintMsg.setText(getString(R.string.spass_sensor_failed));

        } else {
            LogFile.i(this, "Access to " + mAppPackageName +" failed");
            unlock(false);
        }
    }

    @Override
    public void onIdentifyErrorAttempts() {

        if (DEBUG)
            Log.d(TAG, "onIdentifyErrorAttempts() called");

        mAnimImage.setImageResource(R.drawable.scan_mismatch);
        mSwipeFingerprintMsg.setText(getString(R.string.spass_auth_failed_attempts));

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                LogFile.i(LockService.this, "Access to " + mAppPackageName + " failed");
                unlock(false);
            }
        }, 2000);
    }

    @Override
    public void onInitializationFailed(int msg) {

        if (DEBUG)
            Log.d(TAG, "onInitializationFailed() called");

        new MaterialDialog.Builder(LockService.this)
                .title(R.string.error)
                .content(msg)
                .positiveText(R.string.ok)  // the default is 'Accept'
                .negativeText(R.string.close)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        unlock(false);
                        stopSelf();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                        unlock(false);
                        stopSelf();
                    }
                })
                .cancelable(false)
                .build()
                .show();
    }

    @Override
    public void onUnregisteredFingerprints() {

        if (DEBUG)
            Log.d(TAG, "onUnregisteredFingerprints() called");

        new MaterialDialog.Builder(this)
                .title(R.string.error)
                .content(R.string.no_finger)
                .positiveText(R.string.register)  // the default is 'Accept'
                .negativeText(R.string.close)
                .titleColorRes(R.color.primaryColor)
                .positiveColorRes(R.color.accentColor)
                .negativeColorRes(R.color.accentColor)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        Intent i = new Intent(Settings.ACTION_SETTINGS);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        unlock(true);       //We set true just for the purpose of removing the window.
                        stopSelf();         //Since the Service is stopped, the app will be relocked on next access.
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                        unlock(true);       //Read previous lines.
                        stopSelf();
                    }
                })
                .build()
                .show();
    }

    private void setFailedAnimationEnd() {

        new Handler().postDelayed(new Runnable() {
            public void run() {
                mAnimImage.setImageResource(R.drawable.highlight_dot);
                mSwipeFingerprintMsg.setText(getString(R.string.spass_init));
            }
        }, 1750);
    }


    public static boolean isRunning(Context c) {

        if (DEBUG)
            Log.d(TAG, "isRunning() called");

        ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (LockService.class.getName().equals(service.service.getClassName())) {
                if (DEBUG)
                    Log.d(TAG, "isRunning() = true");
                return true;
            }
        }
        if (DEBUG)
            Log.d(TAG, "isRunning() = false");
        return false;
    }
}
