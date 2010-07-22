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

import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_SYSTEM;

import java.util.HashMap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Contacts;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

public class NotifyService extends Service 
	implements OnInitListener, OnUtteranceCompletedListener, Runnable {

	private static final String TAG = "PhoneVoiceService";
	private static final String[] PROJECTION = 
		new String[] { Contacts.Phones.DISPLAY_NAME };
	private static final String STREAM_SYSTEM_STR = 
		String.valueOf(AudioManager.STREAM_SYSTEM);
	private static final String UNKNOWN_NUMBER = "unknown number";
	
	private Intent mIntent;
	
	private TextToSpeech mTts;
	private boolean mIsReady;
	private int mSysVol;

	private AudioManager mAudioManager;

	private static final Object sLock = new Object();

	@Override
	public IBinder onBind(Intent intent) {
		
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		if (mTts == null) {
			mTts = new TextToSpeech(this, this);
		}
		
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		
		super.onStart(intent, startId);
		
		mIntent = intent;
		
		new Thread(this).start();
	}

	@Override
	public void onInit(int status) {
		
		mIsReady = true;
		mTts.setOnUtteranceCompletedListener(this);
		
		synchronized (sLock) {
			sLock.notify();
		}
	}
	
	@Override
	public void onUtteranceCompleted(String utteranceId) {
		
		synchronized (sLock) {
			
			if (utteranceId.equals("call")) {
				mAudioManager.setStreamMute(STREAM_RING, false);
				mAudioManager.setStreamVolume(STREAM_SYSTEM, 
						mSysVol, 0);
			}
			
			else if (utteranceId.equals("sms")) {
				mAudioManager.setStreamMute(STREAM_NOTIFICATION, false);
				mAudioManager.setStreamVolume(STREAM_SYSTEM, 
						mSysVol, 0);
			}
		}
	}

	@Override
	public void run() {

		while (! mIsReady) {
			
			synchronized (sLock) {
				try {
					sLock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		final String action = mIntent.getAction();

		if (action == null ) { 
			return;
		}
		
		if (action.equals(IntentReceiver.ACTION_PHONE_STATE)) {
			TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			
			switch (tm.getCallState()) {
				case TelephonyManager.CALL_STATE_RINGING: {
		
					final AudioManager am = mAudioManager;
					final int mode = am.getRingerMode();
					if (mode == AudioManager.RINGER_MODE_SILENT ||
							mode == AudioManager.RINGER_MODE_VIBRATE) {
						Log.i(TAG, "Not speaking - volume is 0");
						return;
					}
					
					final String name = findContactFromNumber(
							mIntent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
					
					synchronized (sLock) {
						am.setStreamMute(STREAM_RING, true);
						mSysVol = am.getStreamVolume(STREAM_SYSTEM);
						am.setStreamVolume(STREAM_SYSTEM, 
								am.getStreamMaxVolume(STREAM_SYSTEM), 0);
						
						HashMap<String, String> ops = new HashMap<String, String>();
						
						ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "call");
						ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
						mTts.speak("Phone call from " + name, TextToSpeech.QUEUE_FLUSH, ops);
					}					
					
					break;
				}
					
			}
		
		}
		
		else if (action.equals(IntentReceiver.ACTION_SMS_RECEIVED)) {
			final AudioManager am = mAudioManager;
			if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
				Log.i(TAG, "Not speaking - volume is 0");
				return;
			}
			
			Object[] pdusObj = (Object[]) mIntent.getExtras().get("pdus");
			SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdusObj[0]);
			final String from = msg.getOriginatingAddress();
			
			synchronized (sLock) {
				am.setStreamMute(STREAM_NOTIFICATION, true);
				HashMap<String, String> ops = new HashMap<String, String>();
				
				mSysVol = am.getStreamVolume(STREAM_SYSTEM);
				am.setStreamVolume(STREAM_SYSTEM, 
						am.getStreamMaxVolume(STREAM_SYSTEM), 0);
				
				ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sms");
				ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
				
				mTts.speak("text message from " + findContactFromNumber(from), 
						TextToSpeech.QUEUE_FLUSH, ops);
			}
				
		}	
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (mTts != null) {
			mTts.shutdown();
			mTts = null;
		}
	}
	
	private String findContactFromNumber(String number) {
		
		if (number == null) {
			return UNKNOWN_NUMBER;
		}
		
		String name = filteredQuery(number);
		
		if (name == null && number.charAt(0) == '+') {
			//convert +<international-code><number> to readable format
			//<number> is normally 10 digits
			number = "0" + number.substring(number.length() - 10);
			
			name = filteredQuery(number);
		}
		
		return name == null ? UNKNOWN_NUMBER : name;
	}

	private String filteredQuery(String number) {
		
		String result = null;
		
		Uri filtered = Uri.withAppendedPath(
				Contacts.Phones.CONTENT_FILTER_URL, number);
		
		Cursor nameCursor = getContentResolver().query(filtered, 
				PROJECTION, null, null, null);
		
		if (nameCursor.moveToFirst()) {
			result = nameCursor.getString(
					nameCursor.getColumnIndex(Contacts.Phones.DISPLAY_NAME));
		}
		
		nameCursor.close();
		
		return result;
	}
	
	static void start(Context ctx, Intent intent) {
		ctx.startService(intent);
	}
}
