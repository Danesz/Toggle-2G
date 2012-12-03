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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue.IdleHandler;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SetPhoneSettingsV2
{
    /* NETWORK_MODE_* See ril.h RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE 
    60     int NETWORK_MODE_WCDMA_PREF     = 0; // GSM/WCDMA (WCDMA preferred) 
    61     int NETWORK_MODE_GSM_ONLY       = 1; // GSM only 
    62     int NETWORK_MODE_WCDMA_ONLY     = 2; // WCDMA only 
    63     int NETWORK_MODE_GSM_UMTS       = 3; // GSM/WCDMA (auto mode, according to PRL)
    64                                             AVAILABLE Application Settings menu
    65     int NETWORK_MODE_CDMA           = 4; // CDMA and EvDo (auto mode, according to PRL)
    66                                             AVAILABLE Application Settings menu
    67     int NETWORK_MODE_CDMA_NO_EVDO   = 5; // CDMA only 
    68     int NETWORK_MODE_EVDO_NO_CDMA   = 6; // EvDo only 
    69     int NETWORK_MODE_GLOBAL         = 7; // GSM/WCDMA, CDMA, and EvDo (auto mode, according to PRL)
    70                                             AVAILABLE Application Settings menu
    71     int NETWORK_MODE_LTE_CDMA_EVDO  = 8; // LTE, CDMA and EvDo 
    72     int NETWORK_MODE_LTE_GSM_WCDMA  = 9; // LTE, GSM/WCDMA 
    73     int NETWORK_MODE_LTE_CMDA_EVDO_GSM_WCDMA = 10; // LTE, CDMA, EvDo, GSM/WCDMA 
    74     int NETWORK_MODE_LTE_ONLY       = 11; // LTE Only mode.
    */ 
    
    private static final int MESSAGE_GET_PREFERRED_NETWORK_TYPE = 0;
    private static final int MESSAGE_SET_2G = 1;
    private static final int MESSAGE_SET_3G = 2;
    private static final int MESSAGE_SET_AFTER_GET_2G = 3;
    private static final int MESSAGE_SET_AFTER_GET_3G = 4;

    private static final int MESSAGE_SET_CUSTOM = 5;
    private static final int MESSAGE_SET_AFTER_GET_CUSTOM = 6;

    private static final int MESSAGE_RESTORE_DATA_OFF_MONITORING = 7;

    SetHandler setHandler = new SetHandler();
    TelephonyManager telephonyManager;

    Object mPhone;
    Method setPreferredNetworkType;
    Method getPreferredNetworkType;

    int currentNetwork = -1;
    int customNetwork = -1;

    enum setG {
        set2g, set3g, custom
    }

    setG settingG = null;
    String reason2g = null;
    String reason3g = null;
    String reasonCustom = null;
    boolean mTurnDataOff = false;
    Boolean mCurrentDataSetting = null;

    Context context;

    public SetPhoneSettingsV2(Context context)
    {
        this.context = context;
        Toggle2G.loadNetworkSettings(context);

        mPhone = loadPhoneObject();
        try
        {

            setPreferredNetworkType = mPhone.getClass().getMethod("setPreferredNetworkType", new Class[] { int.class, Message.class });
            getPreferredNetworkType = mPhone.getClass().getMethod("getPreferredNetworkType", new Class[] { Message.class });

            getNetwork();
        }
        catch (Exception e)
        {
            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
        }

        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    static Object loadPhoneObject()
    {
        try
        {
            Class<?> forName = Class.forName("com.android.internal.telephony.PhoneFactory");
            Method getDefaultPhone = forName.getMethod("getDefaultPhone", new Class[] {});
            return getDefaultPhone.invoke(null, new Object[] {});
        }
        catch (Exception e)
        {
            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
        }
        return null;
    }
    
    static Integer getDefaultNetwork()
    {
        try
        {
            Object phone = loadPhoneObject();
            Field field = phone.getClass().getField("PREFERRED_NT_MODE");
            field.setAccessible(true);
            return field.getInt(phone);
        }
        catch (Exception e)
        {
            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
        }
        return null;
    }

    void getNetwork()
    {
        try
        {
            getPreferredNetworkType.invoke(mPhone, new Object[] { setHandler.obtainMessage(MESSAGE_GET_PREFERRED_NETWORK_TYPE) });
        }
        catch (Exception e)
        {
            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
        }
    }

    public void set2g(String reason, boolean dataOff)
    {
        if (currentNetwork != Toggle2G.network2GSelect && settingG != setG.set2g)
        {
            mTurnDataOff = dataOff;
            reason2g = reason;
            settingG = setG.set2g;
            Looper.myQueue().addIdleHandler(setHandler);
        }
    }

    public void set3g(String reason, boolean dataOff)
    {
        if (currentNetwork != Toggle2G.network3GSelect && settingG != setG.set3g)
        {
            mTurnDataOff = dataOff;
            reason3g = reason;
            settingG = setG.set3g;
            Looper.myQueue().addIdleHandler(setHandler);
        }
    }

    public void set2gNow(String reason, boolean dataOff)
    {
        currentNetwork = -1;
        // reason2g = reason;
        set2g(reason, dataOff);
    }

    public void set2gNow()
    {
        if (currentNetwork != Toggle2G.network2GSelect)
        {
            Log.i(Toggle2G.TOGGLE2G, "set2g because " + reason2g);
            if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
            {
                try
                {
                    getPreferredNetworkType.invoke(mPhone, new Object[] { setHandler.obtainMessage(MESSAGE_SET_AFTER_GET_2G) });
                }
                catch (Exception e)
                {
                    Log.e(Toggle2G.TOGGLE2G, "Error!", e);
                }
            }
            else
            {
                Log.i(Toggle2G.TOGGLE2G, "2g not set, phone in use");
            }
        }
    }

    public void set3gNow(String reason, boolean dataOff)
    {
        currentNetwork = -1;
        set3g(reason, dataOff);
    }

    public void set3gNow()
    {
        // Log.i(Toggle2G.TOGGLE2G, "unlock is2g=" + is2g +
        // ", telephonyManager.getCallState()=" +
        // telephonyManager.getCallState());
        if (currentNetwork != Toggle2G.network3GSelect)
        {
            Log.i(Toggle2G.TOGGLE2G, "set3g because " + reason3g);
            if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
            {
                try
                {
                    getPreferredNetworkType.invoke(mPhone, new Object[] { setHandler.obtainMessage(MESSAGE_SET_AFTER_GET_3G) });
                }
                catch (Exception e)
                {
                    Log.e(Toggle2G.TOGGLE2G, "Error!", e);
                }
            }
            else
            {
                Log.i(Toggle2G.TOGGLE2G, "3g not set, phone in use");
            }
        }
    }

    public void setCustomNow()
    {
        // Log.i(Toggle2G.TOGGLE2G, "unlock is2g=" + is2g +
        // ", telephonyManager.getCallState()=" +
        // telephonyManager.getCallState());
        if (currentNetwork != customNetwork)
        {
            Log.i(Toggle2G.TOGGLE2G, "set custom because " + reasonCustom);
            if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
            {
                try
                {
                    getPreferredNetworkType.invoke(mPhone, new Object[] { setHandler.obtainMessage(MESSAGE_SET_AFTER_GET_CUSTOM) });
                }
                catch (Exception e)
                {
                    Log.e(Toggle2G.TOGGLE2G, "Error!", e);
                }
            }
            else
            {
                Log.i(Toggle2G.TOGGLE2G, "custom not set, phone in use");
            }
        }
    }

    private class SetHandler extends Handler implements IdleHandler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case MESSAGE_GET_PREFERRED_NETWORK_TYPE:
                // Log.i(Toggle2G.TOGGLE2G,
                // "MESSAGE_GET_PREFERRED_NETWORK_TYPE");
                handleGetPreferredNetworkTypeResponse(msg);
                notfyPlugin();

                break;

            case MESSAGE_SET_AFTER_GET_2G:
                // Log.i(Toggle2G.TOGGLE2G, "MESSAGE_AFTER_GET_2G");
                handleGetPreferredNetworkTypeResponse(msg);
                if (currentNetwork != Toggle2G.network2GSelect && settingG == setG.set2g)
                {
                    Log.i(Toggle2G.TOGGLE2G, "switching from " + currentNetwork + " to " + Toggle2G.network2GSelect);
                    if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
                    {
                        try
                        {
                            // Log.i(Toggle2G.TOGGLE2G,
                            // "setPreferredNetworkType=2g");
                            // setPreferredNetworkType.invoke(mPhone, new
                            // Object[] { 1,
                            // setHandler.obtainMessage(MESSAGE_SET_2G) });
                            int delay = 500;
                            if ( mCurrentDataSetting == null && mTurnDataOff )
                            {
                                mCurrentDataSetting = getMobileData(context);
                                if( mCurrentDataSetting && mTurnDataOff )
                                {
                                    delay=5000;
                                    setMobileDataEnabled( context, false );
                                    long timeout = System.currentTimeMillis() + 5000;
                                    while ( getMobileData(context) && System.currentTimeMillis() < timeout)
                                    {
                                        Thread.sleep(100);
                                    }
                                    Log.i(Toggle2G.TOGGLE2G, "Data Setting is now " + getMobileData(context) );
                                }
                            }
                            int timeout = (int) ((SystemClock.uptimeMillis() + delay ) / 1000);
                            Log.e(Toggle2G.TOGGLE2G, "start timeout = " + timeout );

                            setPreferredNetworkType.invoke(mPhone, new Object[] { Toggle2G.network2GSelect, setHandler.obtainMessage(MESSAGE_SET_2G, Toggle2G.network2GSelect, timeout) });
                            this.sendEmptyMessageDelayed(MESSAGE_RESTORE_DATA_OFF_MONITORING, delay);
                            break;
                        }
                        catch (Exception e)
                        {
                            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
                        }
                    }
                    else
                    {
                        Log.i(Toggle2G.TOGGLE2G, "2g not set, phone in use");
                    }
                }

                if (settingG == setG.set2g)
                {
                    settingG = null;
                }
                break;

            case MESSAGE_SET_AFTER_GET_3G:
                // Log.i(Toggle2G.TOGGLE2G, "MESSAGE_AFTER_GET_3G");
                handleGetPreferredNetworkTypeResponse(msg);
                if (currentNetwork == Toggle2G.network2GSelect && settingG == setG.set3g)
                {
                    Log.i(Toggle2G.TOGGLE2G, "switching from " + currentNetwork + " to " + Toggle2G.network3GSelect);
                    if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
                    {
                        try
                        {
                            // Log.i(Toggle2G.TOGGLE2G,
                            // "setPreferredNetworkType=3g");
                            // setPreferredNetworkType.invoke(mPhone, new
                            // Object[] { 0,
                            // setHandler.obtainMessage(MESSAGE_SET_3G) });
                            int delay = 500;
                            if ( mCurrentDataSetting == null && mTurnDataOff )
                            {
                                mCurrentDataSetting = getMobileData(context);
                                if( mCurrentDataSetting && mTurnDataOff )
                                {
                                    delay=5000;
                                    setMobileDataEnabled( context, false );
                                    long timeout = System.currentTimeMillis() + 5000;
                                    while ( getMobileData(context) && System.currentTimeMillis() < timeout)
                                    {
                                        Thread.sleep(100);
                                    }
                                    Log.i(Toggle2G.TOGGLE2G, "Data Setting is now " + getMobileData(context) );
                                }
                            }
                            int timeout = (int) ((SystemClock.uptimeMillis() + delay ) / 1000);
                            Log.e(Toggle2G.TOGGLE2G, "start timeout = " + timeout );

                            setPreferredNetworkType.invoke(mPhone, new Object[] { Toggle2G.network3GSelect, setHandler.obtainMessage(MESSAGE_SET_3G, Toggle2G.network3GSelect, timeout) });
                            this.sendEmptyMessageDelayed(MESSAGE_RESTORE_DATA_OFF_MONITORING, delay);
                            break;
                        }
                        catch (Exception e)
                        {
                            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
                        }

                    }
                    else
                    {
                        Log.i(Toggle2G.TOGGLE2G, "3g not set, phone in use");
                    }

                }
                if (settingG == setG.set3g)
                {
                    settingG = null;
                }
                break;

            case MESSAGE_SET_AFTER_GET_CUSTOM:
                // Log.i(Toggle2G.TOGGLE2G, "MESSAGE_AFTER_GET_CUSTOM");
                handleGetPreferredNetworkTypeResponse(msg);
                if (currentNetwork != customNetwork && settingG == setG.custom)
                {
                    Log.i(Toggle2G.TOGGLE2G, "switching from " + currentNetwork + " to " + customNetwork);
                    if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
                    {
                        try
                        {
                            // Log.i(Toggle2G.TOGGLE2G,
                            // "setPreferredNetworkType=CUSTOM");
                            int delay = 500;
                            if ( mCurrentDataSetting == null && mTurnDataOff )
                            {
                                mCurrentDataSetting = getMobileData(context);
                                if( mCurrentDataSetting && mTurnDataOff )
                                {
                                    delay=5000;
                                    setMobileDataEnabled( context, false );
                                    long timeout = System.currentTimeMillis() + 5000;
                                    while ( getMobileData(context) && System.currentTimeMillis() < timeout)
                                    {
                                        Thread.sleep(100);
                                    }
                                    Log.i(Toggle2G.TOGGLE2G, "Data Setting is now " + getMobileData(context) );
                                }
                            }
                            int timeout = (int) ((SystemClock.uptimeMillis() + delay ) / 1000);
                            Log.e(Toggle2G.TOGGLE2G, "start timeout = " + timeout );
                            
                            setPreferredNetworkType.invoke(mPhone, new Object[] { customNetwork, setHandler.obtainMessage(MESSAGE_SET_CUSTOM, customNetwork, timeout) });
                            this.sendEmptyMessageDelayed(MESSAGE_RESTORE_DATA_OFF_MONITORING, delay);
                            break;
                        }
                        catch (Exception e)
                        {
                            Log.e(Toggle2G.TOGGLE2G, "Error!", e);
                        }

                    }
                    else
                    {
                        Log.i(Toggle2G.TOGGLE2G, "custom not set, phone in use");
                    }

                }
                if (settingG == setG.custom)
                {
                    settingG = null;
                }
                break;

            case MESSAGE_SET_2G:
                // Log.i(Toggle2G.TOGGLE2G, "MESSAGE_SET_2G");
                if ( handleSetPreferredNetworkTypeResponse(msg, true, setG.set2g))
                {
                    if (settingG == setG.set2g)
                    {
                        settingG = null;
                    }
                }
                break;

            case MESSAGE_SET_3G:
                // Log.i(Toggle2G.TOGGLE2G, "MESSAGE_SET_3G");
                if ( handleSetPreferredNetworkTypeResponse(msg, false, setG.set3g) );
                {
                    if (settingG == setG.set3g)
                    {
                        settingG = null;
                    }
                }
                break;

            case MESSAGE_SET_CUSTOM:
                // Log.i(Toggle2G.TOGGLE2G, "MESSAGE_SET_3G");
                if (handleSetPreferredNetworkTypeResponse(msg, false, setG.custom))
                {
                    if (settingG == setG.custom)
                    {
                        settingG = null;
                    }
                }
                break;
            case MESSAGE_RESTORE_DATA_OFF_MONITORING:
                checkToRestoreData(5000);
            }
        }

        private void handleGetPreferredNetworkTypeResponse(Message msg)
        {
            try
            {
                Field declaredField = msg.obj.getClass().getDeclaredField("exception");
                Object exception = declaredField.get(msg.obj);
                if (exception != null)
                {
                    Log.e(Toggle2G.TOGGLE2G, "Error Setting: " + declaredField.get(msg.obj));
                }
                else
                {
                    declaredField = msg.obj.getClass().getDeclaredField("result");
                    Object result = declaredField.get(msg.obj);
                    int type = ((int[]) result)[0];

                    currentNetwork = type;
                    Log.i(Toggle2G.TOGGLE2G, "2g=" + is2g() + " (" + type + ")");

                    Toggle2GService.showNotification(context, is2g());
                }
            }
            catch (Exception e)
            {
                Log.e(Toggle2G.TOGGLE2G, "Error!", e);
            }

        }

        private boolean handleSetPreferredNetworkTypeResponse(Message msg, boolean set2g, setG set)
        {
            try
            {
                Field declaredField = msg.obj.getClass().getDeclaredField("exception");
                Object exception = declaredField.get(msg.obj);
                
                if (exception != null)
                {
                    Log.e(Toggle2G.TOGGLE2G, "Error Setting: " + exception);
                    
                    // try again!
                    long timeout = msg.arg2 * 1000;

                    Thread.sleep(500);
                    if ( ( settingG == null || set == settingG ) && SystemClock.uptimeMillis() < timeout)
                    {
                        Log.i(Toggle2G.TOGGLE2G, "retry timeout left = " + (timeout - SystemClock.uptimeMillis()));
                        setPreferredNetworkType.invoke(mPhone, new Object[] { msg.arg1, setHandler.obtainMessage(msg.what, msg.arg1, msg.arg2) });
                    }
                    else
                    {
                        Log.i(Toggle2G.TOGGLE2G, "retry timeout over, giving up");
                    }
                }
                else
                {
                    getNetwork();
                    return true;
                }
            }
            catch (Exception e)
            {
                Log.e(Toggle2G.TOGGLE2G, "Error!", e);
            }
            
            return false;
        }

        @Override
        public boolean queueIdle()
        {
            Log.e(Toggle2G.TOGGLE2G, "Idle Queue");
            if (settingG == setG.set2g)
            {
                set2gNow();
            }
            else if (settingG == setG.set3g)
            {
                set3gNow();
            }
            else if (settingG == setG.custom)
            {
                setCustomNow();
            }

            return false;
        }
    }

    void checkToRestoreData(int tryFor)
    {
        Boolean lastSetting = mCurrentDataSetting;
        if( lastSetting != null && lastSetting )
        {
            Log.i(Toggle2G.TOGGLE2G, "Trying to enable Data Setting" );
            
            setMobileDataEnabled( context, true );
            long timeout = System.currentTimeMillis() + tryFor;
            while ( !getMobileData(context) && System.currentTimeMillis() < timeout)
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                }
                setMobileDataEnabled( context, true );
            }
            Log.i(Toggle2G.TOGGLE2G, "Data Setting is now " + getMobileData(context) );
        }
        mCurrentDataSetting = null;
    }
    
    
    private void notfyPlugin()
    {
        // Log.i(Toggle2G.TOGGLE2G, "notfyPlugin()");
        Intent notifyIntent = new Intent(Toggle2GWidgetReceiver.SET_ACTION);
        if (!Toggle2GService.isRunning())
        {
            notifyIntent.putExtra("setting", is2g() ? "2g" : "3g");
        }
        else
        {
            notifyIntent.putExtra("setting", "auto");
        }

        context.sendBroadcast(notifyIntent);
    }

    public boolean is2g()
    {
        if (currentNetwork == Toggle2G.network2GSelect)
        {
            return true;
        }
        else if (currentNetwork == Toggle2G.network3GSelect)
        {
            return false;
        }
        else if (currentNetwork == Toggle2G.NETWORK_MODE_GSM_ONLY)
        {
            // 2G ONLY
            return true;
        }
        return false;
    }

    public void setNetworkNow(String reason, int net)
    {
        if (settingG != setG.custom || customNetwork != net)
        {
            reasonCustom = reason;
            settingG = setG.custom;
            customNetwork = net;
            Looper.myQueue().addIdleHandler(setHandler);
        }
    }

    void setMobileDataEnabled(Context context, boolean enabled)
    {
        try
        {
            final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final Class<?> conmanClass = Class.forName(conman.getClass().getName());
            final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
            iConnectivityManagerField.setAccessible(true);
            final Object iConnectivityManager = iConnectivityManagerField.get(conman);
            final Class<?> iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
            final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
            setMobileDataEnabledMethod.setAccessible(true);

            setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
        }
        catch (Exception e)
        {
            Log.e(Toggle2G.TOGGLE2G, "setMobileDataEnabled Error!", e);
        }
    }

    boolean getMobileData(Context context)
    {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        Method m = null;
        try
        {
            m = ConnectivityManager.class.getDeclaredMethod("getMobileDataEnabled");
            m.setAccessible(true);
            return (Boolean) m.invoke(connectivityManager);
        }
        catch (Exception e)
        {
            Log.e(Toggle2G.TOGGLE2G, "getMobileDataEnabled Error!", e);
            return true;
        }

    }

}
