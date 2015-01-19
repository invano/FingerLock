package com.invano.fingerlock.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.invano.fingerlock.FingerprintScan;
import com.invano.fingerlock.R;
import com.invano.fingerlock.util.LogFile;
import com.invano.fingerlock.util.Util;
import com.samsung.android.sdk.pass.SpassFingerprint;

public class LockActivity extends Activity implements FingerprintScan.FingerprintScanListener {

    private TextView label;
    private TextView unlockMsg;
    private ImageView icon;
    private ImageView fingerprintAnim;
    private TextView resLabel;

    private FingerprintScan scan;
    private int attempts = 0;

    private int backgroundColor;

    private boolean backPressed = false;
    private boolean onFocus = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.lock_fake_activity);

        label = (TextView) findViewById(R.id.label_locked);
        icon = (ImageView) findViewById(R.id.icon_locked);
        unlockMsg = (TextView) findViewById(R.id.unlock_msg_textview);
        fingerprintAnim = (ImageView) findViewById(R.id.fingerprintImage);
        resLabel = (TextView) findViewById(R.id.textViewSpass);

        backgroundColor = getIntent().getIntExtra(Util.BACKGROUND_COLOR, 0);
        getWindow().getDecorView().setBackgroundColor(backgroundColor);

        startLocking();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.lock_fake_activity);
            label = (TextView) findViewById(R.id.label_locked);
            icon = (ImageView) findViewById(R.id.icon_locked);
            fingerprintAnim = (ImageView) findViewById(R.id.fingerprintImage);
            resLabel = (TextView) findViewById(R.id.textViewSpass);
        }
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.lock_fake_activity_land );
            label = (TextView) findViewById(R.id.label_locked);
            icon = (ImageView) findViewById(R.id.icon_locked);
            fingerprintAnim = (ImageView) findViewById(R.id.fingerprintImage);
            resLabel = (TextView) findViewById(R.id.textViewSpass);
        }
        icon.setImageResource(R.drawable.ic_launcher);
        label.setText(getString(R.string.app_name));
    }

    @Override
    protected void onStart() {
        super.onStart();
        onFocus = true;
        backPressed = false;
        if(!scan.isIdentifying()) {
            startLocking();
        }
    }

    @Override
    protected void onStop() {
        onFocus = false;
        scan.cancelIdentify();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        backPressed = true;
        LogFile.i(this, "Access to FingerLock failed");
        super.onBackPressed();
    }

    protected void startLocking() {

        prepareWindow();

        scan = new FingerprintScan(getApplicationContext(), this);
        scan.initialize();
    }

    protected void prepareWindow() {

        icon.setImageResource(R.drawable.ic_launcher);
        label.setText(getString(R.string.app_name));
        Bitmap icBitmap = Util.drawableToBitmap(icon.getDrawable());

        Palette.generateAsync(icBitmap, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                if (swatch != null) {
                    final ObjectAnimator backgroundColorAnimator = ObjectAnimator.ofObject(
                            getWindow().getDecorView(),
                            "backgroundColor",
                            new ArgbEvaluator(),
                            backgroundColor,
                            swatch.getRgb());
                    backgroundColorAnimator.setDuration(1000);
                    backgroundColorAnimator.start();
                    label.setTextColor(swatch.getTitleTextColor());
                    unlockMsg.setTextColor(swatch.getBodyTextColor());
                    resLabel.setTextColor(swatch.getBodyTextColor());
                }
            }
        });

    }

    @Override
    public void onIdentifySucceded() {
        fingerprintAnim.setImageResource(R.drawable.scan_success);
        resLabel.setText(getString(R.string.spass_auth_success));
        sendActivityResultOk();
    }

    @Override
    public void onIdentifyStarted() {
        fingerprintAnim.setImageResource(R.drawable.scan_dot);
    }

    @Override
    public void onIdentifyReady() {
    }

    @Override
    public void onIdentifyFailed(int status) {
        fingerprintAnim.setImageResource(R.drawable.scan_mismatch);

        Log.e("SPASS", "Failed: " + FingerprintScan.getEventStatusName(status));
        if (status == SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED) {
            attempts++;
            if (attempts < 5) {
                setFailedAnimationEnd();
                resLabel.setText(getString(R.string.spass_auth_failed));
                scan = new FingerprintScan(getApplicationContext(), this);
                scan.initialize();
            } else {
                LogFile.i(this, "Access to FingerLock failed");
                onIdentifyErrorAttempts();
            }
        } else if (status == SpassFingerprint.STATUS_QUALITY_FAILED) {
            setFailedAnimationEnd();
            resLabel.setText(getString(R.string.spass_quality_failed));
            scan = new FingerprintScan(getApplicationContext(), this);
            scan.initialize();
        } else {
            if (onFocus || backPressed) {
                sendActivityResultCancel();
            } else {
                setFailedAnimationEnd();
            }
        }
    }

    @Override
    public void onIdentifyErrorAttempts() {
        fingerprintAnim.setImageResource(R.drawable.scan_mismatch);
        resLabel.setText(getString(R.string.spass_auth_failed_attempts));
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                sendActivityResultCancel();
            }
        }, 3000);
    }

    @Override
    public void onInitializationFailed(int msg) {
        new MaterialDialog.Builder(this)
                .title(R.string.error)
                .content(msg)
                .positiveText(R.string.ok)  // the default is 'Accept'
                .negativeText(R.string.close)
                .titleColorRes(R.color.primaryColor)
                .positiveColorRes(R.color.accentColor)
                .negativeColorRes(R.color.accentColor)
                .callback(new MaterialDialog.Callback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        sendActivityResultCancel();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                        sendActivityResultOk();
                    }
                })
                .cancelable(false)
                .build()
                .show();
    }

    @Override
    public void onUnregisteredFingerprints() {

        new MaterialDialog.Builder(this)
                .title(R.string.error)
                .content(R.string.no_finger)
                .positiveText(R.string.register)  // the default is 'Accept'
                .negativeText(R.string.close)
                .titleColorRes(R.color.primaryColor)
                .positiveColorRes(R.color.accentColor)
                .negativeColorRes(R.color.accentColor)
                .callback(new MaterialDialog.Callback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        Intent i = new Intent(Settings.ACTION_SETTINGS);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        sendActivityResultCancel();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                        sendActivityResultCancel();
                    }
                })
                .build()
                .show();
    }

    private void sendActivityResultOk() {
        Intent returnIntent = new Intent();
        setResult(RESULT_OK, returnIntent);
        overridePendingTransition(0,0);
        finish();
    }

    private void sendActivityResultCancel() {
        Intent returnIntent = new Intent();
        setResult(RESULT_CANCELED, returnIntent);
        finish();
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
