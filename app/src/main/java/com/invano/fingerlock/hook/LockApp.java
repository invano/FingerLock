package com.invano.fingerlock.hook;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.content.res.TypedArray;
import android.view.Window;
import android.view.WindowManager;

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
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class LockApp implements IXposedHookZygoteInit, IXposedHookLoadPackage {


    private static XSharedPreferences pref = null;

    private static Timer mActivityTransitionTimer;
    private static TimerTask mActivityTransitionTimerTask;
    private static boolean wasInBackground;

    private static boolean secureZone;
    private static int transitionTime;
    private static boolean hideNotifications;
    private static boolean masterSwitch;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        pref = new XSharedPreferences(Util.MY_PACKAGE_NAME, Util.MY_PACKAGE_NAME);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        reloadSettings();

        final String pkg = lpparam.packageName;
        if (!pref.getBoolean(pkg, false))
            return;

        final Class<?> activity = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        final Class<?> nMS = XposedHelpers.findClass("android.app.NotificationManager", lpparam.classLoader);

        findAndHookMethod(nMS, "notify", String.class, int.class, Notification.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!masterSwitch || secureZone || !hideNotifications)
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
                if (!masterSwitch || secureZone)
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
                startActivityTransitionTimer();
            }
        });

        hookAllMethods(activity, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!masterSwitch || secureZone)
                    return;
                final Activity app = (Activity) param.thisObject;
                if (app.getClass().getName().equals("android.app.Activity")) {
                    return;
                }
                if (wasInBackground) {
                    startFingerLockActivity(app, pkg);
                    app.moveTaskToBack(true);
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
                stopActivityTransitionTimer();
            }
        });

        Long timestamp = System.currentTimeMillis();
        Long permitTimestamp = pref.getLong(pkg + "_sec", 0);
        if (permitTimestamp != 0 && timestamp - permitTimestamp <= Util.MAX_TRANSITION_TIME_MS) {
            return;
        }
        hookAllMethods(activity, "onCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!masterSwitch || secureZone)
                    return;
                final Activity app = (Activity) param.thisObject;
                if (app.getClass().getName().equals("android.app.Activity")) {
                    return;
                }
                startFingerLockActivity(app, app.getPackageName());
                app.moveTaskToBack(true);
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });


    }

    private void startFingerLockActivity(final Activity app, String packageName) {
        TypedArray array = app.getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground,
        });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        array.recycle();

        Intent it = new Intent();
        it.setComponent(new ComponentName(Util.MY_PACKAGE_NAME, Util.MY_PACKAGE_NAME + ".LockFakeActivity"));
        it.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        it.putExtra(Util.ORIG_INTENT, app.getIntent());
        it.putExtra(Util.LOCK, packageName);
        it.putExtra(Util.BACKGROUND_COLOR, backgroundColor);

        app.startActivity(it);
        app.overridePendingTransition(0,0);
    }

    public void startActivityTransitionTimer() {
        mActivityTransitionTimer = new Timer();
        mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                wasInBackground = true;
            }
        };

        reloadSettings();
        mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, transitionTime);
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

    private void reloadSettings() {
        pref.reload();
        secureZone = pref.getBoolean(Util.SECURE_ZONE_SWITCH, false);
        transitionTime = pref.getInt(Util.TRANSITION_TIME, Util.MAX_TRANSITION_TIME_MS);
        hideNotifications = pref.getBoolean(Util.MASK_NOTIFICATIONS, false);
        masterSwitch = pref.getBoolean(Util.MASTER_SWITCH, true);
    }
}