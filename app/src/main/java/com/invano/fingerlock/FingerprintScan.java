package com.invano.fingerlock;

import android.content.Context;
import android.util.Log;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;
import com.samsung.android.sdk.pass.SpassInvalidStateException;

public class FingerprintScan {

    private SpassFingerprint mSpassFingerprint;

    private boolean statusIdentifying = false;

    private FingerprintScanListener innerListener;
    private Context appContext;

    private SpassFingerprint.IdentifyListener listener = new SpassFingerprint.IdentifyListener() {

        @Override
        public void onFinished(int eventStatus) {
            statusIdentifying = false;
            if (eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS
                    || eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS) {
                innerListener.onIdentifySucceded();
            }
            else {
                innerListener.onIdentifyFailed(eventStatus);
            }
        }

        @Override
        public void onReady() {
            innerListener.onIdentifyReady();
        }
        @Override
        public void onStarted() {
            innerListener.onIdentifyStarted();
        }
    };

    public FingerprintScan(Context context, FingerprintScanListener fingerprintScanListener) {
        this.appContext = context;
        this.innerListener = fingerprintScanListener;
    }

    public void initialize() {

        mSpassFingerprint = new SpassFingerprint(appContext);
        Spass mSpass = new Spass();

        try {
            mSpass.initialize(appContext);
        } catch (SsdkUnsupportedException e1) {
            int type = e1.getType();
            if (type == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED
                || type == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED) {
                innerListener.onInitializationFailed(R.string.spass_not_supported);
            } else if (type == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
                innerListener.onInitializationFailed(R.string.spass_missing_lib);
            } else if (type == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED
                    || type == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
                innerListener.onInitializationFailed(R.string.spass_update);
            }
            return;
        }

        try {
            boolean isFeatureEnabled = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT);

            if (!isFeatureEnabled) {
                Log.e("SPASS", "Fingerprint Service is not supported in the device");
            } else {
                if (!mSpassFingerprint.hasRegisteredFinger()) {
                    innerListener.onUnregisteredFingerprints();
                } else {
                    startIdentify();
                }
            }
        } catch (UnsupportedOperationException e) {
            Log.e("SPASS", "Fingerprint Service is not supported in the device");
        } catch (SpassInvalidStateException e){
            if(e.getType() == SpassInvalidStateException.STATUS_OPERATION_DENIED){
                Log.e("SPASS", e.getMessage());
            }
        } catch (IllegalStateException e) {
            Log.e("SPASS", e.getMessage());
        }
    }

    public void startIdentify() {
        try {
            mSpassFingerprint.startIdentify(listener);
            statusIdentifying = true;
        } catch (IllegalStateException e) {
            Log.e("SPASS", e.getMessage());
            innerListener.onIdentifyErrorAttempts();
        }
    }

    public void cancelIdentify() {
        if (statusIdentifying) {
            try {
                mSpassFingerprint.cancelIdentify();
                statusIdentifying = false;
                appContext = null;
            } catch (IllegalStateException e) {
                Log.e("SPASS", e.getMessage());
            }
        }
    }

    public boolean isIdentifying() {
        return statusIdentifying;
    }

    public static String getEventStatusName(int eventStatus) {
        switch (eventStatus) {
            case SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS:
                return "STATUS_AUTHENTIFICATION_SUCCESS";
            case SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS:
                return "STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS";
            case SpassFingerprint.STATUS_TIMEOUT_FAILED:
                return "STATUS_TIMEOUT";
            case SpassFingerprint.STATUS_SENSOR_FAILED:
                return "STATUS_SENSOR_ERROR";
            case SpassFingerprint.STATUS_USER_CANCELLED:
                return "STATUS_USER_CANCELLED";
            case SpassFingerprint.STATUS_QUALITY_FAILED:
                return "STATUS_QUALITY_FAILED";
            case SpassFingerprint.STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE:
                return "STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE";
            case SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED:
            default:
                return "STATUS_AUTHENTIFICATION_FAILED";
        }
    }

    public static abstract interface FingerprintScanListener {
        public abstract void onIdentifySucceded();
        public abstract void onIdentifyStarted();
        public abstract void onIdentifyReady();
        public abstract void onIdentifyFailed(int status);
        public abstract void onIdentifyErrorAttempts();
        public abstract void onInitializationFailed(int msg);
        public abstract void onUnregisteredFingerprints();
    }
}
