using System;
using System.Drawing;
using System.Windows.Forms;
using Microsoft.Win32;
using System.IO;

namespace AutoPrintAgent
{
    public class MainForm : Form
    {
        private readonly AppConfig _cfg;
        private readonly PrintWorker _worker;

        private NotifyIcon _tray;
        private ContextMenuStrip _trayMenu;

        private TextBox _txtServer;
        private TextBox _txtApiKey;
        private ComboBox _cboPrinter;
        private TextBox _txtFilterPrinter;
        private NumericUpDown _numInterval;
        private NumericUpDown _numBatch;
        private NumericUpDown _numRetry;
        private CheckBox _chkSchedule;
        private DateTimePicker _dtStart;
        private DateTimePicker _dtEnd;
        private CheckBox _chkAutoStart;
        private CheckBox _chkWinStartup;
        private Button _btnStartStop;
        private Button _btnTest;
        private Button _btnSave;
        private Button _btnRunOnce;
        private Label _lblStatus;
        private TextBox _txtLog;

        private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
        private const string RunValueName = "AutoPrintAgent";

        public MainForm()
        {
            _cfg = AppConfig.Load();
            _worker = new PrintWorker(_cfg);
            _worker.OnStatus += s => SafeInvoke(() => _lblStatus.Text = "状态：" + s);
            Logger.OnLine += line => SafeInvoke(() => AppendLog(line));

            BuildUi();
            BuildTray();
            LoadConfigToUi();

            Load += (s, e) =>
            {
                if (_cfg.AutoStartPolling) StartWorker();
            };
        }

        private void AppendLog(string line)
        {
            if (_txtLog.Lines.Length > 500)
                _txtLog.Clear();
            _txtLog.AppendText(line + Environment.NewLine);
        }

        private void SafeInvoke(Action action)
        {
            if (IsDisposed) return;
            if (InvokeRequired) BeginInvoke(action);
            else action();
        }

        private void BuildUi()
        {
            Text = "斑马标签自动打印代理 v1.0";
            Size = new Size(640, 640);
            MinimumSize = new Size(560, 560);
            StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Microsoft YaHei UI", 9f);

            int y = 16;
            int labelX = 18, ctrlX = 150, ctrlW = 440;

            AddLabel("服务器地址", labelX, y + 3);
            _txtServer = new TextBox { Left = ctrlX, Top = y, Width = ctrlW };
            Controls.Add(_txtServer);
            y += 34;

            AddLabel("API 密钥", labelX, y + 3);
            _txtApiKey = new TextBox { Left = ctrlX, Top = y, Width = ctrlW, UseSystemPasswordChar = true };
            Controls.Add(_txtApiKey);
            y += 34;

            AddLabel("打印机", labelX, y + 3);
            _cboPrinter = new ComboBox { Left = ctrlX, Top = y, Width = ctrlW - 90, DropDownStyle = ComboBoxStyle.DropDown };
            Controls.Add(_cboPrinter);
            var btnRefresh = new Button { Left = ctrlX + ctrlW - 84, Top = y - 1, Width = 84, Text = "刷新列表" };
            btnRefresh.Click += (s, e) => LoadPrinters();
            Controls.Add(btnRefresh);
            y += 34;

            AddLabel("仅取此打印机", labelX, y + 3);
            _txtFilterPrinter = new TextBox { Left = ctrlX, Top = y, Width = ctrlW };
            Controls.Add(_txtFilterPrinter);
            AddHint("留空=领取所有待打印作业；填写=只领取该打印机名的作业", ctrlX, y + 30);
            y += 52;

            AddLabel("轮询间隔(秒)", labelX, y + 3);
            _numInterval = new NumericUpDown { Left = ctrlX, Top = y, Width = 70, Minimum = 1, Maximum = 3600, Value = 3 };
            Controls.Add(_numInterval);
            AddLabelW("每次最多领", ctrlX + 82, y + 3, 78);
            _numBatch = new NumericUpDown { Left = ctrlX + 164, Top = y, Width = 70, Minimum = 1, Maximum = 50, Value = 10 };
            Controls.Add(_numBatch);
            AddLabelW("失败重试", ctrlX + 246, y + 3, 62);
            _numRetry = new NumericUpDown { Left = ctrlX + 312, Top = y, Width = 70, Minimum = 0, Maximum = 10, Value = 3 };
            Controls.Add(_numRetry);
            y += 40;

            _chkSchedule = new CheckBox { Left = labelX, Top = y, Width = 130, Text = "启用定时时段" };
            Controls.Add(_chkSchedule);
            _dtStart = new DateTimePicker { Left = ctrlX, Top = y - 2, Width = 90, Format = DateTimePickerFormat.Time, ShowUpDown = true };
            Controls.Add(_dtStart);
            AddLabel("至", ctrlX + 98, y + 3);
            _dtEnd = new DateTimePicker { Left = ctrlX + 120, Top = y - 2, Width = 90, Format = DateTimePickerFormat.Time, ShowUpDown = true };
            Controls.Add(_dtEnd);
            AddHint("仅在该时段内自动打印，跨零点可设 22:00 至 06:00", ctrlX, y + 26);
            y += 48;

            _chkAutoStart = new CheckBox { Left = labelX, Top = y, Width = 260, Text = "程序启动后自动开始轮询" };
            Controls.Add(_chkAutoStart);
            _chkWinStartup = new CheckBox { Left = labelX + 270, Top = y, Width = 260, Text = "开机自动启动本程序" };
            Controls.Add(_chkWinStartup);
            y += 40;

            _btnSave = new Button { Left = labelX, Top = y, Width = 110, Height = 34, Text = "保存设置" };
            _btnSave.Click += (s, e) => OnSave();
            Controls.Add(_btnSave);
            _btnTest = new Button { Left = labelX + 120, Top = y, Width = 110, Height = 34, Text = "测试连接" };
            _btnTest.Click += (s, e) => OnTest();
            Controls.Add(_btnTest);
            _btnRunOnce = new Button { Left = labelX + 240, Top = y, Width = 120, Height = 34, Text = "立即打印一次" };
            _btnRunOnce.Click += (s, e) => OnRunOnce();
            Controls.Add(_btnRunOnce);
            _btnStartStop = new Button { Left = labelX + 370, Top = y, Width = 120, Height = 34, Text = "开始轮询" };
            _btnStartStop.Click += (s, e) => OnStartStop();
            Controls.Add(_btnStartStop);
            y += 44;

            _lblStatus = new Label { Left = labelX, Top = y, Width = 560, Text = "状态：已停止", ForeColor = Color.DimGray };
            Controls.Add(_lblStatus);
            y += 26;

            _txtLog = new TextBox { Left = labelX, Top = y, Width = 588, Height = 180, Multiline = true, ScrollBars = ScrollBars.Vertical, ReadOnly = true, BackColor = Color.FromArgb(30, 30, 30), ForeColor = Color.Gainsboro, Font = new Font("Consolas", 8.5f) };
            _txtLog.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            Controls.Add(_txtLog);

            FormClosing += OnFormClosing;
            LoadPrinters();
        }

