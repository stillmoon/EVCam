package com.kooo.evcam.telegram;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.WakeUpHelper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Telegram Bot 消息轮询管理器
 * 使用 Long Polling 方式接收消息
 */
public class TelegramBotManager {
    private static final String TAG = "TelegramBotManager";
    private static final int POLL_TIMEOUT = 30; // 长轮询超时时间（秒）
    private static final int POLL_LIMIT = 5; // 每次拉取的消息数量限制
    private static final int MESSAGE_EXPIRE_SECONDS = 600; // 消息过期时间（10分钟 = 600秒）
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 5000; // 5秒

    private final Context context;
    private final TelegramConfig config;
    private final TelegramApiClient apiClient;
    private final ConnectionCallback connectionCallback;
    private final Handler mainHandler;

    private volatile boolean isRunning = false;
    private volatile boolean shouldStop = false;
    private Thread pollingThread;
    private int reconnectAttempts = 0;
    private CommandCallback currentCommandCallback;

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public interface CommandCallback {
        void onRecordCommand(long chatId, int durationSeconds);
        void onPhotoCommand(long chatId);
    }

    public TelegramBotManager(Context context, TelegramConfig config,
                               TelegramApiClient apiClient, ConnectionCallback callback) {
        this.context = context;
        this.config = config;
        this.apiClient = apiClient;
        this.connectionCallback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 启动消息轮询
     */
    public void start(CommandCallback commandCallback) {
        if (isRunning) {
            AppLog.w(TAG, "Bot 已在运行");
            return;
        }

        this.currentCommandCallback = commandCallback;
        this.shouldStop = false;
        this.reconnectAttempts = 0;

        startPolling();
    }

    /**
     * 内部方法：启动轮询线程
     */
    private void startPolling() {
        pollingThread = new Thread(() -> {
            try {
                AppLog.d(TAG, "正在验证 Bot Token...");

                // 验证 Token
                JsonObject botInfo = apiClient.getMe();
                String botUsername = botInfo.get("username").getAsString();
                AppLog.d(TAG, "Bot 验证成功: @" + botUsername);

                isRunning = true;
                reconnectAttempts = 0;

                // 通知连接成功
                mainHandler.post(() -> connectionCallback.onConnected());

                // 开始长轮询
                long offset = config.getLastUpdateId() + 1;

                while (!shouldStop) {
                    try {
                        JsonArray updates = apiClient.getUpdates(offset, POLL_TIMEOUT, POLL_LIMIT);
                        long currentTime = System.currentTimeMillis() / 1000; // 当前时间（秒）

                        for (int i = 0; i < updates.size(); i++) {
                            JsonObject update = updates.get(i).getAsJsonObject();
                            long updateId = update.get("update_id").getAsLong();

                            // 处理消息
                            if (update.has("message")) {
                                JsonObject message = update.getAsJsonObject("message");

                                // 检查消息时间，忽略超过 10 分钟的旧消息
                                if (message.has("date")) {
                                    long messageTime = message.get("date").getAsLong();
                                    long messageAge = currentTime - messageTime;

                                    if (messageAge > MESSAGE_EXPIRE_SECONDS) {
                                        AppLog.d(TAG, "忽略过期消息，消息时间: " + messageTime +
                                                ", 已过去 " + messageAge + " 秒");
                                        // 仍然更新 offset，避免重复拉取
                                        offset = updateId + 1;
                                        config.saveLastUpdateId(updateId);
                                        continue;
                                    }
                                }

                                processMessage(message);
                            }

                            // 更新 offset
                            offset = updateId + 1;
                            config.saveLastUpdateId(updateId);
                        }

                    } catch (Exception e) {
                        if (!shouldStop) {
                            AppLog.e(TAG, "轮询出错: " + e.getMessage());
                            // 短暂休眠后继续
                            Thread.sleep(1000);
                        }
                    }
                }

            } catch (Exception e) {
                AppLog.e(TAG, "启动 Bot 失败", e);
                isRunning = false;

                // 尝试重连
                if (!shouldStop && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    AppLog.d(TAG, "将在 " + RECONNECT_DELAY_MS + "ms 后尝试第 " + reconnectAttempts + " 次重连");
                    mainHandler.postDelayed(() -> {
                        if (!shouldStop) {
                            startPolling();
                        }
                    }, RECONNECT_DELAY_MS);
                } else {
                    mainHandler.post(() -> connectionCallback.onError("启动失败: " + e.getMessage()));
                }
            }

            isRunning = false;
            if (shouldStop) {
                mainHandler.post(() -> connectionCallback.onDisconnected());
            }
        });

        pollingThread.setName("TelegramPolling");
        pollingThread.start();
    }

    /**
     * 处理收到的消息
     */
    private void processMessage(JsonObject message) {
        try {
            // 获取 chat 信息
            JsonObject chat = message.getAsJsonObject("chat");
            long chatId = chat.get("id").getAsLong();
            String chatType = chat.get("type").getAsString(); // private, group, supergroup, channel

            // 检查是否允许此 chat
            if (!config.isChatIdAllowed(chatId)) {
                AppLog.d(TAG, "Chat ID 不在白名单中: " + chatId);
                return;
            }

            // 获取消息文本
            if (!message.has("text")) {
                return; // 非文本消息，忽略
            }

            String text = message.get("text").getAsString();
            AppLog.d(TAG, "收到消息 - chatId: " + chatId + ", type: " + chatType + ", text: " + text);

            // 解析指令
            String command = parseCommand(text);
            AppLog.d(TAG, "解析的指令: " + command);

            // 处理指令
            if (command.startsWith("/record") || command.startsWith("录制") ||
                command.toLowerCase().startsWith("record")) {

                int durationSeconds = parseRecordDuration(command);
                AppLog.d(TAG, "收到录制指令，时长: " + durationSeconds + " 秒");

                // 发送确认消息
                String confirmMsg = String.format("收到录制指令，开始录制 %d 秒视频...", durationSeconds);
                sendResponseAndThen(chatId, confirmMsg, () -> {
                    // 使用 WakeUpHelper 唤醒并启动录制
                    AppLog.d(TAG, "使用 WakeUpHelper 启动录制...");
                    WakeUpHelper.launchForRecordingTelegram(context, chatId, durationSeconds);
                });

            } else if ("/photo".equals(command) || "拍照".equals(command) ||
                       "photo".equalsIgnoreCase(command)) {

                AppLog.d(TAG, "收到拍照指令");

                // 发送确认消息
                sendResponseAndThen(chatId, "收到拍照指令，正在拍照...", () -> {
                    // 使用 WakeUpHelper 唤醒并启动拍照
                    AppLog.d(TAG, "使用 WakeUpHelper 启动拍照...");
                    WakeUpHelper.launchForPhotoTelegram(context, chatId);
                });

            } else if ("/help".equals(command) || "帮助".equals(command) ||
                       "/start".equals(command)) {

                apiClient.sendMessage(chatId,
                    "<b>可用指令：</b>\n" +
                    "• /record - 开始录制 60 秒视频（默认）\n" +
                    "• /record 30 - 录制指定秒数视频\n" +
                    "• 录制 或 录制30 - 中文指令\n" +
                    "• /photo - 拍摄照片\n" +
                    "• 拍照 - 中文指令\n" +
                    "• /help - 显示此帮助信息");

            } else if ("/status".equals(command) || "状态".equals(command)) {
                apiClient.sendMessage(chatId, "✅ Bot 正在运行中");

            } else {
                AppLog.d(TAG, "未识别的指令: " + command);
                apiClient.sendMessage(chatId,
                    "未识别的指令。发送 /help 查看可用指令。");
            }

        } catch (Exception e) {
            AppLog.e(TAG, "处理消息失败", e);
        }
    }

    /**
     * 解析指令文本
     * 移除 @ 机器人名称部分
     */
    private String parseCommand(String text) {
        if (text == null) {
            return "";
        }

        // 移除 @botname 部分
        String command = text.replaceAll("@\\S+", "").trim();
        return command;
    }

    /**
     * 解析录制时长（秒）
     * 支持格式：/record、/record 30、录制、录制30、录制 30
     */
    private int parseRecordDuration(String command) {
        if (command == null || command.isEmpty()) {
            return 60;
        }

        // 移除指令关键字，提取数字
        String durationStr = command
                .replaceAll("(?i)(/record|录制|record)", "")
                .trim();

        if (durationStr.isEmpty()) {
            return 60; // 默认 1 分钟
        }

        try {
            int duration = Integer.parseInt(durationStr);
            // 限制范围：最少 5 秒，最多 600 秒（10分钟）
            if (duration < 5) {
                return 5;
            } else if (duration > 600) {
                return 600;
            }
            return duration;
        } catch (NumberFormatException e) {
            AppLog.w(TAG, "无法解析录制时长: " + durationStr + "，使用默认值 60 秒");
            return 60;
        }
    }

    /**
     * 发送响应消息，并在发送完成后执行回调
     */
    private void sendResponseAndThen(long chatId, String message, Runnable callback) {
        new Thread(() -> {
            try {
                apiClient.sendMessage(chatId, message);
                AppLog.d(TAG, "响应消息已发送: " + message);

                if (callback != null) {
                    callback.run();
                }
            } catch (Exception e) {
                AppLog.e(TAG, "发送响应消息失败", e);
                // 即使发送失败，也执行回调
                if (callback != null) {
                    callback.run();
                }
            }
        }).start();
    }

    /**
     * 停止消息轮询
     */
    public void stop() {
        AppLog.d(TAG, "正在停止 Bot...");
        shouldStop = true;

        if (pollingThread != null) {
            pollingThread.interrupt();
        }

        isRunning = false;
        AppLog.d(TAG, "Bot 已停止");
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
}
