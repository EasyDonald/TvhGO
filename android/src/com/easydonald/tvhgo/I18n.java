package com.easydonald.tvhgo;

import java.util.HashMap;
import java.util.Map;

/**
 * 极简多语言：以简体中文原文为 key，查找 繁中 / English / 日本語 译文。
 * 找不到译文时回退显示中文，保证不会出现空串。
 */
public class I18n {

    public static final int ZH_CN = 0, ZH_TW = 1, EN = 2, JA = 3;

    private static int lang = ZH_CN;

    private static final Map<String, String[]> DICT = new HashMap<String, String[]>();

    private static void add(String zh, String tw, String en, String ja) {
        DICT.put(zh, new String[]{tw, en, ja});
    }

    static {
        // 标签页 / 标题
        add("频道", "頻道", "Channels", "チャンネル");
        add("状态", "狀態", "Status", "ステータス");
        add("录制", "錄影", "Recordings", "録画");
        add("设置", "設定", "Settings", "設定");
        add("TVH 客户端", "TvhGO", "TvhGO", "TvhGO");
        add("刷新", "重新整理", "Refresh", "更新");
        // 频道页
        add("搜索频道（名称或频道号）", "搜尋頻道（名稱或頻道號）", "Search by name or number", "名前・番号で検索");
        add("全部分组", "全部分組", "All groups", "すべてのグループ");
        add("共", "共", "total", "計");
        add("个频道", "個頻道", "channels", "ch");
        add("显示", "顯示", "shown", "表示");
        // 状态页
        add("服务器", "伺服器", "Server", "サーバー");
        add("名称", "名稱", "Name", "名前");
        add("地址", "位址", "Address", "アドレス");
        add("频道 / 分组", "頻道 / 分組", "Channels / Groups", "チャンネル / グループ");
        add("事件连接", "事件連線", "Connections", "接続数");
        add("每 1 秒自动刷新", "每 1 秒自動重新整理", "Auto refresh every 1s", "1秒ごとに自動更新");
        add("当前订阅（", "目前訂閱（", "Subscriptions (", "現在の購読（");
        add("暂无客户端在观看", "暫無用戶端觀看", "No active clients", "視聴中のクライアントなし");
        add("输入流（", "輸入流（", "Inputs (", "入力ストリーム（");
        add("当前没有活动的输入流", "目前沒有作用中的輸入流", "No active inputs", "アクティブな入力なし");
        add("订阅", "訂閱", "Subs", "購読");
        add("信号", "訊號", "Signal", "信号");
        add("入", "入", "in", "in");
        add("出", "出", "out", "out");
        add("正在读取状态…", "正在讀取狀態…", "Loading status…", "状態を読み込み中…");
        add("读取失败：", "讀取失敗：", "Failed: ", "失敗: ");
        // EPG
        add("拖动浏览，点节目可播放/录制", "拖曳瀏覽，點節目可播放/錄影", "Drag to browse, tap to play/record", "ドラッグで閲覧、タップで再生/録画");
        add("正在读取 EPG…", "正在讀取 EPG…", "Loading EPG…", "EPG を読み込み中…");
        add("节目", "節目", "events", "番組");
        add("个频道 ·", "個頻道 ·", "channels ·", "ch ·");
        add("EPG 读取失败：", "EPG 讀取失敗：", "EPG failed: ", "EPG 失敗: ");
        add("播放该频道", "播放該頻道", "Play this channel", "このchを再生");
        add("录制此节目", "錄製此節目", "Record this programme", "この番組を録画");
        add("查看详情", "查看詳情", "Details", "詳細");
        add("节目详情", "節目詳情", "Programme details", "番組詳細");
        add("关闭", "關閉", "Close", "閉じる");
        add("该节目已预约录制", "該節目已預約錄影", "Already scheduled", "録画予約済み");
        add("已预约录制：", "已預約錄影：", "Scheduled: ", "録画予約: ");
        add("录制失败：", "錄影失敗：", "Record failed: ", "録画失敗: ");
        add("正在播", "正在播", "Live", "放送中");
        add("今天", "今天", "Today", "今日");
        add("明天", "明天", "Tomorrow", "明日");
        // 录制页
        add("待录制 / 录制中", "待錄影 / 錄影中", "Upcoming / Recording", "予約 / 録画中");
        add("已完成", "已完成", "Finished", "完了");
        add("新建录制", "新建錄影", "New recording", "新規録画");
        add("正在读取录制任务…", "正在讀取錄影任務…", "Loading recordings…", "録画一覧を読み込み中…");
        add("频道", "頻道", "Channel", "チャンネル");
        add("标题", "標題", "Title", "タイトル");
        add("开始时间 (yyyy-MM-dd HH:mm)", "開始時間 (yyyy-MM-dd HH:mm)", "Start (yyyy-MM-dd HH:mm)", "開始 (yyyy-MM-dd HH:mm)");
        add("结束时间 (yyyy-MM-dd HH:mm)", "結束時間 (yyyy-MM-dd HH:mm)", "Stop (yyyy-MM-dd HH:mm)", "終了 (yyyy-MM-dd HH:mm)");
        add("优先级 (pri)", "優先級 (pri)", "Priority (pri)", "優先度 (pri)");
        add("提前开始（分钟）", "提前開始（分鐘）", "Start extra (min)", "開始前 (分)");
        add("延后结束（分钟）", "延後結束（分鐘）", "Stop extra (min)", "終了後 (分)");
        add("录制配置", "錄影設定", "Recording config", "録画設定");
        add("创建", "建立", "Create", "作成");
        add("取消", "取消", "Cancel", "キャンセル");
        add("保存", "儲存", "Save", "保存");
        add("取消录制", "取消錄影", "Cancel recording", "録画中止");
        add("停止录制", "停止錄影", "Stop recording", "録画停止");
        add("删除条目", "刪除條目", "Delete entry", "項目を削除");
        add("删除录制", "刪除錄影", "Delete recording", "録画を削除");
        add("用内置播放器播放", "用內建播放器播放", "Play in built-in player", "内蔵プレーヤーで再生");
        add("用 VLC 播放", "用 VLC 播放", "Play in VLC", "VLC で再生");
        add("已创建录制：", "已建立錄影：", "Created: ", "作成しました: ");
        add("创建失败：", "建立失敗：", "Create failed: ", "作成失敗: ");
        add("操作完成", "操作完成", "Done", "完了");
        add("操作失败：", "操作失敗：", "Failed: ", "失敗: ");
        // 设置页
        add("切换服务器", "切換伺服器", "Switch server", "サーバー切替");
        add("编辑当前服务器", "編輯目前伺服器", "Edit current server", "現在のサーバーを編集");
        add("新增服务器", "新增伺服器", "Add server", "サーバーを追加");
        add("播放方式", "播放方式", "Playback", "再生方法");
        add("内置播放器（不依赖 VLC）", "內建播放器（不需 VLC）", "Built-in player (no VLC)", "内蔵プレーヤー（VLC不要）");
        add("VLC / 系统播放器", "VLC / 系統播放器", "VLC / system player", "VLC / システム");
        add("拉流 profile", "串流 profile", "Stream profile", "ストリーム profile");
        add("VLC 包名", "VLC 套件名稱", "VLC package", "VLC パッケージ");
        add("频道列表", "頻道列表", "Channel list", "チャンネル一覧");
        add("左侧显示", "左側顯示", "Left column", "左側表示");
        add("频道号", "頻道號", "Channel number", "チャンネル番号");
        add("频道图标", "頻道圖示", "Channel icon", "チャンネルアイコン");
        add("副标题显示", "副標題顯示", "Subtitle shows", "サブタイトル表示");
        add("当前节目", "當前節目", "Now playing", "放送中の番組");
        add("分组", "分組", "Group", "グループ");
        add("其它", "其它", "Misc", "その他");
        add("打开 Tvheadend 网页后台", "開啟 Tvheadend 網頁後台", "Open Tvheadend web UI", "Tvheadend Web UI を開く");
        add("关于", "關於", "About", "情報");
        add("界面语言", "介面語言", "Language", "言語");
        add("简体中文", "简体中文", "Simplified Chinese", "簡体字中国語");
        add("繁体中文", "繁體中文", "Traditional Chinese", "繁体字中国語");
        add("英语", "英語", "English", "英語");
        add("日语", "日語", "Japanese", "日本語");
        add("已切换到配置：", "已切換到設定：", "Switched to ", "切替しました: ");
        add("已保存配置：", "已儲存設定：", "Saved ", "保存しました: ");
        add("已删除，当前配置：", "已刪除，目前設定：", "Deleted, current: ", "削除しました: ");
        add("至少要保留一套配置", "至少要保留一組設定", "Keep at least one profile", "設定は最低1つ必要です");
        add("删除配置", "刪除設定", "Delete profile", "設定を削除");
        add("删除", "刪除", "Delete", "削除");
        add("新增配置", "新增設定", "Add profile", "設定を追加");
        add("编辑配置", "編輯設定", "Edit profile", "設定を編集");
        add("服务器配置", "伺服器設定", "Server profiles", "サーバー設定");
        add("正在连接 ", "正在連線 ", "Connecting ", "接続中 ");
        add("连接失败：", "連線失敗：", "Connect failed: ", "接続失敗: ");
        add("频道列表还没加载完", "頻道列表尚未載入完成", "Channels not loaded yet", "チャンネル未読込");
        add("服务器上没有可用的录制配置，请先在网页版 DVR 里建一个", "伺服器上沒有可用的錄影設定，請先在網頁版 DVR 建立",
                "No recording config on server; create one in the web UI DVR", "録画設定がありません。Web UI で作成してください");
        add("没有可用的播放器，请先安装 VLC", "沒有可用的播放器，請先安裝 VLC", "No player found; install VLC", "プレーヤーがありません。VLC を導入してください");
        add("未找到 VLC，改用系统选择器", "找不到 VLC，改用系統選擇", "VLC not found; using system chooser", "VLC が見つかりません");
        add("已复制", "已複製", "Copied", "コピーしました");
        add("该频道已禁用，先启用再播放", "該頻道已停用，請先啟用再播放", "Channel disabled", "チャンネルは無効です");
        add("播放（默认方式）", "播放（預設方式）", "Play (default)", "再生（既定）");
        add("复制串流链接（含账号密码）", "複製串流連結（含帳密）", "Copy stream URL (with credentials)", "ストリームURLをコピー");
        add("用内置播放器播放", "用內建播放器播放", "Built-in player", "内蔵プレーヤー");
        add("查看节目单 (EPG)", "查看節目表 (EPG)", "EPG", "番組表 (EPG)");
        add("编辑频道", "編輯頻道", "Edit channel", "チャンネル編集");
        add("禁用频道", "停用頻道", "Disable channel", "チャンネルを無効化");
        add("启用频道", "啟用頻道", "Enable channel", "チャンネルを有効化");
        add("复制频道 ID", "複製頻道 ID", "Copy channel ID", "チャンネルIDをコピー");
        add("串流链接", "串流連結", "Stream URL", "ストリームURL");
        add("复制链接", "複製連結", "Copy URL", "URLをコピー");
        add("编辑频道", "編輯頻道", "Edit channel", "チャンネル編集");
        add("启用该频道", "啟用該頻道", "Enable channel", "チャンネルを有効化");
        add("已保存", "已儲存", "Saved", "保存しました");
        add("保存失败：", "儲存失敗：", "Save failed: ", "保存失敗: ");
        add("已启用 ", "已啟用 ", "Enabled ", "有効化: ");
        add("已禁用 ", "已停用 ", "Disabled ", "無効化: ");
        add("首次使用：填写服务器", "首次使用：填寫伺服器", "First run: enter server", "初回: サーバー入力");
        add("服务器地址", "伺服器位址", "Server URL", "サーバーURL");
        add("用户名", "使用者名稱", "Username", "ユーザー名");
        add("密码", "密碼", "Password", "パスワード");
        add("转码配置 (profile)", "轉碼設定 (profile)", "Transcode profile", "トランスコード profile");
        add("播放器包名（VLC）", "播放器套件名稱（VLC）", "Player package (VLC)", "プレーヤーパッケージ");
        add("优先用 VLC 直接播放", "優先使用 VLC 播放", "Prefer VLC", "VLC を優先");
        add("使用内置播放器（不依赖 VLC）", "使用內建播放器（不需 VLC）", "Use built-in player", "内蔵プレーヤーを使う");
        add("正在读取节目单…", "正在讀取節目表…", "Loading guide…", "番組表を読み込み中…");
        add("个节目", "個節目", "events", "番組");
        add("点节目可录制/播放", "點節目可錄影/播放", "tap to record/play", "タップで録画/再生");
        add("全部频道", "全部頻道", "All channels", "すべて");
        add("主题", "主題", "Theme", "テーマ");
        add("跟随系统", "跟隨系統", "Follow system", "システムに従う");
        add("深色", "深色", "Dark", "ダーク");
        add("浅色", "淺色", "Light", "ライト");
        add("下载到手机", "下載到手機", "Download", "ダウンロード");
        add("已开始下载", "已開始下載", "Download started", "ダウンロード開始");
        add("下载失败：", "下載失敗：", "Download failed: ", "ダウンロード失敗: ");
        add("占用", "佔用", "used", "使用量");
        add("条", "條", "items", "件");
        add("订阅详情", "訂閱詳情", "Subscription", "購読の詳細");
        add("该用户当前有", "該使用者目前有", "This user has", "このユーザーの接続数");
        add("条连接", "條連線", "connection(s)", "件");
        add("断开该用户", "中斷該使用者", "Drop user's connections", "このユーザーを切断");
        add("断开全部连接", "中斷全部連線", "Drop all connections", "すべて切断");
        add("待录制", "待錄影", "Upcoming", "予約");
        add("已完成", "已完成", "Finished", "完了");
        add("失败", "失敗", "Failed", "失敗");
        add("已移除", "已移除", "Removed", "削除済み");
        add("自动录制", "自動錄影", "Autorec", "自動録画");
        add("定时录制", "定時錄影", "Timerec", "時刻録画");
        add("重新录制", "重新錄影", "Re-record", "再録画");
        add("条规则", "條規則", "rules", "件のルール");
        add("条", "條", "items", "件");
        add("占用", "佔用", "used", "使用量");
        add("启用规则", "啟用規則", "Enable rule", "ルールを有効化");
        add("停用规则", "停用規則", "Disable rule", "ルールを無効化");
        add("删除规则", "刪除規則", "Delete rule", "ルールを削除");
        add("已删除规则", "已刪除規則", "Rule deleted", "ルールを削除しました");
        add("已启用", "已啟用", "Enabled", "有効化しました");
        add("已停用", "已停用", "Disabled", "無効化しました");
        add("启用", "啟用", "Enabled", "有効");
        add("停用", "停用", "Disabled", "無効");
        add("星期", "星期", "Days", "曜日");
        add("备注", "備註", "Comment", "コメント");
        add("状态", "狀態", "State", "状態");
        add("已安排重新录制", "已安排重新錄影", "Re-record scheduled", "再録画を予約しました");
        add("正在读取…", "正在讀取…", "Loading…", "読み込み中…");
        add("错误", "錯誤", "errors", "エラー");
        add("已断开", "已中斷", "Dropped", "切断しました");
        add("失败", "失敗", "failed", "失敗");
        add("确定", "確定", "OK", "OK");
        add("已断开全部连接", "已中斷全部連線", "All connections dropped", "すべて切断しました");
        add("断开失败：", "中斷失敗：", "Drop failed: ", "切断失敗: ");
        add("断开所有客户端与 Tvheadend 的连接（包括你自己的设备），确定？",
            "中斷所有用戶端與 Tvheadend 的連線（包含你自己的裝置），確定？",
            "Drop every client connection to Tvheadend, including your own devices. Continue?",
            "Tvheadend への全接続を切断します（自分の端末も含む）。よろしいですか？");
        add("用户", "使用者", "User", "ユーザー");
        add("客户端", "用戶端", "Client", "クライアント");
        add("服务", "服務", "Service", "サービス");
        add("码率", "碼率", "Bitrate", "ビットレート");
        add("该 Tvheadend 版本未提供断开订阅的接口（实测多种 API 均无此功能，禁用频道也不会断流）。如需强制断开，请在网页后台停用该客户端账号，或在该客户端侧停止播放。",
            "此 Tvheadend 版本未提供中斷訂閱的介面（實測多種 API 皆無此功能，停用頻道也不會斷流）。如需強制中斷，請在網頁後台停用該用戶端帳號，或在該用戶端停止播放。",
            "This Tvheadend build exposes no API to drop a subscription (verified: several endpoints 404; disabling the channel does not drop it). Disable the client account in the web UI or stop playback on the client side.",
            "この Tvheadend には購読を切断する API がありません（複数エンドポイントを検証済み）。Web UI で該当アカウントを無効化するか、クライアント側で停止してください。");
        add("默认串流配置", "預設串流設定", "Default stream profile", "既定プロファイル");
        add("串流配置", "串流設定", "Stream profile", "プロファイル");
        add("跟随默认", "跟隨預設", "Follow default", "既定に従う");
        add("退出", "離開", "Exit", "終了");
        add("确定要退出应用吗？", "確定要離開應用程式嗎？", "Exit the app?", "アプリを終了しますか？");
        add("开始时间", "開始時間", "Start time", "開始");
        add("结束时间", "結束時間", "Stop time", "終了");
        add("播放", "播放", "Play", "再生");
        add("复制串流链接", "複製串流連結", "Copy stream URL", "URLをコピー");
        add("选择播放器", "選擇播放器", "Choose player", "プレーヤー選択");
        add("录制标题（可留空）", "錄影標題（可留空）", "Title (optional)", "タイトル（任意）");
        add("读取 profile 失败，请检查权限", "讀取 profile 失敗，請檢查權限", "Failed to load profiles", "プロファイル取得失敗");
    }

    public static void setLang(int l) {
        lang = (l < 0 || l > 3) ? ZH_CN : l;
    }

    public static int lang() {
        return lang;
    }

    public static String tr(String zh) {
        if (lang == ZH_CN || zh == null) return zh;
        String[] row = DICT.get(zh);
        if (row == null) return zh;
        String s = row[lang - 1];
        return (s == null || s.isEmpty()) ? zh : s;
    }
}