        private void AddLabel(string text, int x, int yPos)
        {
            Controls.Add(new Label { Left = x, Top = yPos, Width = 130, Text = text });
        }

        private void AddLabelW(string text, int x, int yPos, int width)
        {
            Controls.Add(new Label { Left = x, Top = yPos, Width = width, Text = text, AutoSize = false });
        }

        private void AddHint(string text, int x, int yPos)
        {
            Controls.Add(new Label { Left = x, Top = yPos, Width = 460, Text = text, ForeColor = Color.Gray, Font = new Font("Microsoft YaHei UI", 8f) });
        }

        private void BuildTray()
        {
            _trayMenu = new ContextMenuStrip();
            _trayMenu.Items.Add("显示主界面", null, (s, e) => ShowMainWindow());
            _trayMenu.Items.Add("开始/停止轮询", null, (s, e) => OnStartStop());
            _trayMenu.Items.Add("立即打印一次", null, (s, e) => OnRunOnce());
            _trayMenu.Items.Add(new ToolStripSeparator());
            _trayMenu.Items.Add("退出", null, (s, e) => ExitApp());

            _tray = new NotifyIcon
            {
                Icon = SystemIcons.Application,
                Text = "斑马标签自动打印代理",
                Visible = true,
                ContextMenuStrip = _trayMenu
            };
            _tray.DoubleClick += (s, e) => ShowMainWindow();
        }

        private void ShowMainWindow()
        {
            Show();
            WindowState = FormWindowState.Normal;
            ShowInTaskbar = true;
            Activate();
        }

        private void ExitApp()
        {
            _worker.Stop();
            if (_tray != null) _tray.Visible = false;
            Application.Exit();
        }

        private void LoadPrinters()
        {
            try
            {
                var current = _cboPrinter.Text;
                _cboPrinter.Items.Clear();
                foreach (string p in System.Drawing.Printing.PrinterSettings.InstalledPrinters)
                    _cboPrinter.Items.Add(p);
                _cboPrinter.Text = current;
            }
            catch (Exception ex)
            {
                Logger.Warn("枚举打印机失败：" + ex.Message);
            }
        }

