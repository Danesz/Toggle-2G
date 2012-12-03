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

import java.util.Map;
import java.util.Map.Entry;

import com.mb.toggle2g.RootCommand.CommandResult;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;

public class Toggle2G extends PreferenceActivity implements OnSharedPreferenceChangeListener, OnPreferenceClickListener
{
    private static final String APP_FOLDER = "/data/data/com.mb.toggle2g";
    private static final String SHARED_PREFS_FOLDER = APP_FOLDER + "/shared_prefs";
    private static final String SHARED_PREFS_FILE = APP_FOLDER + "/shared_prefs/com.mb.toggle2g_preferences.xml";

    SharedPreferences DEFAULT_SHARED_PREFERENCES;

    static int network2GSelect = 1;
    static int network3GSelect = 0;

    static int NETWORK_MODE_WCDMA_PREF = 0; /* GSM/WCDMA (WCDMA preferred) */
    static int NETWORK_MODE_GSM_ONLY = 1; /* GSM only */
    static int NETWORK_MODE_WCDMA_ONLY = 2; /* WCDMA only */
    static int NETWORK_MODE_GSM_UMTS = 3; /*
                                           * GSM/WCDMA (auto mode, according to
                                           * PRL) AVAILABLE Application Settings
                                           * menu
                                           */
    static int NETWORK_MODE_CDMA = 4; /*
                                       * CDMA and EvDo (auto mode, according to
                                       * PRL) AVAILABLE Application Settings
                                       * menu
                                       */
    static int NETWORK_MODE_CDMA_NO_EVDO = 5; /* CDMA only */
    static int NETWORK_MODE_EVDO_NO_CDMA = 6; /* EvDo only */
    static int NETWORK_MODE_GLOBAL = 7; /*
                                         * GSM/WCDMA, CDMA, and EvDo (auto mode,
                                         * according to PRL) AVAILABLE
                                         * Application Settings menu
                                         */
    static int NETWORK_MODE_LTE_CDMA_EVDO = 8; // LTE, CDMA and EvDo
    static int NETWORK_MODE_LTE_GSM_WCDMA = 9; // LTE, GSM/WCDMA
    static int NETWORK_MODE_LTE_CMDA_EVDO_GSM_WCDMA = 10; // LTE, CDMA, EvDo,
                                                          // GSM/WCDMA
    static int NETWORK_MODE_LTE_ONLY = 11; // LTE Only mode.

    public static final String TOGGLE2G = "Toggle2G";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Log.i(TOGGLE2G, "loading preferences");
        DEFAULT_SHARED_PREFERENCES = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences phonePreferences = getPhonePreferences(this, true);
        if ( phonePreferences != null )
        {
            Log.i(TOGGLE2G, "using shared preferences");
            Editor edit = DEFAULT_SHARED_PREFERENCES.edit();
            Map<String, ?> all = phonePreferences.getAll();
            for (Entry<String, ?> entry : all.entrySet())
            {
                Object v = entry.getValue();
                String key = entry.getKey();
                if (v instanceof Boolean)
                {
                    edit.putBoolean(key, ((Boolean) v).booleanValue());
                }
                else if (v instanceof Float)
                {
                    edit.putFloat(key, ((Float) v).floatValue());
                }
                else if (v instanceof Integer)
                {
                    edit.putInt(key, ((Integer) v).intValue());
                }
                else if (v instanceof Long)
                {
                    edit.putLong(key, ((Long) v).longValue());
                }
                else if (v instanceof String)
                {
                    edit.putString(key, ((String) v));
                }
            }
            edit.commit();
        }
        else
        {
            preparePreferences(this);
        }

        addPreferencesFromResource(R.xml.preference);
        
        // setup the preferences
        boolean service = DEFAULT_SHARED_PREFERENCES.getBoolean("enableService", false);
        otherSettings(service);

        DEFAULT_SHARED_PREFERENCES.registerOnSharedPreferenceChangeListener(this);
        if (!DEFAULT_SHARED_PREFERENCES.contains("wait4userNotification"))
        {
            // backward compatibility
            boolean w4u = DEFAULT_SHARED_PREFERENCES.getBoolean("wait4user", false);
            Editor edit = DEFAULT_SHARED_PREFERENCES.edit();
            edit.putBoolean("wait4userNotification", w4u);
            edit.commit();
            ((CheckBoxPreference) getPreferenceScreen().findPreference("wait4userNotification")).setChecked(w4u);
        }

