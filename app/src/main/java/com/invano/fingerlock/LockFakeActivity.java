package com.invano.fingerlock;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.invano.fingerlock.util.Util;
import com.invano.fingerlock.util.LogFile;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;

public class LockFakeActivity extends Activity {

    private String lockedPkgName;
    private Intent lockedApp;
    private Context context;
    private TextView label;
    private ImageView icon;
    private Drawable ic = null;
    private String lb = null;

    private SpassFingerprint mSpassFingerprint;
    private Spass mSpass;
    private boolean onReadyIdentify = false;

    private ActivityManager am;


    private SpassFingerprint.IdentifyListener listener = new SpassFingerprint.IdentifyListener() {

        @Override
        public void onFinished(int eventStatus) {
            if (eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS
                    || eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS) {

                onReadyIdentify = false;
                fingerprintSucceeded();
            }
            else {
                LogFile.i(context, lockedPkgName + " access failed");
                finish();
            }
        }

        @Override
        public void onReady() {}
        @Override
        public void onStarted() {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.lock_fake_activity);
        context = this;

        lockedApp = getIntent().getParcelableExtra(Util.ORIG_INTENT);
        lockedPkgName = getIntent().getStringExtra(Util.LOCK);
        am = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(lockedPkgName);
        am.killBackgroundProcesses(Util.MY_PACKAGE_NAME);

        Long timestamp = System.currentTimeMillis();
        Long permitTimestamp = FLApplication.getPermitTimeHook(lockedPkgName);
        if(permitTimestamp != 0 && timestamp - permitTimestamp <= Util.MAX_TRANSITION_TIME_MS) {
            fingerprintSucceeded();
        }
        else {
            label = (TextView) findViewById(R.id.label_locked);
            icon = (ImageView) findViewById(R.id.icon_locked);
            askFingerprint();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.lock_fake_activity);
            label = (TextView) findViewById(R.id.label_locked);
            icon = (ImageView) findViewById(R.id.icon_locked);
        }
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.lock_fake_activity_land );
            label = (TextView) findViewById(R.id.label_locked);
            icon = (ImageView) findViewById(R.id.icon_locked);
        }
        if(ic != null && lb != null) {
            icon.setImageDrawable(ic);
            label.setText(lb);
        }
    }

    private void askFingerprint() {
        mSpass = new Spass();
        mSpassFingerprint = new SpassFingerprint(context);

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

        checkFingerPrint();
    }

    private void checkFingerPrint() {

        try {
            mSpass.initialize(context);
        } catch (SsdkUnsupportedException e) {
            Log.e("SPASS", "Exception: " + e);
        } catch (UnsupportedOperationException e) {
            Log.e("SPASS", "Fingerprint Service is not supported in the device");
        }

        boolean isFeatureEnabled = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT);
        try{
            if(!isFeatureEnabled){
                Log.e("SPASS", "Fingerprint Service is not supported in the device");
            } else {
                if(!mSpassFingerprint.hasRegisteredFinger()){
                    new MaterialDialog.Builder(context)
                            .content(R.string.no_finger)
                            .positiveText(R.string.register)  // the default is 'Accept'
                            .negativeText(R.string.close)
                            .callback(new MaterialDialog.Callback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                                }

                                @Override
                                public void onNegative(MaterialDialog dialog) {
                                    dialog.dismiss();
                                    finish();
                                }
                            })
                            .build()
                            .show();
                } else {
                    if (!onReadyIdentify) {
                        onReadyIdentify = true;
                        try {
                            mSpassFingerprint.setDialogBgTransparency(0);
                        }
                        catch (IllegalStateException e) {
                            Log.e("SPASS", "Transparency not supported");
                        }
                        mSpassFingerprint.startIdentifyWithDialog(context, listener, FLApplication.useBackupPassword());
                    }
                }
            }
        } catch (UnsupportedOperationException e){
            Log.e("SPASS", "Fingerprint Service is not supported in the device");
        }
    }

    private void fingerprintSucceeded () {
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
            finish();
        }
    }

}