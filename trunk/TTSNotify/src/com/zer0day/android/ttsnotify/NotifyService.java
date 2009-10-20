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

import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_SYSTEM;

import java.util.HashMap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
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

	private static final String TAG = "NotifyService";
	private Intent mIntent;
	
	private TextToSpeech mTts;
	private boolean mReady;
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
		
		mReady = true;
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

		while (! mReady) {
			
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
					
					if (am.getStreamVolume(STREAM_RING) == 0) {
						Log.i(TAG, "Volume is 0, so returning");
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
						ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, 
								String.valueOf(STREAM_SYSTEM));
						mTts.speak("Phone call from " + name, TextToSpeech.QUEUE_FLUSH, ops);
					}					
				}
					
			}
		
		}
		
		else if (action.equals(IntentReceiver.ACTION_SMS_RECEIVED)) {
			final AudioManager am = mAudioManager;
			if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
				Log.i(TAG, "Volume is 0, so returning");
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
				ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, 
						String.valueOf(STREAM_SYSTEM));
				
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
			return "unknown number";
		}
		
		String normalFormat = null;
		
		if (number.startsWith("+")) {
			//convert +<international-code><number> to readable format
			//<number> is normally 10 digits
			normalFormat = "0" + number.substring(number.length() - 10);
		}
		
		String selection = Contacts.Phones.NUMBER + " = ?";
		
		if (normalFormat != null) selection += " OR " + Contacts.Phones.NUMBER + " = ?";
		
		final String[] args = new String[(normalFormat == null ? 1 : 2)];
		args[0] = number;
		
		if (normalFormat != null) args[1] = normalFormat;
		
		Cursor c = getContentResolver().query(Contacts.Phones.CONTENT_URI, 
				new String[] {Contacts.Phones.DISPLAY_NAME}, 
				selection, 
				args, null);
		
		String name = null;
		
		if (c.moveToFirst()) {
			name = c.getString(c.getColumnIndex(Contacts.Phones.DISPLAY_NAME));
		}
		
		else {
			name = "unknown number";
		}
		
		c.close();
		
		return name;
	}

	static void start(Context ctx, Intent intent) {
		ctx.startService(intent);
	}
}