        if (!DEFAULT_SHARED_PREFERENCES.contains("batteryLevelEnabled"))
        {
            // backward compatibility
            int bat = Integer.valueOf(DEFAULT_SHARED_PREFERENCES.getString("batteryLevel", Toggle2GService.DEFAULT_2G_LOW_BATTERY));
            Editor edit = DEFAULT_SHARED_PREFERENCES.edit();
            edit.putBoolean("batteryLevelEnabled", bat > 0);
            if (bat == 0)
            {
                edit.putString("batteryLevel", Toggle2GService.DEFAULT_2G_LOW_BATTERY);
            }
            edit.commit();
            ((CheckBoxPreference) getPreferenceScreen().findPreference("batteryLevelEnabled")).setChecked(bat > 0);
        }

        Preference p = getPreferenceScreen().findPreference("wait4user");
        p.setOnPreferenceClickListener(this);

        p = getPreferenceScreen().findPreference("wait4userNotification");
        p.setOnPreferenceClickListener(this);

        loadNetworkSettings(this);

        try
        {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            setTitle(getTitle() + " Version: " + version);
        }
        catch (Exception e)
        {
        }
        
        if (service)
        {
            Toggle2GService.checkLockService(this, true);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        boolean service = getPreferences(this).getBoolean("enableService", false);
        otherSettings(service);
    }
    
