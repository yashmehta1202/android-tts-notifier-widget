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

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.widget.TextView;

public class HowTo extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);
		
		TextView t = (TextView) findViewById(R.id.tv);
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("<h2>Using PhoneVoice</h2>")
		.append("\u00BB <b>Ensure you have Text to Speech installed on your phone</b>.<br />")
		.append("\u00BB Add the widget to your home screen (see below)<br />")
		.append("\u00BB Tap the widget to turn it on, ")
		.append("a notification will appear in your notification bar.<br />")
		.append("\u00BB When a call or message comes in, if the volume is on, ")
		.append("it will read out who is calling or who sent the message.<br /><br />");
		
		sb.append("\u00BB To disable PhoneVoice, either tap the widget or notification icon.");
		
		
		sb.append("<h2>Adding the PhoneVoice widget</h2>")
		.append("\u00BB <b>Long press</b> an empty space on your home screen.<br />")
		.append("\u00BB Choose <b>Add</b>.<br />")
		.append("\u00BB Choose <b>Widget</b>.<br />")
		.append("\u00BB Choose <b>PhoneVoice</b>.<br /><br />");
		
		Spanned ss = Html.fromHtml(sb.toString());
		t.setText(ss);
	}
}
