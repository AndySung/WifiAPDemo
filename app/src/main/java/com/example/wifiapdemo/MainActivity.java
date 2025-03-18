package com.example.wifiapdemo;

import android.Manifest;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WifiManager wifiManager;
    private Switch switchWifiAP;
    private TextView tvApInfo;
    private ListView lvConnectedDevices;
    private List<String> connectedDevices;
    private ArrayAdapter<String> deviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        connectedDevices = new ArrayList<>();
        
        initViews();
        checkPermissions();
        setupWifiAPSwitch();
        setupDevicesList();
    }

    private void initViews() {
        switchWifiAP = findViewById(R.id.switchWifiAP);
        tvApInfo = findViewById(R.id.tvApInfo);
        lvConnectedDevices = findViewById(R.id.lvConnectedDevices);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            };
            requestPermissions(permissions, 1);
        }
    }

    private void setupWifiAPSwitch() {
        switchWifiAP.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startWifiAP();
            } else {
                stopWifiAP();
            }
        });
    }

    private void setupDevicesList() {
        deviceAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_list_item_1, 
                connectedDevices);
        lvConnectedDevices.setAdapter(deviceAdapter);
    }

    private void startWifiAP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WifiManager.LocalOnlyHotspotCallback callback = new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                    WifiConfiguration config = reservation.getWifiConfiguration();
                    updateApInfo(config);
                }

                @Override
                public void onFailed(int reason) {
                    Toast.makeText(MainActivity.this, 
                            "WiFi AP 启动失败", 
                            Toast.LENGTH_SHORT).show();
                }
            };
            wifiManager.startLocalOnlyHotspot(callback, null);
        }
    }

    private void stopWifiAP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                    reservation.close();
                }
            }, null);
        }
        tvApInfo.setText("AP信息：");
        connectedDevices.clear();
        deviceAdapter.notifyDataSetChanged();
    }

    private void updateApInfo(WifiConfiguration config) {
        String ipAddress = getIpAddress();
        String ssid = config != null ? config.SSID : "Unknown";
        String password = config != null ? config.preSharedKey : "Unknown";
        
        String apInfo = "AP信息：\n" +
                "SSID: " + ssid + "\n" +
                "密码: " + password + "\n" +
                "IP地址: " + ipAddress;
        
        tvApInfo.setText(apInfo);
        
        startDeviceCheck();
    }

    private String getIpAddress() {
        int ipAddress = wifiManager.getDhcpInfo().serverAddress;
        return String.format(
                "%d.%d.%d.%d",
                ipAddress & 0xff,
                (ipAddress >> 8) & 0xff,
                (ipAddress >> 16) & 0xff,
                (ipAddress >> 24) & 0xff
        );
    }

    private void startDeviceCheck() {
        new Thread(() -> {
            while (switchWifiAP.isChecked()) {
                // 这里需要实现获取已连接设备的逻辑
                // 可以通过读取 /proc/net/arp 文件或其他方式
                // 这里仅作示例
                updateConnectedDevices(List.of("192.168.43.1", "192.168.43.2"));
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateConnectedDevices(List<String> devices) {
        runOnUiThread(() -> {
            connectedDevices.clear();
            connectedDevices.addAll(devices);
            deviceAdapter.notifyDataSetChanged();
        });
    }
}