    public static SharedPreferences getPreferences(Context context)
    {
        SharedPreferences phonePreferences = getPhonePreferences(context, false);
        if ( phonePreferences == null )
        {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
        return phonePreferences;
    }

    public static SharedPreferences getPhonePreferences(Context context, boolean log)
    {
        try
        {
            Context con = context.createPackageContext("com.android.phone", Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sharedPreferences = con.getSharedPreferences("toggle2g", Context.MODE_WORLD_WRITEABLE);
            if ( log && sharedPreferences == null )
            {
                Log.e(TOGGLE2G, "Using Legacy Settings becuase shared file not returned");
            }
            return sharedPreferences;
        }
        catch (Exception e1)
        {
            if ( log )
            {
                Log.e(TOGGLE2G, "Using Legacy Settings", e1);
            }
        }

        return null;
    }

    public static void preparePreferences(final Context context)
    {
        try
        {
            PreferenceManager.getDefaultSharedPreferences(context);

            RootCommand cmd = new RootCommand();
            if (cmd.canSU())
            {
                CommandResult runWaitFor = cmd.su.runWaitFor("mkdir " + SHARED_PREFS_FOLDER);
                Log.i(TOGGLE2G, "mkdir " + SHARED_PREFS_FOLDER + ": " + runWaitFor.stdout);

                runWaitFor = cmd.su.runWaitFor("chmod 777 " + APP_FOLDER);
                Log.i(TOGGLE2G, "chmod 777 " + APP_FOLDER + ": " + runWaitFor.stdout);

                runWaitFor = cmd.su.runWaitFor("chmod 777 " + SHARED_PREFS_FOLDER);
                Log.i(TOGGLE2G, "chmod 777 " + SHARED_PREFS_FOLDER + ": " + runWaitFor.stdout);

                runWaitFor = cmd.su.runWaitFor("chmod 777 " + SHARED_PREFS_FILE);
                Log.i(TOGGLE2G, "chmod 777 " + SHARED_PREFS_FILE + ": " + runWaitFor.stdout);
            }
        }
        catch (Exception e)
        {
            Log.e(TOGGLE2G, "error prcessing preferences directory", e);
        }
    }

    @Override
    protected void onDestroy()
    {
        // TODO Auto-generated method stub
        super.onDestroy();

        DEFAULT_SHARED_PREFERENCES.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        SharedPreferences phonePreferences = getPhonePreferences(this, false);
        if ( phonePreferences != null )
        {
            Map<String, ?> all = sharedPreferences.getAll();
            Editor edit = phonePreferences.edit();
            Object v = all.get(key);
            if (v instanceof Boolean)
            {
                edit.putBoolean(key, ((Boolean) v).booleanValue());
            }
            else if (v instanceof Float)
            {
                edit.putFloat(key, ((Float) v).floatValue());
            }
            else if (v instanceof Integer)
            {
                edit.putInt(key, ((Integer) v).intValue());
            }
            else if (v instanceof Long)
            {
                edit.putLong(key, ((Long) v).longValue());
            }
            else if (v instanceof String)
            {
                edit.putString(key, ((String) v));
            }
            edit.commit();
        }
        
        boolean service = sharedPreferences.getBoolean("enableService", false);
        if ("enableService".equals(key))
        {
            Toggle2GService.checkLockService(this, service);
        }
        else if ("network2gselect".equals(key))
        {
            network2GSelect = Integer.parseInt(sharedPreferences.getString("network2gselect", "1"));
        }
        else if ("network3gselect".equals(key))
        {
            network3GSelect = Integer.parseInt(sharedPreferences.getString("network3gselect", "0"));
        }
        else if ("kbps_enabled".equals(key))
        {
            getPreferenceScreen().findPreference("kbps").setEnabled(sharedPreferences.getBoolean("kbps_enabled", false));
        }
        else if ("delay2GEnabled".equals(key))
        {
            getPreferenceScreen().findPreference("delay2GTime").setEnabled(sharedPreferences.getBoolean("delay2GEnabled", false));
        }
        else if ("batteryLevelEnabled".equals(key))
        {
            getPreferenceScreen().findPreference("batteryLevel").setEnabled(sharedPreferences.getBoolean("batteryLevelEnabled", false));
        }
        else if ("wait4user".equals(key))
        {
            boolean wait = sharedPreferences.getBoolean("wait4user", false);
            getPreferenceScreen().findPreference("wait4userNotification").setEnabled(wait);
            getPreferenceScreen().findPreference("when2Switch").setEnabled(!wait);
        }
        otherSettings(service);
    }

    private void otherSettings(boolean enabled)
    {
        SharedPreferences preferences = getPreferences(this);
        
        ((CheckBoxPreference) getPreferenceScreen().findPreference("enableService")).setChecked(enabled);

        ((ListPreference) getPreferenceScreen().findPreference("when2Switch")).setValue(preferences.getString("when2Switch", "0"));
        getPreferenceScreen().findPreference("when2Switch").setEnabled(enabled);

        boolean w4u = preferences.getBoolean("wait4user", false);
        ((CheckBoxPreference) getPreferenceScreen().findPreference("wait4user")).setChecked(w4u);
        getPreferenceScreen().findPreference("wait4user").setEnabled(enabled);
        ((CheckBoxPreference) getPreferenceScreen().findPreference("wait4userNotification")).setChecked(preferences.getBoolean("wait4userNotification", false));
        getPreferenceScreen().findPreference("wait4userNotification").setEnabled(w4u && enabled);

        boolean bat = preferences.getBoolean("delay2GEnabled", true);
        ((CheckBoxPreference) getPreferenceScreen().findPreference("delay2GEnabled")).setChecked(bat);
        getPreferenceScreen().findPreference("delay2GEnabled").setEnabled(enabled);
        ((ListPreference) getPreferenceScreen().findPreference("delay2GTime")).setValue(preferences.getString("delay2GTime", "10"));
        getPreferenceScreen().findPreference("delay2GTime").setEnabled(bat && enabled);

        boolean sleep2g = preferences.getBoolean("batteryLevelEnabled", true);
        ((CheckBoxPreference) getPreferenceScreen().findPreference("batteryLevelEnabled")).setChecked(sleep2g);
        getPreferenceScreen().findPreference("batteryLevelEnabled").setEnabled(enabled);
        ((ListPreference) getPreferenceScreen().findPreference("batteryLevel")).setValue(preferences.getString("batteryLevel", "20"));
        getPreferenceScreen().findPreference("batteryLevel").setEnabled(sleep2g && enabled);

        ((CheckBoxPreference) getPreferenceScreen().findPreference("2g_wifi")).setChecked(preferences.getBoolean("2g_wifi", true));
        getPreferenceScreen().findPreference("2g_wifi").setEnabled(enabled);

        ((CheckBoxPreference) getPreferenceScreen().findPreference("2g_dataoff")).setChecked(preferences.getBoolean("2g_dataoff", true));
        getPreferenceScreen().findPreference("2g_dataoff").setEnabled(enabled);
        
        boolean kbps = preferences.getBoolean("kbps_enabled", false);
        ((CheckBoxPreference) getPreferenceScreen().findPreference("kbps_enabled")).setChecked(kbps);
        getPreferenceScreen().findPreference("kbps_enabled").setEnabled(enabled);
        ((EditTextPreference) getPreferenceScreen().findPreference("kbps")).setText(preferences.getString("kbps", "2"));
        getPreferenceScreen().findPreference("kbps").setEnabled(kbps && enabled);

        ((CheckBoxPreference) getPreferenceScreen().findPreference("dataoff_switch")).setChecked(preferences.getBoolean("dataoff_switch", false));
        getPreferenceScreen().findPreference("dataoff_switch").setEnabled(enabled);

        ((CheckBoxPreference) getPreferenceScreen().findPreference("dontCheckPluggedIn")).setChecked(preferences.getBoolean("dontCheckPluggedIn", false));
        getPreferenceScreen().findPreference("dontCheckPluggedIn").setEnabled(enabled);

        ((ListPreference) getPreferenceScreen().findPreference("network2gselect")).setValue(preferences.getString("network2gselect", "1"));
        ((ListPreference) getPreferenceScreen().findPreference("network3gselect")).setValue(preferences.getString("network3gselect", "0"));
    }

    @Override
    public boolean onPreferenceClick(Preference preference)
    {
        if ("wait4user".equals(preference.getKey()))
        {
            CheckBoxPreference cb = (CheckBoxPreference) preference;
            if (cb.isChecked())
            {
                Toggle2GService running = Toggle2GService.running;
                if (running != null && running.phoneSetter != null)
                {
                    Toggle2GService.running.phoneSetter.getNetwork();
                }
            }
            else
            {
                Toggle2GService.showNotification(this, false);
            }
        }
        else if ("wait4userNotification".equals(preference.getKey()))
        {
            final CheckBoxPreference cb = (CheckBoxPreference) preference;

            // Doesn't matter what the user clicks. The end result is based on
            // the remote login.
            if (cb.isChecked())
            {
                if (!Toggle2GService.isNotificationAppInstalled(this))
                {
                    cb.setChecked(false);

                    AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                    alertDialog.setTitle(R.string.missingPlugin_title);
                    alertDialog.setMessage(getString(R.string.missingPlugin_message));
                    alertDialog.setButton(getString(R.string.missingPlugin_download), new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("market://search?q=pname:com.mb.toggle2g.plugin.notification"));
                            startActivity(intent);
                            dialog.cancel();
                            return;
                        }
                    });
                    alertDialog.setButton2(getString(R.string.missingPlugin_cancel), new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            dialog.cancel();
                            return;
                        }
                    });
                    alertDialog.show();
                }

