package com.kooo.evcam;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;

/**
 * 软件设置界面 Fragment
 */
public class SettingsFragment extends Fragment {

    private SwitchMaterial debugSwitch;
    private Button saveLogsButton;
    private Button overlayPermissionButton;
    private TextView overlayStatusText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 初始化控件
        debugSwitch = view.findViewById(R.id.switch_debug_to_info);
        saveLogsButton = view.findViewById(R.id.btn_save_logs);
        Button menuButton = view.findViewById(R.id.btn_menu);

        // 设置菜单按钮点击事件
        menuButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                DrawerLayout drawerLayout = getActivity().findViewById(R.id.drawer_layout);
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });

        // 初始化Debug开关状态
        if (getContext() != null) {
            debugSwitch.setChecked(AppLog.isDebugToInfoEnabled(getContext()));
        }

        // 设置Debug开关监听器
        debugSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null) {
                AppLog.setDebugToInfoEnabled(getContext(), isChecked);
                String message = isChecked ? "Debug logs will show as info" : "Debug logs will show as debug";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // 设置保存日志按钮监听器
        saveLogsButton.setOnClickListener(v -> {
            if (getContext() != null) {
                File logFile = AppLog.saveLogsToFile(getContext());
                if (logFile != null) {
                    Toast.makeText(getContext(), "Logs saved to: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), "Failed to save logs", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 初始化悬浮窗权限控件
        overlayPermissionButton = view.findViewById(R.id.btn_overlay_permission);
        overlayStatusText = view.findViewById(R.id.tv_overlay_status);

        // 更新悬浮窗权限状态
        updateOverlayPermissionStatus();

        // 设置悬浮窗权限按钮监听器
        overlayPermissionButton.setOnClickListener(v -> {
            if (getContext() != null) {
                if (WakeUpHelper.hasOverlayPermission(getContext())) {
                    Toast.makeText(getContext(), "已授权悬浮窗权限", Toast.LENGTH_SHORT).show();
                } else {
                    WakeUpHelper.requestOverlayPermission(getContext());
                    Toast.makeText(getContext(), "请在设置中授权悬浮窗权限", Toast.LENGTH_LONG).show();
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次返回时更新权限状态
        updateOverlayPermissionStatus();
    }

    /**
     * 更新悬浮窗权限状态显示
     */
    private void updateOverlayPermissionStatus() {
        if (getContext() == null || overlayStatusText == null || overlayPermissionButton == null) {
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Android 6.0 以下不需要此权限
            overlayStatusText.setText("系统版本低于 Android 6.0，无需授权");
            overlayPermissionButton.setVisibility(View.GONE);
        } else if (WakeUpHelper.hasOverlayPermission(getContext())) {
            overlayStatusText.setText("已授权 ✓");
            overlayStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            overlayPermissionButton.setText("已授权");
            overlayPermissionButton.setEnabled(false);
        } else {
            overlayStatusText.setText("未授权 - 后台钉钉命令需要此权限");
            overlayStatusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            overlayPermissionButton.setText("去授权");
            overlayPermissionButton.setEnabled(true);
        }
    }
}