        private void LoadConfigToUi()
        {
            _txtServer.Text = _cfg.ServerBaseUrl;
            _txtApiKey.Text = _cfg.ApiKey;
            _cboPrinter.Text = _cfg.PrinterName;
            _txtFilterPrinter.Text = _cfg.FilterPrinterName;
            _numInterval.Value = Clamp(_cfg.PollIntervalSeconds, 1, 3600);
            _numBatch.Value = Clamp(_cfg.BatchLimit, 1, 50);
            _numRetry.Value = Clamp(_cfg.MaxRetry, 0, 10);
            _chkSchedule.Checked = _cfg.ScheduleEnabled;
            _dtStart.Value = ParseTime(_cfg.ScheduleStart, 8, 0);
            _dtEnd.Value = ParseTime(_cfg.ScheduleEnd, 20, 0);
            _chkAutoStart.Checked = _cfg.AutoStartPolling;
            _chkWinStartup.Checked = IsWinStartupEnabled();
        }

        private void CollectUiToConfig()
        {
            _cfg.ServerBaseUrl = _txtServer.Text.Trim();
            _cfg.ApiKey = _txtApiKey.Text.Trim();
            _cfg.PrinterName = _cboPrinter.Text.Trim();
            _cfg.FilterPrinterName = _txtFilterPrinter.Text.Trim();
            _cfg.PollIntervalSeconds = (int)_numInterval.Value;
            _cfg.BatchLimit = (int)_numBatch.Value;
            _cfg.MaxRetry = (int)_numRetry.Value;
            _cfg.ScheduleEnabled = _chkSchedule.Checked;
            _cfg.ScheduleStart = _dtStart.Value.ToString("HH:mm");
            _cfg.ScheduleEnd = _dtEnd.Value.ToString("HH:mm");
            _cfg.AutoStartPolling = _chkAutoStart.Checked;
            _cfg.Normalize();
        }

        private void OnSave()
        {
            try
            {
                CollectUiToConfig();
                _cfg.Save();
                _worker.ReloadConfig();
                SetWinStartup(_chkWinStartup.Checked);
                Logger.Info("设置已保存");
                MessageBox.Show("设置已保存并生效。", "提示", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (Exception ex)
            {
                MessageBox.Show("保存失败：" + ex.Message, "错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void OnTest()
        {
            CollectUiToConfig();
            _worker.ReloadConfig();
            var client = new ApiClient(_cfg);
            if (client.TestConnection(out var msg))
                MessageBox.Show(msg, "测试连接", MessageBoxButtons.OK, MessageBoxIcon.Information);
            else
                MessageBox.Show("连接失败：" + msg, "测试连接", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }

        private void OnRunOnce()
        {
            CollectUiToConfig();
            _worker.ReloadConfig();
            System.Threading.Tasks.Task.Run(() =>
            {
                try
                {
                    int n = _worker.RunOnceManually();
                    Logger.Info($"手动执行完成，打印 {n} 张");
                }
                catch (Exception ex)
                {
                    Logger.Error("手动执行失败：" + ex.Message);
                }
            });
        }

        private void OnStartStop()
        {
            if (_worker.IsRunning) StopWorker();
            else StartWorker();
        }

        private void StartWorker()
        {
            CollectUiToConfig();
            _cfg.Save();
            _worker.ReloadConfig();
            _worker.Start();
            _btnStartStop.Text = "停止轮询";
        }

        private void StopWorker()
        {
            _worker.Stop();
            _btnStartStop.Text = "开始轮询";
        }

        private void OnFormClosing(object sender, FormClosingEventArgs e)
        {
            // 点右上角关闭时最小化到托盘，保持后台静默运行，而不是退出。
            if (e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
                ShowInTaskbar = false;
                _tray.ShowBalloonTip(1500, "仍在后台运行", "自动打印代理已最小化到托盘，双击图标可恢复。", ToolTipIcon.Info);
            }
        }

        private bool IsWinStartupEnabled()
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(RunKey, false))
                    return key?.GetValue(RunValueName) != null;
            }
            catch { return false; }
        }

        private void SetWinStartup(bool enable)
        {
            try
            {
                using (var key = Registry.CurrentUser.OpenSubKey(RunKey, true))
                {
                    if (key == null) return;
                    if (enable)
                        key.SetValue(RunValueName, "\"" + Application.ExecutablePath + "\"");
                    else if (key.GetValue(RunValueName) != null)
                        key.DeleteValue(RunValueName, false);
                }
            }
            catch (Exception ex)
            {
                Logger.Warn("设置开机自启失败：" + ex.Message);
            }
        }

        private static decimal Clamp(int v, int min, int max)
        {
            if (v < min) return min;
            if (v > max) return max;
            return v;
        }

        private static DateTime ParseTime(string hhmm, int defH, int defM)
        {
            if (TimeSpan.TryParse(hhmm, out var ts))
                return DateTime.Today.Add(ts);
            return DateTime.Today.AddHours(defH).AddMinutes(defM);
        }
    }
}