                Toggle2GService running = Toggle2GService.running;
                if (running != null && running.phoneSetter != null)
                {
                    Toggle2GService.running.phoneSetter.getNetwork();
                }
            }
            else
            {
                Toggle2GService.showNotification(this, false);
            }
        }

        return false;
    }

    public static void loadNetworkSettings(Context context)
    {
        SharedPreferences defaultSharedPreferences = Toggle2G.getPreferences(context);
        network2GSelect = Integer.parseInt(defaultSharedPreferences.getString("network2gselect", "1"));

        String defaultNetwork = String.valueOf(SetPhoneSettingsV2.getDefaultNetwork());
        String useNetwork = "0";
        if (defaultNetwork != null && !defaultSharedPreferences.contains("network3gselect"))
        {
            useNetwork = defaultNetwork;
            Log.i(Toggle2G.TOGGLE2G, "setting default network to " + defaultNetwork);
        }

        network3GSelect = Integer.parseInt(defaultSharedPreferences.getString("network3gselect", useNetwork));
        if ( !String.valueOf(network3GSelect).equals( defaultNetwork ))
        {
            Log.i(Toggle2G.TOGGLE2G, "WARNING: default 3G network is " + defaultNetwork + ", but Toggle2G is set to use " + network3GSelect);
        }
    }
}
