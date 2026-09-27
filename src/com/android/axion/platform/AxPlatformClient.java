/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.axion.platform;

import android.annotation.NonNull;
import android.app.UiModeManager;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class AxPlatformClient {

    private static final String TAG = "AxPlatformClient";
    private static final long RECONNECT_DELAY_MS = 3000L;

    public static final String ACTION_BIND = "com.android.systemui.action.AX_PLATFORM";
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    public static final String KEY_WIFI_SCAN = "wifi_scan";
    public static final String KEY_BATTERY = "battery";
    public static final String KEY_MEDIA = "media";
    public static final String KEY_ALARM = "alarm";
    public static final String KEY_CALENDAR = "calendar";
    public static final String KEY_CONFIG = "config";
    public static final String KEY_DOZE = "doze";
    public static final String KEY_KEYGUARD = "keyguard";
    public static final String KEY_NOW_PLAYING = "now_playing";

    public static final String ACTION_WIFI_CONNECT = "wifi_connect";
    public static final String ACTION_BT_CONNECT = "bt_connect";

    private static volatile AxPlatformClient sInstance;
    private static volatile boolean sTorchActive = false;

    private final Object mLock = new Object();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<StateCallback, IAxPlatformCallback> mCallbacks =
            new ConcurrentHashMap<>();

    private volatile IAxPlatformService mService;
    private Context mContext;
    private boolean mBound;

    public interface StateCallback {
        void onStateChanged(@NonNull String key, @NonNull AxFeatureState state);
    }

    @FunctionalInterface
    private interface RemoteAction {
        void run(IAxPlatformService service) throws RemoteException;
    }

    @FunctionalInterface
    private interface RemoteQuery<T> {
        T run(IAxPlatformService service) throws RemoteException;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IAxPlatformService.Stub.asInterface(service);
            Log.d(TAG, "Connected to AxPlatform service");
            reregisterCallbacks();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
            Log.d(TAG, "Disconnected from AxPlatform service");
            scheduleReconnect();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mService = null;
            mBound = false;
            Log.w(TAG, "Binding died, reconnecting");
            scheduleReconnect();
        }
    };

    private AxPlatformClient() {}

    @NonNull
    public static AxPlatformClient getInstance() {
        if (sInstance == null) {
            synchronized (AxPlatformClient.class) {
                if (sInstance == null) {
                    sInstance = new AxPlatformClient();
                }
            }
        }
        return sInstance;
    }

    public void init(@NonNull Context context) {
        synchronized (mLock) {
            if (mContext != null) return;
            mContext = context.getApplicationContext();
        }
        setupTorchListener();
        bind();
    }

    public boolean isAvailable() {
        return mService != null;
    }

    public void toggle(@NonNull String feature) {
        if (mService != null) {
            call("toggle:" + feature, service -> service.toggle(feature));
        } else {
            executeLocalToggle(feature);
        }
    }

    public void setEnabled(@NonNull String feature, boolean enabled) {
        if (mService != null) {
            call("setEnabled:" + feature, service -> service.setEnabled(feature, enabled));
        } else {
            executeLocalSetEnabled(feature, enabled);
        }
    }

    public void setValue(@NonNull String feature, int value) {
        if (mService != null) {
            call("setValue:" + feature, service -> service.setValue(feature, value));
        } else {
            executeLocalSetValue(feature, value);
        }
    }

    public void performAction(@NonNull String action, @NonNull String param) {
        call("performAction:" + action, service -> service.performAction(action, param));
    }

    @NonNull
    public AxFeatureState getState(@NonNull String key) {
        return AxFeatureState.fromBundle(getStateBundle(key));
    }

    @NonNull
    public Map<String, AxFeatureState> getStates() {
        Bundle states = query("getAllStates", IAxPlatformService::getAllStates, null);
        Map<String, AxFeatureState> result = new HashMap<>();
        if (states != null) {
            for (String key : states.keySet()) {
                result.put(key, AxFeatureState.fromBundle(states.getBundle(key)));
            }
        } else {
            for (String feature : getSupportedFeatures()) {
                result.put(feature, getState(feature));
            }
        }
        return result;
    }

    @NonNull
    public String[] getSupportedFeatures() {
        String[] supported = query("getSupportedFeatures", IAxPlatformService::getSupportedFeatures, null);
        if (supported != null && supported.length > 0) {
            return supported;
        }
        return AxPlatformFeature.getBaseFeatures();
    }

    public void registerCallback(@NonNull Executor executor, @NonNull StateCallback callback) {
        Executor callbackExecutor = Objects.requireNonNull(executor);
        StateCallback stateCallback = Objects.requireNonNull(callback);
        IAxPlatformCallback remote = new IAxPlatformCallback.Stub() {
            @Override
            public void onStateChanged(String key, Bundle state) {
                AxFeatureState featureState = AxFeatureState.fromBundle(state);
                callbackExecutor.execute(() -> stateCallback.onStateChanged(key, featureState));
            }
        };
        if (mCallbacks.putIfAbsent(stateCallback, remote) != null) {
            throw new IllegalArgumentException("Callback is already registered");
        }
        call("registerCallback", service -> service.registerCallback(remote));
    }

    public void unregisterCallback(@NonNull StateCallback callback) {
        IAxPlatformCallback remote = mCallbacks.remove(Objects.requireNonNull(callback));
        if (remote != null) {
            call("unregisterCallback", service -> service.unregisterCallback(remote));
        }
    }

    private Bundle getStateBundle(String key) {
        Bundle bundle = query("getState:" + key, service -> service.getState(key), null);
        if (bundle != null && !bundle.isEmpty()) {
            return bundle;
        }
        return getLocalStateBundle(key);
    }

    private void bind() {
        synchronized (mLock) {
            if (mBound || mContext == null) return;
            Intent intent = new Intent(ACTION_BIND).setPackage(SYSTEMUI_PACKAGE);
            try {
                mBound = mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                if (!mBound) {
                    Log.w(TAG, "AxPlatform service not available on this ROM, using standard AOSP platform fallback");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "bind", e);
            }
        }
    }

    private void scheduleReconnect() {
        mHandler.removeCallbacksAndMessages(this);
        mHandler.postDelayed(this::bind, this, RECONNECT_DELAY_MS);
    }

    private void reregisterCallbacks() {
        for (IAxPlatformCallback callback : mCallbacks.values()) {
            call("reregisterCallback", service -> service.registerCallback(callback));
        }
    }

    private IAxPlatformService getService() {
        return mService;
    }

    private void call(String method, RemoteAction action) {
        try {
            IAxPlatformService service = getService();
            if (service != null) {
                action.run(service);
            } else {
                Log.w(TAG, method + ": service not connected, attempting rebind");
                bind();
            }
        } catch (RemoteException e) {
            Log.e(TAG, method, e);
        }
    }

    private <T> T query(String method, RemoteQuery<T> action, T fallback) {
        try {
            IAxPlatformService service = getService();
            if (service != null) return action.run(service);
        } catch (RemoteException e) {
            Log.e(TAG, method, e);
        }
        return fallback;
    }

    private void setupTorchListener() {
        if (mContext == null) return;
        try {
            CameraManager cm = mContext.getSystemService(CameraManager.class);
            if (cm != null) {
                cm.registerTorchCallback(new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(@NonNull String cameraId, boolean enabled) {
                        sTorchActive = enabled;
                        notifyLocalStateChanged(AxPlatformFeature.FLASHLIGHT);
                    }
                }, mHandler);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to register torch callback", e);
        }
    }

    private void notifyLocalStateChanged(@NonNull String feature) {
        Bundle state = getLocalStateBundle(feature);
        for (IAxPlatformCallback callback : mCallbacks.values()) {
            try {
                callback.onStateChanged(feature, state);
            } catch (RemoteException e) {
                Log.w(TAG, "Local callback error", e);
            }
        }
    }

    private Bundle getLocalStateBundle(@NonNull String key) {
        if (mContext == null) return Bundle.EMPTY;
        boolean active = false;
        String label = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String category = AxPlatformFeature.getCategory(key);

        try {
            switch (key) {
                case AxPlatformFeature.ROTATION:
                    active = Settings.System.getInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
                    label = "Auto-rotate";
                    break;
                case AxPlatformFeature.AIRPLANE_MODE:
                    active = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
                    label = "Airplane Mode";
                    break;
                case AxPlatformFeature.BATTERY_SAVER:
                    PowerManager pm = mContext.getSystemService(PowerManager.class);
                    active = pm != null && pm.isPowerSaveMode();
                    label = "Battery Saver";
                    break;
                case AxPlatformFeature.DARK_MODE:
                    int mode = mContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    active = mode == Configuration.UI_MODE_NIGHT_YES;
                    label = "Dark theme";
                    break;
                case AxPlatformFeature.BLUETOOTH:
                    BluetoothManager bm = mContext.getSystemService(BluetoothManager.class);
                    active = bm != null && bm.getAdapter() != null && bm.getAdapter().isEnabled();
                    label = "Bluetooth";
                    break;
                case AxPlatformFeature.WIFI:
                    WifiManager wm = mContext.getSystemService(WifiManager.class);
                    active = wm != null && wm.isWifiEnabled();
                    label = "Internet";
                    break;
                case AxPlatformFeature.FLASHLIGHT:
                    active = sTorchActive;
                    label = "Flashlight";
                    break;
                case AxPlatformFeature.RINGER_MODE:
                    AudioManager am = mContext.getSystemService(AudioManager.class);
                    int ringer = am != null ? am.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
                    active = ringer == AudioManager.RINGER_MODE_NORMAL;
                    String sec = ringer == AudioManager.RINGER_MODE_SILENT ? "Silent" : (ringer == AudioManager.RINGER_MODE_VIBRATE ? "Vibrate" : "Ring");
                    return AxFeatureState.newBuilder()
                            .setFeature(key)
                            .setActive(active)
                            .setLabel("Sound")
                            .setSecondaryLabel(sec)
                            .setRingerMode(ringer)
                            .setCategory(category)
                            .build().toBundle();
                default:
                    label = key.substring(0, 1).toUpperCase() + key.substring(1);
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error resolving local state for " + key, e);
        }

        return AxFeatureState.newBuilder()
                .setFeature(key)
                .setActive(active)
                .setLabel(label)
                .setCategory(category)
                .build().toBundle();
    }

    private void executeLocalToggle(@NonNull String feature) {
        if (mContext == null) return;
        try {
            switch (feature) {
                case AxPlatformFeature.ROTATION:
                    boolean rot = Settings.System.getInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
                    Settings.System.putInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, rot ? 0 : 1);
                    notifyLocalStateChanged(feature);
                    break;
                case AxPlatformFeature.AIRPLANE_MODE:
                    boolean air = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
                    Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, air ? 0 : 1);
                    mContext.sendBroadcast(new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", !air));
                    notifyLocalStateChanged(feature);
                    break;
                case AxPlatformFeature.BATTERY_SAVER:
                    PowerManager pm = mContext.getSystemService(PowerManager.class);
                    if (pm != null) {
                        pm.setPowerSaveModeEnabled(!pm.isPowerSaveMode());
                        notifyLocalStateChanged(feature);
                    }
                    break;
                case AxPlatformFeature.DARK_MODE:
                    UiModeManager um = mContext.getSystemService(UiModeManager.class);
                    if (um != null) {
                        boolean isNight = um.getNightMode() == UiModeManager.MODE_NIGHT_YES;
                        um.setNightMode(isNight ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
                        notifyLocalStateChanged(feature);
                    }
                    break;
                case AxPlatformFeature.BLUETOOTH:
                    BluetoothManager bm = mContext.getSystemService(BluetoothManager.class);
                    if (bm != null && bm.getAdapter() != null) {
                        if (bm.getAdapter().isEnabled()) bm.getAdapter().disable();
                        else bm.getAdapter().enable();
                        notifyLocalStateChanged(feature);
                    }
                    break;
                case AxPlatformFeature.FLASHLIGHT:
                    CameraManager cm = mContext.getSystemService(CameraManager.class);
                    if (cm != null) {
                        String id = cm.getCameraIdList()[0];
                        sTorchActive = !sTorchActive;
                        cm.setTorchMode(id, sTorchActive);
                        notifyLocalStateChanged(feature);
                    }
                    break;
                case AxPlatformFeature.RINGER_MODE:
                    AudioManager am = mContext.getSystemService(AudioManager.class);
                    if (am != null) {
                        int cur = am.getRingerMode();
                        int next = (cur == AudioManager.RINGER_MODE_NORMAL) ? AudioManager.RINGER_MODE_VIBRATE :
                                ((cur == AudioManager.RINGER_MODE_VIBRATE) ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL);
                        am.setRingerMode(next);
                        notifyLocalStateChanged(feature);
                    }
                    break;
                default:
                    Log.d(TAG, "Local toggle requested for unhandled feature: " + feature);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing local toggle for " + feature, e);
        }
    }

    private void executeLocalSetEnabled(@NonNull String feature, boolean enabled) {
        if (mContext == null) return;
        try {
            switch (feature) {
                case AxPlatformFeature.ROTATION:
                    Settings.System.putInt(mContext.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0);
                    notifyLocalStateChanged(feature);
                    break;
                case AxPlatformFeature.AIRPLANE_MODE:
                    Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, enabled ? 1 : 0);
                    mContext.sendBroadcast(new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled));
                    notifyLocalStateChanged(feature);
                    break;
                case AxPlatformFeature.BATTERY_SAVER:
                    PowerManager pm = mContext.getSystemService(PowerManager.class);
                    if (pm != null) {
                        pm.setPowerSaveModeEnabled(enabled);
                        notifyLocalStateChanged(feature);
                    }
                    break;
                case AxPlatformFeature.FLASHLIGHT:
                    CameraManager cm = mContext.getSystemService(CameraManager.class);
                    if (cm != null) {
                        String id = cm.getCameraIdList()[0];
                        sTorchActive = enabled;
                        cm.setTorchMode(id, enabled);
                        notifyLocalStateChanged(feature);
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting local state for " + feature, e);
        }
    }

    private void executeLocalSetValue(@NonNull String feature, int value) {
        if (mContext == null) return;
        try {
            if (AxPlatformFeature.RINGER_MODE.equals(feature)) {
                AudioManager am = mContext.getSystemService(AudioManager.class);
                if (am != null) {
                    am.setRingerMode(value);
                    notifyLocalStateChanged(feature);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting local value for " + feature, e);
        }
    }
}
