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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

    // 在类成员变量中添加
    private SwipeRefreshLayout swipeRefresh;
    
    // 在 initViews() 方法中添加
    private void initViews() {
        switchWifiAP = findViewById(R.id.switchWifiAP);
        tvApInfo = findViewById(R.id.tvApInfo);
        lvConnectedDevices = findViewById(R.id.lvConnectedDevices);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        
        // 设置下拉刷新监听器
        swipeRefresh.setOnRefreshListener(() -> {
            refreshConnectedDevices();
        });
    }
    
    // 添加刷新方法
    private void refreshConnectedDevices() {
        if (!switchWifiAP.isChecked()) {
            swipeRefresh.setRefreshing(false);
            return;
        }
    
        new Thread(() -> {
            // 这里实现实际的设备扫描逻辑
            List<String> connectedIPs = getConnectedDevices();
            runOnUiThread(() -> {
                connectedDevices.clear();
                connectedDevices.addAll(connectedIPs);
                deviceAdapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
            });
        }).start();
    }
    
    // 添加获取已连接设备的方法
    private List<String> getConnectedDevices() {
        List<String> result = new ArrayList<>();
        try {
            // 读取 ARP 缓存表获取连接设备
            Process process = Runtime.getRuntime().exec("ip neigh");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("192.168.") && line.contains("REACHABLE")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 0) {
                        result.add(parts[0]); // 添加 IP 地址
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
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
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "请先开启WiFi", Toast.LENGTH_SHORT).show();
            switchWifiAP.setChecked(false);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WifiManager.LocalOnlyHotspotCallback callback = new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                    WifiConfiguration config = reservation.getWifiConfiguration();
                    updateApInfo(config);
                }

                @Override
                public void onFailed(int reason) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                "WiFi AP 启动失败", 
                                Toast.LENGTH_SHORT).show();
                        switchWifiAP.setChecked(false);
                    });
                }
            };
            try {
                wifiManager.startLocalOnlyHotspot(callback, null);
            } catch (IllegalStateException e) {
                Toast.makeText(this, "热点已经在运行中", Toast.LENGTH_SHORT).show();
                switchWifiAP.setChecked(false);
            }
        }
    }

    private void stopWifiAP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                    @Override
                    public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                        reservation.close();
                    }
                }, null);
            } catch (IllegalStateException e) {
                // 忽略异常，因为这可能意味着热点已经关闭
            }
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

    // 只保留这一个 startDeviceCheck 方法，删除其他重复的
    private void startDeviceCheck() {
        new Thread(() -> {
            while (switchWifiAP.isChecked()) {
                List<String> devices = getConnectedDevices();
                updateConnectedDevices(devices);
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