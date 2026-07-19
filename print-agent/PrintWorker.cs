using System;
using System.Collections.Generic;
using System.Threading;

namespace AutoPrintAgent
{
    /// <summary>
    /// 轮询打印工作线程：定时领取待打印作业 → RAW 发 ZPL 到打印机 → 回传结果。
    /// 支持启停、定时时段限制、失败重试。所有异常都被捕获，保证线程不崩。
    /// </summary>
    public class PrintWorker
    {
        private readonly AppConfig _cfg;
        private ApiClient _client;
        private Thread _thread;
        private volatile bool _running;

        private readonly Dictionary<string, int> _retryCount = new Dictionary<string, int>();

        public event Action<string> OnStatus;
        public bool IsRunning => _running;

        public PrintWorker(AppConfig cfg)
        {
            _cfg = cfg;
            _client = new ApiClient(cfg);
        }

        public void ReloadConfig()
        {
            _client = new ApiClient(_cfg);
        }

        public void Start()
        {
            if (_running) return;
            _running = true;
            _thread = new Thread(Loop) { IsBackground = true, Name = "PrintWorker" };
            _thread.Start();
            Logger.Info("轮询已启动");
            OnStatus?.Invoke("运行中");
        }

        public void Stop()
        {
            if (!_running) return;
            _running = false;
            Logger.Info("轮询已停止");
            OnStatus?.Invoke("已停止");
        }

        private void Loop()
        {
            while (_running)
            {
                try
                {
                    if (_cfg.IsWithinSchedule(DateTime.Now))
                    {
                        ProcessOnce();
                    }
                }
                catch (Exception ex)
                {
                    Logger.Warn("本轮轮询异常：" + ex.Message);
                    OnStatus?.Invoke("网络/接口异常，重试中");
                }

                for (int i = 0; i < _cfg.PollIntervalSeconds * 10 && _running; i++)
                    Thread.Sleep(100);
            }
        }

        /// <summary>执行一轮：领取 → 逐条打印 → 回传。供界面“立即打印一次”复用。</summary>
        public int ProcessOnce()
        {
            var jobs = _client.ClaimNext();
            if (jobs.Count == 0) return 0;

            Logger.Info($"领取到 {jobs.Count} 条待打印作业");
            int ok = 0;
            foreach (var job in jobs)
            {
                if (!_running && !_manualRun) break;
                if (PrintOne(job)) ok++;
            }
            OnStatus?.Invoke($"运行中（刚打印 {ok}/{jobs.Count}）");
            return ok;
        }

        private volatile bool _manualRun;

        public int RunOnceManually()
        {
            _manualRun = true;
            try { return ProcessOnce(); }
            finally { _manualRun = false; }
        }

        private bool PrintOne(PrintJob job)
        {
            var printer = string.IsNullOrWhiteSpace(_cfg.PrinterName)
                ? job.printerName
                : _cfg.PrinterName;

            if (string.IsNullOrWhiteSpace(printer))
            {
                Report(job, false, "未指定打印机名称");
                return false;
            }
            if (string.IsNullOrEmpty(job.zplContent))
            {
                Report(job, false, "作业缺少 ZPL 内容");
                return false;
            }

            try
            {
                RawPrinterHelper.SendZpl(printer, job.zplContent, "Label-" + job.taskNo);
                Report(job, true, "打印成功");
                _retryCount.Remove(job.printJobNo);
                Logger.Info($"打印成功 job={job.printJobNo} task={job.taskNo} printer={printer}");
                return true;
            }
            catch (Exception ex)
            {
                int tries = _retryCount.TryGetValue(job.printJobNo, out var c) ? c + 1 : 1;
                _retryCount[job.printJobNo] = tries;
                Logger.Error($"打印失败 job={job.printJobNo} 第{tries}次：{ex.Message}");

                if (tries >= _cfg.MaxRetry)
                {
                    Report(job, false, $"打印失败（已重试{tries}次）：{ex.Message}");
                    _retryCount.Remove(job.printJobNo);
                }
                // 未达重试上限时不回传 FAILED，作业保持 SENT，超时后会被后端重新分配。
                return false;
            }
        }

        private void Report(PrintJob job, bool printed, string message)
        {
            try
            {
                _client.Callback(job.printJobNo, printed, message);
            }
            catch (Exception ex)
            {
                Logger.Warn($"回传打印结果失败 job={job.printJobNo}：{ex.Message}");
            }
        }
    }
}
