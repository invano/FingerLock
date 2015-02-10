package com.invano.fingerlock.hook;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.content.res.TypedArray;
import android.view.Window;
import android.view.WindowManager;

import com.invano.fingerlock.LockService;
import com.invano.fingerlock.util.Util;

import java.util.Timer;
import java.util.TimerTask;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class LockApp implements IXposedHookZygoteInit, IXposedHookLoadPackage {


    private static XSharedPreferences pref = null;

    private static Timer mActivityTransitionTimer;
    private static TimerTask mActivityTransitionTimerTask;
    private static boolean wasInBackground;

    private static boolean mSecureZone;
    private static int mTransitionTime;
    private static boolean mHideNotifications;
    private static boolean mMasterSwitch;

    private boolean mFirstLaunch = true;
    private String mPackageName;


    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        pref = new XSharedPreferences(Util.MY_PACKAGE_NAME, Util.MY_PACKAGE_NAME);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        reloadSettings();

        mPackageName = lpparam.packageName;

        if (!pref.getBoolean(mPackageName, false))
            return;

        final Class<?> activity = findClass("android.app.Activity", lpparam.classLoader);
        final Class<?> nMS = findClass("android.app.NotificationManager", lpparam.classLoader);

        findAndHookMethod(nMS, "notify", String.class, int.class, Notification.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!mSecureZone || mMasterSwitch || !mHideNotifications)
                    return;
                Context context = (Context) getObjectField(param.thisObject, "mContext");
                Notification nOld = (Notification) param.args[2];
                Notification nNew = new Notification.Builder(context)
                                        .setContentTitle(lpparam.appInfo.loadLabel(context.getPackageManager()))
                                        .setContentText("New notification")
                                        .setSmallIcon(nOld.icon)
                                        .setContentIntent(nOld.contentIntent)
                                        .build();
                nNew.flags = nOld.flags;
                param.args[2] = nNew;
            }
        });

        findAndHookMethod(activity, "getWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!mMasterSwitch || mSecureZone)
                    return;
                Window window = (Window) param.getResult();
                if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != WindowManager.LayoutParams.FLAG_SECURE) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
        });

        hookAllMethods(activity, "onPause", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Activity app = (Activity) param.thisObject;
                if (app.getClass().getName().equals("android.app.Activity")) {
                    return;
                }
                startActivityTransitionTimer(app);
            }
        });

        hookAllMethods(activity, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!mMasterSwitch || mSecureZone)
                    return;
                final Activity app = (Activity) param.thisObject;
                if (app.getClass().getName().equals("android.app.Activity")) {
                    return;
                }

                if (wasInBackground && mFirstLaunch) {

                    if (!LockService.isRunning(app)) {
                        startFingerLockService(app);
                    }
                    else {
                        broadcastUnlockStarted(app);
                    }
                    mFirstLaunch = false;
                }

                stopActivityTransitionTimer();
            }
        });

        hookAllMethods(activity, "onCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!mMasterSwitch || mSecureZone)
                    return;
                final Activity app = (Activity) param.thisObject;
                if (app.getClass().getName().equals("android.app.Activity")) {
                    return;
                }

                if (mFirstLaunch) {

                    if (!LockService.isRunning(app)) {
                        startFingerLockService(app);
                    }
                    else {
                        broadcastUnlockStarted(app);
                    }
                    mFirstLaunch = false;
                }
            }
        });

        Class system = findClass("java.lang.System", lpparam.classLoader);

        findAndHookMethod(system, "exit", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
                Context context = (Context) callMethod(activityThread, "getSystemContext");

                broadcastUnlockFinished(context);
            }
        });

        Class process = findClass("android.os.Process", lpparam.classLoader);

        findAndHookMethod(process, "killProcess", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
                Context context = (Context) callMethod(activityThread, "getSystemContext");

                broadcastUnlockFinished(context);
            }
        });

    }

    private void startFingerLockService(Context c)
    {
        TypedArray typedArray = c.getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground,
        });
        int color = typedArray.getColor(0, 0xFF00FF);
        typedArray.recycle();

        Intent i = new Intent();
        i.setComponent(new ComponentName(Util.MY_PACKAGE_NAME, Util.MY_PACKAGE_NAME + ".LockService"));
        i.putExtra(Util.LOCK, mPackageName);
        i.putExtra(Util.BACKGROUND_COLOR, color);

        c.startService(i);
    }

    public void startActivityTransitionTimer(final Context c) {
        mActivityTransitionTimer = new Timer();
        mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                wasInBackground = true;
                broadcastUnlockFinished(c);
            }
        };

        reloadSettings();
        mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, mTransitionTime);
    }

    public void stopActivityTransitionTimer() {
        if (mActivityTransitionTimerTask != null) {
            mActivityTransitionTimerTask.cancel();
        }

        if (mActivityTransitionTimer != null) {
            mActivityTransitionTimer.cancel();
        }

        wasInBackground = false;
    }

    private void broadcastUnlockFinished(Context c) {

        Intent localIntent = new Intent("com.invano.fingerlock.UNLOCK_FINISHED");
        localIntent.putExtra(Util.LOCK, mPackageName);
        c.sendBroadcast(localIntent);

        mFirstLaunch = true;
    }

    private void broadcastUnlockStarted(Context c) {

        Intent localIntent = new Intent("com.invano.fingerlock.UNLOCK_STARTED");
        localIntent.putExtra(Util.LOCK, mPackageName);
        c.sendBroadcast(localIntent);
    }

    private void reloadSettings() {
        pref.reload();
        mSecureZone = pref.getBoolean(Util.SECURE_ZONE_SWITCH, false);
        mTransitionTime = pref.getInt(Util.TRANSITION_TIME, Util.MAX_TRANSITION_TIME_MS);
        mHideNotifications = pref.getBoolean(Util.MASK_NOTIFICATIONS, false);
        mMasterSwitch = pref.getBoolean(Util.MASTER_SWITCH, true);
    }
}