/*
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

package com.mb.toggle2g;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class Toggle2GBoot extends BroadcastReceiver
{
	public void onReceive(Context context, Intent data)
	{
	    boolean service = false;
	    SharedPreferences phonePreferences = Toggle2G.getPhonePreferences(context, true);
        if ( phonePreferences != null )
	    {
            Log.i(Toggle2G.TOGGLE2G, "using shared preferences");
	        service = phonePreferences.getBoolean("enableService", false);
	    }
        else
        {
            service = Toggle2G.getPreferences(context.getApplicationContext()).getBoolean("enableService", false);
        }
		
		Log.i(Toggle2G.TOGGLE2G, "boot service=" + service);
		if ( service )
		{
		    if ( phonePreferences == null )
		    {
		        Toggle2G.preparePreferences(context);
		    }
			Toggle2GService.checkLockService(context, true);
		}
	}
}
