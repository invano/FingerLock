package com.invano.fingerlock.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.invano.fingerlock.FLApplication;
import com.invano.fingerlock.R;
import com.invano.fingerlock.util.Util;

public class MasterSwitchProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        for (int widgetId : appWidgetIds) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                    R.layout.master_switch_widget_layout);

            int imageId = FLApplication.isMasterSwitch() ? R.drawable.widget_switch_on : R.drawable.widget_switch_off;
            remoteViews.setImageViewResource(R.id.widget_imageview, imageId);

            Intent intent = new Intent(context, MasterSwitchActivity.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

            remoteViews.setOnClickPendingIntent(R.id.widget_imageview, pendingIntent);
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        super.onReceive(context, intent);
        if(Util.MASTER_SWITCH_UPDATE.equals(intent.getAction())){
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisAppWidget = new ComponentName(context, MasterSwitchProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
            onUpdate(context, appWidgetManager, appWidgetIds);

            String msg = FLApplication.isMasterSwitch() ?
                context.getString(R.string.title_master_switch_enabled) :
                context.getString(R.string.title_master_switch_disabled);
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }
    }
}
