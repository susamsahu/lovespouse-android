package com.lovespouse.controller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

public class LoveSpouseBleManager {
    private static final String TAG = "LoveSpouseBLE";
    private static final int COMPANY_ID = 0x00FF; // Decimal 255
    private static final String PREFIX_HEX = "0000006db643ce97fe427c";

    private final Context context;
    private BluetoothLeAdvertiser advertiser;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isBroadcasting = false;

    // Verified command lookup tables
    private final Map<Integer, String> shakeCommands = new HashMap<>();
    private final Map<Integer, String> shockCommands = new HashMap<>();
    private final Map<Integer, String> telescopicCommands = new HashMap<>();
    private final Map<String, String> stopCommands = new HashMap<>();

    public LoveSpouseBleManager(Context context) {
        this.context = context;
        initCommands();
        initBluetooth();
    }

    private void initCommands() {
        shakeCommands.put(1, "F41D7C");
        shakeCommands.put(2, "F7864E");
        shakeCommands.put(3, "F60F5F");
        shakeCommands.put(4, "F1B02B");
        shakeCommands.put(5, "F0393A");
        shakeCommands.put(6, "F3A208");
        shakeCommands.put(7, "F22B19");
        shakeCommands.put(8, "FDDCE1");
        shakeCommands.put(9, "FC55F0");
        stopCommands.put("shake", "C5175C");

        shockCommands.put(1, "d41f5d");
        shockCommands.put(2, "d7846f");
        shockCommands.put(3, "d60d7e");
        shockCommands.put(4, "d1b20a");
        shockCommands.put(5, "d03b1b");
        shockCommands.put(6, "d3a029");
        shockCommands.put(7, "d22938");
        shockCommands.put(8, "dddec0");
        shockCommands.put(9, "dc57d1");
        stopCommands.put("shock", "d5964c");

        telescopicCommands.put(1, "E49C6C");
        telescopicCommands.put(2, "E7075E");
        telescopicCommands.put(3, "E68E4F");
        telescopicCommands.put(4, "E1313B");
        telescopicCommands.put(5, "E0B82A");
        telescopicCommands.put(6, "E32318");
        telescopicCommands.put(7, "E2AA09");
        telescopicCommands.put(8, "ED5DF1");
        telescopicCommands.put(9, "ECD4E0");
        stopCommands.put("telescopic", "E5157D");
    }

    private void initBluetooth() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            BluetoothAdapter adapter = bm.getAdapter();
            if (adapter != null && adapter.isEnabled()) {
                advertiser = adapter.getBluetoothLeAdvertiser();
            }
        }
    }

    public synchronized void sendCommand(int speed, String mode) {
        if (advertiser == null) {
            initBluetooth();
            if (advertiser == null) {
                Log.e(TAG, "BluetoothLeAdvertiser not available. Check BLE permissions or enable Bluetooth.");
                return;
            }
        }

        String suffix;
        if (speed <= 0) {
            suffix = stopCommands.getOrDefault(mode.toLowerCase(), "C5175C");
        } else {
            int spd = Math.min(Math.max(speed, 1), 9);
            switch (mode.toLowerCase()) {
                case "shock":
                    suffix = shockCommands.get(spd);
                    break;
                case "telescopic":
                    suffix = telescopicCommands.get(spd);
                    break;
                default:
                    suffix = shakeCommands.get(spd);
                    break;
            }
        }

        if (suffix == null) suffix = "C5175C";

        byte[] payload = hexToBytes(PREFIX_HEX + suffix);
        broadcastPayload(payload);
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            isBroadcasting = true;
            Log.d(TAG, "BLE Broadcast Active.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            isBroadcasting = false;
            Log.e(TAG, "BLE Broadcast Failed: " + errorCode);
        }
    };

    private void broadcastPayload(byte[] payload) {
        try {
            if (isBroadcasting) {
                advertiser.stopAdvertising(advertiseCallback);
                isBroadcasting = false;
            }

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build();

            AdvertiseData data = new AdvertiseData.Builder()
                .addManufacturerData(COMPANY_ID, payload)
                .build();

            advertiser.startAdvertising(settings, data, advertiseCallback);

            // Repeat pulse for 450ms to ensure 100% RF packet receipt by toy
            handler.postDelayed(() -> {
                try {
                    advertiser.stopAdvertising(advertiseCallback);
                    isBroadcasting = false;
                } catch (Exception ignored) {}
            }, 450);

        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException: Missing BLUETOOTH_ADVERTISE permission", se);
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error", e);
        }
    }

    private byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
