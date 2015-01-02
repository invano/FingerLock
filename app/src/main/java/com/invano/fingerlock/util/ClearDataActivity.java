package com.invano.fingerlock.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.invano.fingerlock.FLApplication;
import com.invano.fingerlock.R;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;
import com.stericson.RootTools.RootTools;

public class ClearDataActivity extends Activity {

    private static final String CLEAR_DATA = "pm clear " + Util.MY_PACKAGE_NAME;

    private SpassFingerprint mSpassFingerprint;
    private Spass mSpass = new Spass();

    private SpassFingerprint.IdentifyListener listener = new SpassFingerprint.IdentifyListener() {
        @Override
        public void onFinished(int eventStatus) {
            if (eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS
                    || eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS) {
                MaterialDialog md = new MaterialDialog.Builder(ClearDataActivity.this)
                        .title(R.string.clear_data_question)
                        .content(R.string.clear_data_content)
                        .positiveText(R.string.ok)
                        .negativeText(R.string.close)
                        .callback(new MaterialDialog.Callback() {
                            @Override
                            public void onNegative(MaterialDialog materialDialog) {
                                finish();
                            }

                            @Override
                            public void onPositive(MaterialDialog materialDialog) {
                                if (!RootTools.isAccessGiven()) {
                                    Toast.makeText(ClearDataActivity.this, "Data not cleared", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                                if (Util.execute(CLEAR_DATA)) {
                                    Toast.makeText(ClearDataActivity.this, "Data cleared", Toast.LENGTH_SHORT).show();
                                }
                                else {
                                    Toast.makeText(ClearDataActivity.this, "Error while clearing data", Toast.LENGTH_SHORT).show();
                                }
                                finish();
                            }
                        })
                        .build();
                md.show();
            }
            else {
                LogFile.i(ClearDataActivity.this, "Attempt to clear application data failed");
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

        mSpassFingerprint = new SpassFingerprint(this);

    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            mSpass.initialize(this);
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
                    new MaterialDialog.Builder(this)
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
                    mSpassFingerprint.startIdentifyWithDialog(this, listener, FLApplication.useBackupPassword());
                }
            }
        } catch (UnsupportedOperationException e){
            Log.e("SPASS", "Fingerprint Service is not supported in the device");
        }
    }

}
