package com.kooo.evcam.telegram;

import com.kooo.evcam.AppLog;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Telegram 图片上传服务
 * 负责将拍摄的照片上传到 Telegram
 */
public class TelegramPhotoUploadService {
    private static final String TAG = "TelegramPhotoUpload";

    private final Context context;
    private final TelegramApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public TelegramPhotoUploadService(Context context, TelegramApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 上传图片文件到 Telegram
     * @param photoFiles 图片文件列表
     * @param chatId Telegram Chat ID
     * @param callback 上传回调
     */
    public void uploadPhotos(List<File> photoFiles, long chatId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (photoFiles == null || photoFiles.isEmpty()) {
                    callback.onError("没有图片文件可上传");
                    return;
                }

                callback.onProgress("开始上传 " + photoFiles.size() + " 张照片...");

                // 发送 "正在上传照片" 状态
                apiClient.sendChatAction(chatId, "upload_photo");

                List<String> uploadedFiles = new ArrayList<>();

                for (int i = 0; i < photoFiles.size(); i++) {
                    File photoFile = photoFiles.get(i);

                    if (!photoFile.exists()) {
                        AppLog.w(TAG, "图片文件不存在: " + photoFile.getPath());
                        continue;
                    }

                    callback.onProgress("正在上传 (" + (i + 1) + "/" + photoFiles.size() + "): " + photoFile.getName());

                    try {
                        // 发送 "正在上传照片" 状态
                        apiClient.sendChatAction(chatId, "upload_photo");

                        // 直接上传并发送图片
                        String caption = "照片 " + (i + 1) + "/" + photoFiles.size();
                        apiClient.sendPhoto(chatId, photoFile, caption);

                        uploadedFiles.add(photoFile.getName());
                        AppLog.d(TAG, "图片上传成功: " + photoFile.getName());

                        // 延迟2秒后再上传下一张照片
                        if (i < photoFiles.size() - 1) {
                            callback.onProgress("等待2秒后上传下一张照片...");
                            Thread.sleep(2000);
                        }

                    } catch (Exception e) {
                        AppLog.e(TAG, "上传图片失败: " + photoFile.getName(), e);
                        callback.onError("上传失败: " + photoFile.getName() + " - " + e.getMessage());
                    }
                }

                if (uploadedFiles.isEmpty()) {
                    callback.onError("所有图片上传失败");
                } else {
                    String successMessage = "✅ 图片上传完成！共上传 " + uploadedFiles.size() + " 张照片";
                    callback.onSuccess(successMessage);

                    // 发送完成消息
                    apiClient.sendMessage(chatId, successMessage);
                }

            } catch (Exception e) {
                AppLog.e(TAG, "上传过程出错", e);
                callback.onError("上传过程出错: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 上传单张图片
     */
    public void uploadPhoto(File photoFile, long chatId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(photoFile);
        uploadPhotos(files, chatId, callback);
    }
}
