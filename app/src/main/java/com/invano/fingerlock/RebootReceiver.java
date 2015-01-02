package com.invano.fingerlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.invano.fingerlock.util.Util;

public class RebootReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {

        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        String action = intent.getAction();
        switch (action) {
            case Util.BROADCAST_REBOOT:
                Util.execute("reboot");
                break;
            case Util.BROADCAST_SOFT_REBOOT:
                Util.execute("setprop ctl.restart surfaceflinger");
                Util.execute("setprop ctl.restart zygote");
                break;
            default:
                break;
        }
    }

    private void softReboot() {

    }
}
