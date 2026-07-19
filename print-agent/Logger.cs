using System;
using System.IO;
using System.Text;

namespace AutoPrintAgent
{
    /// <summary>
    /// 极简本地日志：按天写到 exe 同目录 logs\ 下，同时提供内存回调给界面显示。
    /// </summary>
    public static class Logger
    {
        private static readonly object Lock = new object();
        public static event Action<string> OnLine;

        private static string LogDir =>
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "logs");

        public static void Info(string msg) => Write("INFO", msg);
        public static void Warn(string msg) => Write("WARN", msg);
        public static void Error(string msg) => Write("ERROR", msg);

        private static void Write(string level, string msg)
        {
            var line = $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} [{level}] {msg}";
            try
            {
                lock (Lock)
                {
                    Directory.CreateDirectory(LogDir);
                    var file = Path.Combine(LogDir, $"agent-{DateTime.Now:yyyy-MM-dd}.log");
                    File.AppendAllText(file, line + Environment.NewLine, new UTF8Encoding(false));
                    CleanupOldLogs();
                }
            }
            catch
            {
                // 日志写失败不影响打印主流程
            }
            try { OnLine?.Invoke(line); } catch { }
        }

        private static void CleanupOldLogs()
        {
            try
            {
                var files = Directory.GetFiles(LogDir, "agent-*.log");
                foreach (var f in files)
                {
                    if (File.GetLastWriteTime(f) < DateTime.Now.AddDays(-14))
                        File.Delete(f);
                }
            }
            catch { }
        }
    }
}
