package com.example.wifiapdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import java.net.NetworkInterface;
import java.io.FileReader;
import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity {
    private WifiManager wifiManager;
    private Switch switchWifiAP;
    private TextView tvApInfo;
    private ListView lvConnectedDevices;
    private List<String> connectedDevices;
    private ArrayAdapter<String> deviceAdapter;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
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
    


    // 修改 updateConnectedDevices 方法，添加日志
    private void updateConnectedDevices(List<String> devices) {
        runOnUiThread(() -> {
            Log.d("WiFiAP", "更新设备列表，数量: " + devices.size());
            connectedDevices.clear();
            connectedDevices.addAll(devices);
            deviceAdapter.notifyDataSetChanged();
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            requestPermissions(permissions, 1);
        }
    }

    // 修改获取设备方法
    private List<String> getConnectedDevices() {
        List<String> result = new ArrayList<>();
        
        try {
            // 尝试使用 su 命令获取 root 权限
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            // 执行命令
            os.writeBytes("ip neigh show\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("WiFiAP", "设备信息: " + line);
                if (line.contains("192.168.") || line.contains("172.16.")) {
                    result.add(line);
                }
            }
            
            // 如果上面的方法没有结果，尝试读取 ARP 缓存
            if (result.isEmpty()) {
                process = Runtime.getRuntime().exec("su");
                os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("cat /proc/net/arp\n");
                os.writeBytes("exit\n");
                os.flush();

                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    Log.d("WiFiAP", "ARP信息: " + line);
                    if (line.contains("192.168.") || line.contains("172.16.")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 4) {
                            String ip = parts[0];
                            String mac = parts[3];
                            result.add("IP: " + ip + ", MAC: " + mac);
                        }
                    }
                }
            }
            
            reader.close();
            os.close();
            
        } catch (Exception e) {
            Log.e("WiFiAP", "获取设备信息失败", e);
            // 如果 root 方法失败，尝试不使用 root 的方法
            try {
                Process process = Runtime.getRuntime().exec("ip neigh show");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("192.168.") || line.contains("172.16.")) {
                        result.add(line);
                    }
                }
                reader.close();
            } catch (IOException ioException) {
                Log.e("WiFiAP", "非 root 方法也失败", ioException);
            }
        }
        
        Log.d("WiFiAP", "找到设备数量: " + result.size());
        return result;
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

    private WifiManager.LocalOnlyHotspotReservation hotspotReservation; // 添加到类成员变量

    @SuppressLint("MissingPermission")
    private void startWifiAP() {
        // 先确保之前的热点已经完全关闭
        stopWifiAP();
        
        try {
            Thread.sleep(1000); // 等待之前的热点完全关闭
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                WifiManager.LocalOnlyHotspotCallback callback = new WifiManager.LocalOnlyHotspotCallback() {
                    @Override
                    public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                        hotspotReservation = reservation; // 保存热点实例
                        WifiConfiguration config = reservation.getWifiConfiguration();
                        updateApInfo(config);
                    }

                    @Override
                    public void onFailed(int reason) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                    "WiFi AP 启动失败: " + reason, 
                                    Toast.LENGTH_SHORT).show();
                            switchWifiAP.setChecked(false);
                        });
                    }

                    @Override
                    public void onStopped() {
                        hotspotReservation = null; // 清除热点实例
                        runOnUiThread(() -> {
                            switchWifiAP.setChecked(false);
                            tvApInfo.setText("AP信息：");
                            connectedDevices.clear();
                            deviceAdapter.notifyDataSetChanged();
                        });
                    }
                };
                wifiManager.startLocalOnlyHotspot(callback, null);
            } catch (Exception e) {
                Log.e("WiFiAP", "启动热点失败", e);
                Toast.makeText(this, "启动热点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                switchWifiAP.setChecked(false);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void stopWifiAP() {
        if (hotspotReservation != null) {
            hotspotReservation.close();
            hotspotReservation = null;
        }
        tvApInfo.setText("AP信息：");
        connectedDevices.clear();
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        stopWifiAP();
        super.onDestroy();
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

    @SuppressLint("DefaultLocale")
    private String getIpAddress() {
        try {
            Process process = Runtime.getRuntime().exec("ip addr show");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("192.168.")) {
                    String[] parts = line.trim().split("\\s+");
                    for (String part : parts) {
                        if (part.startsWith("192.168.")) {
                            return part.split("/")[0];
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 如果上述方法失败，回退到原来的方法
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
}