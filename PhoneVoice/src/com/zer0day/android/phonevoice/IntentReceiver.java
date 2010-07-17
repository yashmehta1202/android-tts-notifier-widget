/*
 * Copyright (C) 2010 Zer0day.com
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

package com.zer0day.android.phonevoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class IntentReceiver extends BroadcastReceiver {

	static final String ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE";
	static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
	static final String ACTION_BATTERY_LOW = "android.intent.action.ACTION_BATTERY_LOW";
	 
	static final String ENABLED = "enabled";
	 
	@Override
	public void onReceive(Context context, Intent intent) {

		if (! PreferenceManager.getDefaultSharedPreferences(context)
				.getBoolean(ENABLED, false)) {
			return;
		}
		
		Intent serv = new Intent(context, NotifyService.class);
		serv.setAction(intent.getAction());
		serv.putExtras(intent);
		NotifyService.start(context, serv);
		
	}
}
