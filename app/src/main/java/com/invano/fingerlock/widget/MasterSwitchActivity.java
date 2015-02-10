package com.invano.fingerlock.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;
import com.invano.fingerlock.FLApplication;
import com.invano.fingerlock.R;
import com.invano.fingerlock.util.LogFile;
import com.invano.fingerlock.util.Util;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;

public class MasterSwitchActivity extends Activity {

    private SpassFingerprint mSpassFingerprint;
    private Spass mSpass = new Spass();
    private boolean onReadyIdentify = false;

    private SpassFingerprint.IdentifyListener listener = new SpassFingerprint.IdentifyListener() {
        @Override
        public void onFinished(int eventStatus) {
            onReadyIdentify = false;
            if (eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS
                    || eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS) {
                Log.e("SPASS", "onFinished() : STATUS_AUTHENTIFICATION_SUCCESS" );
                FLApplication.setMasterSwitch(!FLApplication.isMasterSwitch());
                Intent i = new Intent(Util.MASTER_SWITCH_UPDATE);
                sendBroadcast(i);
            }
            else {
                LogFile.i(MasterSwitchActivity.this, "Attempt to disable FingerLock failed");
            }
            MasterSwitchActivity.this.finish();
        }

        @Override
        public void onReady() {}
        @Override
        public void onStarted() {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpassFingerprint = new SpassFingerprint(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(!FLApplication.isMasterSwitch()) {
            listener.onFinished(SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS);
            return;
        }

        try {
            mSpass.initialize(this);
        } catch (SsdkUnsupportedException | UnsupportedOperationException e) {
            Log.e("SPASS", "Exceptionn: " + e);
            System.exit(0);
        }

        try{
            boolean isFeatureEnabled = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT);
            if(!isFeatureEnabled){
                Log.e("SPASS", "Fingerprint Service is not supported in the device");
            } else {
                if(!mSpassFingerprint.hasRegisteredFinger()){
                    new MaterialDialog.Builder(this)
                            .content(R.string.no_finger)
                            .positiveText(R.string.register)  // the default is 'Accept'
                            .negativeText(R.string.close)
                            .callback(new MaterialDialog.ButtonCallback() {
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
                    if(!onReadyIdentify) {
                        onReadyIdentify = true;
                        mSpassFingerprint.startIdentifyWithDialog(this, listener, FLApplication.useBackupPassword());
                    }
                }
            }
        } catch (UnsupportedOperationException e){
            Log.e("SPASS", "Fingerprint Service is not supported in the device");
        }
    }
}
