using System;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

namespace AutoPrintAgent
{
    /// <summary>
    /// 代理运行配置。保存到 exe 同目录的 config.json，供配置界面读写。
    /// </summary>
    public class AppConfig
    {
        public string ServerBaseUrl { get; set; } = "http://localhost/api";
        public string ApiKey { get; set; } = "CHANGE_ME_EXTERNAL_API_KEY";
        public string PrinterName { get; set; } = "";
        public string FilterPrinterName { get; set; } = "";
        public int PollIntervalSeconds { get; set; } = 3;
        public int BatchLimit { get; set; } = 10;
        public int MaxRetry { get; set; } = 3;

        public bool ScheduleEnabled { get; set; } = false;
        public string ScheduleStart { get; set; } = "08:00";
        public string ScheduleEnd { get; set; } = "20:00";

        public bool AutoStartPolling { get; set; } = true;

        private static readonly JavaScriptSerializer Serializer = new JavaScriptSerializer();

        public static string ConfigPath =>
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "config.json");

        public static AppConfig Load()
        {
            try
            {
                if (File.Exists(ConfigPath))
                {
                    var json = File.ReadAllText(ConfigPath, Encoding.UTF8);
                    var cfg = Serializer.Deserialize<AppConfig>(json);
                    if (cfg != null) return cfg.Normalize();
                }
            }
            catch (Exception ex)
            {
                Logger.Warn("读取配置失败，使用默认配置：" + ex.Message);
            }
            return new AppConfig();
        }

        public void Save()
        {
            try
            {
                var json = Serializer.Serialize(Normalize());
                File.WriteAllText(ConfigPath, json, new UTF8Encoding(false));
            }
            catch (Exception ex)
            {
                Logger.Error("保存配置失败：" + ex.Message);
                throw;
            }
        }

        public AppConfig Normalize()
        {
            if (PollIntervalSeconds < 1) PollIntervalSeconds = 1;
            if (BatchLimit < 1) BatchLimit = 1;
            if (BatchLimit > 50) BatchLimit = 50;
            if (MaxRetry < 0) MaxRetry = 0;
            if (string.IsNullOrWhiteSpace(ServerBaseUrl)) ServerBaseUrl = "http://localhost/api";
            ServerBaseUrl = ServerBaseUrl.Trim().TrimEnd('/');
            return this;
        }

        /// <summary>当前时间是否在允许打印的时间段内（未开启定时则始终允许）。</summary>
        public bool IsWithinSchedule(DateTime now)
        {
            if (!ScheduleEnabled) return true;
            if (!TimeSpan.TryParse(ScheduleStart, out var start)) start = TimeSpan.Zero;
            if (!TimeSpan.TryParse(ScheduleEnd, out var end)) end = new TimeSpan(23, 59, 59);
            var t = now.TimeOfDay;
            if (start <= end) return t >= start && t <= end;
            // 跨零点，例如 22:00 - 06:00
            return t >= start || t <= end;
        }
    }
}
