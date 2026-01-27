package com.kooo.evcam;

import android.content.Context;

import com.kooo.evcam.dingtalk.DingTalkApiClient;
import com.kooo.evcam.dingtalk.DingTalkConfig;
import com.kooo.evcam.dingtalk.DingTalkStreamManager;
import com.kooo.evcam.telegram.TelegramApiClient;
import com.kooo.evcam.telegram.TelegramBotManager;
import com.kooo.evcam.telegram.TelegramConfig;

/**
 * 远程服务管理器（单例）
 * 管理钉钉和 Telegram 服务的生命周期，确保在 Activity 重建时服务不会中断
 * 这个类持有服务实例的强引用，避免被垃圾回收
 */
public class RemoteServiceManager {
    private static final String TAG = "RemoteServiceManager";
    private static RemoteServiceManager instance;

    // 钉钉服务
    private DingTalkStreamManager dingTalkStreamManager;
    private DingTalkApiClient dingTalkApiClient;

    // Telegram 服务
    private TelegramBotManager telegramBotManager;
    private TelegramApiClient telegramApiClient;

    private RemoteServiceManager() {
        // 私有构造函数
    }

    public static synchronized RemoteServiceManager getInstance() {
        if (instance == null) {
            instance = new RemoteServiceManager();
        }
        return instance;
    }

    // ==================== DingTalk 服务管理 ====================

    public void setDingTalkService(DingTalkStreamManager manager, DingTalkApiClient apiClient) {
        this.dingTalkStreamManager = manager;
        this.dingTalkApiClient = apiClient;
        AppLog.d(TAG, "DingTalk service registered");
    }

    public DingTalkStreamManager getDingTalkStreamManager() {
        return dingTalkStreamManager;
    }

    public DingTalkApiClient getDingTalkApiClient() {
        return dingTalkApiClient;
    }

    public boolean isDingTalkRunning() {
        return dingTalkStreamManager != null && dingTalkStreamManager.isRunning();
    }

    public void clearDingTalkService() {
        if (dingTalkStreamManager != null) {
            dingTalkStreamManager.stop();
        }
        this.dingTalkStreamManager = null;
        this.dingTalkApiClient = null;
        AppLog.d(TAG, "DingTalk service cleared");
    }

    // ==================== Telegram 服务管理 ====================

    public void setTelegramService(TelegramBotManager manager, TelegramApiClient apiClient) {
        this.telegramBotManager = manager;
        this.telegramApiClient = apiClient;
        AppLog.d(TAG, "Telegram service registered");
    }

    public TelegramBotManager getTelegramBotManager() {
        return telegramBotManager;
    }

    public TelegramApiClient getTelegramApiClient() {
        return telegramApiClient;
    }

    public boolean isTelegramRunning() {
        return telegramBotManager != null && telegramBotManager.isRunning();
    }

    public void clearTelegramService() {
        if (telegramBotManager != null) {
            telegramBotManager.stop();
        }
        this.telegramBotManager = null;
        this.telegramApiClient = null;
        AppLog.d(TAG, "Telegram service cleared");
    }

    // ==================== 通用方法 ====================

    /**
     * 检查是否有任何远程服务在运行
     */
    public boolean hasAnyServiceRunning() {
        return isDingTalkRunning() || isTelegramRunning();
    }

    /**
     * 停止所有服务
     */
    public void stopAllServices() {
        AppLog.d(TAG, "Stopping all remote services");
        clearDingTalkService();
        clearTelegramService();
    }

    /**
     * 获取服务状态描述（用于前台服务通知）
     */
    public String getServiceStatusDescription() {
        StringBuilder sb = new StringBuilder();
        if (isDingTalkRunning()) {
            sb.append("钉钉远程服务运行中");
        }
        if (isTelegramRunning()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("Telegram 远程服务运行中");
        }
        if (sb.length() == 0) {
            sb.append("远程服务运行中");
        }
        return sb.toString();
    }
}
