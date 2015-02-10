package com.invano.fingerlock;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.invano.fingerlock.util.LogFile;
import com.invano.fingerlock.util.Util;
import com.samsung.android.sdk.pass.SpassFingerprint;

public class LockFakeActivity extends Activity implements FingerprintScan.FingerprintScanListener {

    private String lockedPkgName;
    private Intent lockedApp;

    private TextView label;
    private TextView unlockMsg;
    private ImageView icon;
    private ImageView fingerprintAnim;
    private TextView resLabel;

    private Drawable ic = null;
    private String lb = null;
    private int backgroundColor;

    private ActivityManager am;
    private FingerprintScan scan;

    private MaterialDialog dialog;

    private boolean configurationChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.lock_fake_activity);

        label = (TextView) findViewById(R.id.label_locked);
        icon = (ImageView) findViewById(R.id.icon_locked);
        unlockMsg = (TextView) findViewById(R.id.unlock_msg_textview);
        fingerprintAnim = (ImageView) findViewById(R.id.fingerprintImage);
        resLabel = (TextView) findViewById(R.id.textViewSpass);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_270) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) fingerprintAnim.getLayoutParams();
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            fingerprintAnim.setRotation(90);
            fingerprintAnim.setLayoutParams(lp);
        }

        if (savedInstanceState == null) {

            backgroundColor = getIntent().getIntExtra(Util.BACKGROUND_COLOR, 0);
            lockedApp = getIntent().getParcelableExtra(Util.ORIG_INTENT);
            lockedPkgName = getIntent().getStringExtra(Util.LOCK);
        }
        else {

            backgroundColor = savedInstanceState.getInt(Util.BACKGROUND_COLOR);
            lockedApp = savedInstanceState.getParcelable(Util.ORIG_INTENT);
            lockedPkgName = savedInstanceState.getString(Util.LOCK);
        }

        getWindow().getDecorView().setBackgroundColor(backgroundColor);


        am = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(lockedPkgName);
        am.killBackgroundProcesses(Util.MY_PACKAGE_NAME);

        Long timestamp = System.currentTimeMillis();
        Long permitTimestamp = FLApplication.getPermitTimeHook(lockedPkgName);
        if(permitTimestamp != 0 && timestamp - permitTimestamp <= Util.MAX_TRANSITION_TIME_MS) {
            onIdentifySucceded();
        }
        else {
            prepareWindow();

            scan = new FingerprintScan(getApplicationContext(), this);
            scan.initialize();
        }
    }

    private void prepareWindow() {

        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            ic = packageManager.getApplicationIcon(lockedPkgName);
            applicationInfo = packageManager.getApplicationInfo(lockedPkgName, 0);
            lb = (String)((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : "???");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(null, e.toString());
        }
        icon.setImageDrawable(ic);
        label.setText(lb);
        Bitmap icBitmap = Util.drawableToBitmap(ic);

        Palette.generateAsync(icBitmap, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                int swatchRgb;
                if (swatch != null) {
                    swatchRgb = swatch.getRgb();
                    label.setTextColor(swatch.getTitleTextColor());
                    unlockMsg.setTextColor(swatch.getBodyTextColor());
                    resLabel.setTextColor(swatch.getBodyTextColor());
                }
                else {
                    swatchRgb = R.color.transparent_back;
                }

                ObjectAnimator backgroundColorAnimator = ObjectAnimator.ofObject(
                        getWindow().getDecorView(),
                        "backgroundColor",
                        new ArgbEvaluator(),
                        R.color.transparent_back,
                        swatchRgb);
                backgroundColorAnimator.setDuration(1000);
                backgroundColorAnimator.start();
            }
        });

    }

    @Override
    protected void onStop() {
        scan.cancelIdentify();
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(Util.BACKGROUND_COLOR, backgroundColor);
        outState.putParcelable(Util.ORIG_INTENT, lockedApp);
        outState.putString(Util.LOCK, lockedPkgName);

        configurationChanged = true;
    }

    @Override
    public void onIdentifySucceded() {
        fingerprintAnim.setImageResource(R.drawable.scan_success);
        resLabel.setText(getString(R.string.spass_auth_success));
        unlockActivity();
    }

    @Override
    public void onIdentifyStarted() {
        fingerprintAnim.setImageResource(R.drawable.scan_dot);
    }

    @Override
    public void onIdentifyReady() {
    }

    @Override
    public void onIdentifyErrorAttempts() {
        fingerprintAnim.setImageResource(R.drawable.scan_mismatch);
        resLabel.setText(getString(R.string.spass_auth_failed_attempts));
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                LogFile.i(LockFakeActivity.this, "Access to " + lockedPkgName +" failed");
                finish();
            }
        }, 2000);
    }

    @Override
    public void onInitializationFailed(int msg) {

        dialog = new MaterialDialog.Builder(this)
                .title(R.string.error)
                .content(msg)
                .positiveText(R.string.ok)  // the default is 'Accept'
                .negativeText(R.string.close)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        finish();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .cancelable(false)
                .build();
        dialog.show();
    }

    @Override
    public void onUnregisteredFingerprints() {

        dialog = new MaterialDialog.Builder(this)
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
                        finish();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .build();
        dialog.show();
    }

    @Override
    public void onIdentifyFailed(int status) {
        fingerprintAnim.setImageResource(R.drawable.scan_mismatch);
        setFailedAnimationEnd();

        Log.e("SPASS", "Failed: " + FingerprintScan.getEventStatusName(status));
        if (status == SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED) {
            resLabel.setText(getString(R.string.spass_auth_failed));
            scan = new FingerprintScan(getApplicationContext(), this);
            scan.initialize();
        } else if (status == SpassFingerprint.STATUS_QUALITY_FAILED) {
            resLabel.setText(getString(R.string.spass_quality_failed));
            scan = new FingerprintScan(getApplicationContext(), this);
            scan.initialize();
        } else if (status == SpassFingerprint.STATUS_SENSOR_FAILED) {
            resLabel.setText(getString(R.string.spass_sensor_failed));
        } else {
            if (!configurationChanged) {
                LogFile.i(this, "Access to " + lockedPkgName +" failed");
                finish();
            }
        }
    }

    private void unlockActivity() {
        FLApplication.setPermitTimeHook(lockedPkgName, System.currentTimeMillis());
        am.killBackgroundProcesses(Util.MY_PACKAGE_NAME);

        Intent intent = new Intent(lockedApp);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent);
        } catch (SecurityException e) {
            Intent intent_option = getPackageManager().getLaunchIntentForPackage(lockedPkgName);
            intent_option.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent_option);
            overridePendingTransition(0,0);
        } finally {
            Log.i("FingerLock", "======= finish() unlockActivity =======");
            finish();
        }
    }

    private void setFailedAnimationEnd(){
        Handler h = new Handler();
        h.postDelayed(new Runnable(){
            public void run(){
                fingerprintAnim.setImageResource(R.drawable.highlight_dot);
                resLabel.setText(getString(R.string.spass_init));
            }
        }, 1750);
    }

}