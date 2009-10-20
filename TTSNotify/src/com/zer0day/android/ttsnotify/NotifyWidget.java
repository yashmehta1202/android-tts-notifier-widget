/*
 * Copyright (C) 2009 Zer0day.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zer0day.android.ttsnotify;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

public class NotifyWidget extends AppWidgetProvider {
	private static final int NOTIF_ID = 500;
	private boolean update;
	
	@Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
		
		final boolean update = this.update;
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean op = prefs.getBoolean(IntentReceiver.ENABLED, false);
		
		prefs.edit().putBoolean(IntentReceiver.ENABLED, (update ? !op : op)).commit();
		
		final RemoteViews remote = new RemoteViews(context.getPackageName(), R.layout.widget);
		
		int resource = (update ? !op : op) ? R.drawable.on : R.drawable.off;
		
		remote.setImageViewResource(R.id.img, resource);
		remote.setOnClickPendingIntent(R.id.img, 
        		PendingIntent.getBroadcast(
        				context, 0, 
        				new Intent(context, NotifyWidget.class)
        					.setAction("UPDATE"), 
        				0)
        );
		
		ComponentName cn = new ComponentName(context, NotifyWidget.class);
		appWidgetManager.updateAppWidget(cn, remote);
		
		
		if ((update ? !op : op)) {
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			Notification n = new Notification();
			RemoteViews statusBarView = new RemoteViews(context.getPackageName(), 
					R.layout.statusbar);
			n.contentView = statusBarView;
			n.icon = R.drawable.icon;
			n.contentIntent = PendingIntent.getBroadcast(context, 0, 
	        				new Intent(context, NotifyWidget.class)
	        					.setAction("UPDATE"), 0);
			n.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
			nm.notify(NOTIF_ID, n);
		}
		
		else {
			NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.cancel(NOTIF_ID);
		}
		
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		//funny things happen unless we intercept all broadcasts and 
		//call onUpdate ourselves
		String action = intent.getAction();
		
		if (action != null) {
			if (action.equals("UPDATE")) {
				update = true;
				final AppWidgetManager manager = AppWidgetManager.getInstance(context);
				onUpdate(context, manager, 
						manager.getAppWidgetIds(new ComponentName(
								context, NotifyWidget.class)));
			}
			
			else if (action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)) {
				super.onReceive(context, intent);
			}
			
			else {
				super.onReceive(context, intent);
			}
		}
		
		else {
			super.onReceive(context, intent);
		}
	}
}
