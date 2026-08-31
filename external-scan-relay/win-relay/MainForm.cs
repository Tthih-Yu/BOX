using System;
using System.Drawing;
using System.IO;
using System.Net;
using System.Text;
using System.Windows.Forms;

namespace AliyunApiRelay
{
    internal sealed class MainForm : Form
    {
        private readonly string _configPath;
        private RelayHost _host;

        private readonly TextBox _listenPrefix = new TextBox();
        private readonly TextBox _allowedSourceIp = new TextBox();
        private readonly TextBox _relayToken = new TextBox();
        private readonly TextBox _apiBaseUrl = new TextBox();
        private readonly TextBox _apiToken = new TextBox();
        private readonly TextBox _maxBodyBytes = new TextBox();
        private readonly TextBox _timeoutSeconds = new TextBox();
        private readonly TextBox _logBox = new TextBox();

        private readonly Label _statusLabel = new Label();
        private readonly Button _saveButton = new Button();
        private readonly Button _startButton = new Button();
        private readonly Button _stopButton = new Button();
        private readonly Button _testButton = new Button();

        public MainForm(string configPath)
        {
            _configPath = configPath;
            InitializeUi();
            LoadConfigIntoUi();
            UpdateButtons();
        }

        private void InitializeUi()
        {
            Text = "阿里云 API 中转服务";
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(680, 560);
            AutoScaleMode = AutoScaleMode.Dpi;

            _saveButton.Text = "保存配置";
            _saveButton.Click += SaveButton_Click;

            _startButton.Text = "启动";
            _startButton.Click += StartButton_Click;

            _stopButton.Text = "停止";
            _stopButton.Enabled = false;
            _stopButton.Click += StopButton_Click;

            _testButton.Text = "测试两端连接";
            _testButton.Click += TestButton_Click;

            _statusLabel.Text = "未启动";
            _statusLabel.AutoSize = true;
            _statusLabel.Anchor = AnchorStyles.Left;
            _statusLabel.Margin = new Padding(12, 9, 0, 0);

            var buttonPanel = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.LeftToRight,
                WrapContents = false,
                Padding = new Padding(12, 10, 12, 10)
            };
            buttonPanel.Controls.Add(_saveButton);
            buttonPanel.Controls.Add(_startButton);
            buttonPanel.Controls.Add(_stopButton);
            buttonPanel.Controls.Add(_testButton);
            buttonPanel.Controls.Add(_statusLabel);

            var fields = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                AutoScroll = true,
                ColumnCount = 2,
                Padding = new Padding(12, 0, 12, 12)
            };
            fields.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 150));
            fields.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            for (int i = 0; i < 7; i++)
            {
                fields.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            }

            AddField(fields, 0, "本机监听地址", _listenPrefix);
            AddField(fields, 1, "公司 Ubuntu 来源 IP", _allowedSourceIp);
            AddField(fields, 2, "RelayToken", _relayToken, true);
            AddField(fields, 3, "阿里云 API 地址（IP/域名）", _apiBaseUrl);
            AddField(fields, 4, "ApiToken", _apiToken, true);
            AddField(fields, 5, "最大请求体字节", _maxBodyBytes);
            AddField(fields, 6, "超时秒数", _timeoutSeconds);

            _logBox.Multiline = true;
            _logBox.ReadOnly = true;
            _logBox.ScrollBars = ScrollBars.Vertical;
            _logBox.Dock = DockStyle.Fill;
            _logBox.Margin = new Padding(12, 0, 12, 12);
            _logBox.BackColor = Color.White;

            var root = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 3
            };
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.RowStyles.Add(new RowStyle(SizeType.Absolute, 150));
            root.Controls.Add(buttonPanel, 0, 0);
            root.Controls.Add(fields, 0, 1);
            root.Controls.Add(_logBox, 0, 2);

            Controls.Add(root);
        }

        private void AddField(TableLayoutPanel table, int row, string labelText, TextBox textBox, bool password = false)
        {
            var label = new Label
            {
                Text = labelText,
                AutoSize = true,
                Anchor = AnchorStyles.Left,
                Margin = new Padding(0, 6, 10, 6)
            };
            textBox.Anchor = AnchorStyles.Left | AnchorStyles.Right;
            textBox.Margin = new Padding(0, 3, 0, 3);
            textBox.UseSystemPasswordChar = password;

            table.Controls.Add(label, 0, row);
            table.Controls.Add(textBox, 1, row);
        }

        private void LoadConfigIntoUi()
        {
            if (File.Exists(_configPath))
            {
                try
                {
                    var config = RelayConfig.Load(_configPath);
                    _listenPrefix.Text = config.ListenPrefix;
                    _allowedSourceIp.Text = config.AllowedSourceIp;
                    _relayToken.Text = config.RelayToken;
                    _apiBaseUrl.Text = config.ApiBaseUrl;
                    _apiToken.Text = config.ApiToken;
                    _maxBodyBytes.Text = config.MaxBodyBytes.ToString();
                    _timeoutSeconds.Text = config.TimeoutSeconds.ToString();
                    return;
                }
                catch (Exception ex)
                {
                    MessageBox.Show(this, "读取配置失败，将使用默认值：\r\n" + ex.Message, Text, MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            }

            _listenPrefix.Text = "http://+:80/aliyun-relay/";
            _allowedSourceIp.Text = "127.0.0.1";
            _apiBaseUrl.Text = "https://tthih.top";
            _maxBodyBytes.Text = "65536";
            _timeoutSeconds.Text = "20";
        }

        private RelayConfig ReadConfigFromUi()
        {
            var config = new RelayConfig
            {
                ListenPrefix = _listenPrefix.Text.Trim(),
                AllowedSourceIp = _allowedSourceIp.Text.Trim(),
                RelayToken = _relayToken.Text.Trim(),
                ApiBaseUrl = _apiBaseUrl.Text.Trim(),
                ApiToken = _apiToken.Text.Trim(),
                MaxBodyBytes = ParseInt(_maxBodyBytes.Text, 65536),
                TimeoutSeconds = ParseInt(_timeoutSeconds.Text, 20)
            };
            config.Validate();
            return config;
        }

        private void SaveConfig()
        {
            var config = ReadConfigFromUi();
            config.Save(_configPath);
        }

        private static int ParseInt(string value, int defaultValue)
        {
            int result;
            return int.TryParse(value.Trim(), out result) ? result : defaultValue;
        }

        private void SaveButton_Click(object sender, EventArgs e)
        {
            try
            {
                SaveConfig();
                _statusLabel.Text = "配置已保存";
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "保存失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void StartButton_Click(object sender, EventArgs e)
        {
            if (_host != null)
            {
                return;
            }

            try
            {
                SaveConfig();
                var host = new RelayHost(RelayConfig.Load(_configPath));
                host.Start();
                _host = host;
                _statusLabel.Text = "运行中";
                UpdateButtons();
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "启动失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void StopButton_Click(object sender, EventArgs e)
        {
            if (_host == null)
            {
                return;
            }

            try
            {
                _host.Dispose();
            }
            finally
            {
                _host = null;
                _statusLabel.Text = "已停止";
                UpdateButtons();
            }
        }

        private void TestButton_Click(object sender, EventArgs e)
        {
            _logBox.Clear();
            AppendLog("== 两端连接测试 ==");
            AppendLog("公司 Ubuntu 来源 IP：" + _allowedSourceIp.Text.Trim());
            AppendLog("阿里云 API 地址：" + _apiBaseUrl.Text.Trim());
            AppendLog(string.Empty);
            AppendLog("1) 本端中继自检（127.0.0.1 → WinServer Relay）：");
            AppendLog(TestLocalRelay());
            AppendLog(string.Empty);
            AppendLog("2) WinServer → 阿里云：");
            AppendLog(TestAliyun());
        }

        private string TestLocalRelay()
        {
            if (_host == null)
            {
                return "未启动，请先点击“启动”后再测试。";
            }

            try
            {
                var request = (HttpWebRequest)WebRequest.Create(BuildLocalBaseUrl() + "/health");
                request.Method = "GET";
                request.Proxy = null;
                request.Timeout = 10000;
                request.ReadWriteTimeout = 10000;
                request.Headers["X-Relay-Token"] = _relayToken.Text.Trim();

                using (var response = (HttpWebResponse)request.GetResponse())
                using (var reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                {
                    return "成功 HTTP " + (int)response.StatusCode + Environment.NewLine + reader.ReadToEnd().Trim();
                }
            }
            catch (WebException ex)
            {
                var response = ex.Response as HttpWebResponse;
                if (response == null)
                {
                    return "失败：" + ex.Message;
                }

                using (response)
                using (var reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                {
                    return "失败 HTTP " + (int)response.StatusCode + Environment.NewLine + reader.ReadToEnd().Trim();
                }
            }
            catch (Exception ex)
            {
                return "失败：" + ex.Message;
            }
        }

        private string TestAliyun()
        {
            var apiBase = _apiBaseUrl.Text.Trim();
            if (string.IsNullOrWhiteSpace(apiBase))
            {
                return "未填写阿里云 API 地址。";
            }

            apiBase = apiBase.TrimEnd('/');
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            ServicePointManager.Expect100Continue = false;

            try
            {
                var request = (HttpWebRequest)WebRequest.Create(apiBase + "/health");
                request.Method = "GET";
                request.Proxy = null;
                request.Timeout = 10000;
                request.ReadWriteTimeout = 10000;

                var token = _apiToken.Text.Trim();
                if (!string.IsNullOrEmpty(token))
                {
                    request.Headers[HttpRequestHeader.Authorization] = "Bearer " + token;
                }

                using (var response = (HttpWebResponse)request.GetResponse())
                using (var reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                {
                    return "成功 HTTP " + (int)response.StatusCode + Environment.NewLine + reader.ReadToEnd().Trim();
                }
            }
            catch (WebException ex)
            {
                var response = ex.Response as HttpWebResponse;
                if (response == null)
                {
                    return "失败：" + ex.Message;
                }

                using (response)
                using (var reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                {
                    return "失败 HTTP " + (int)response.StatusCode + Environment.NewLine + reader.ReadToEnd().Trim();
                }
            }
            catch (Exception ex)
            {
                return "失败：" + ex.Message;
            }
        }

        private string BuildLocalBaseUrl()
        {
            var prefix = _listenPrefix.Text.Trim();
            if (string.IsNullOrWhiteSpace(prefix) || !prefix.StartsWith("http://", StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidOperationException("监听地址必须以 http:// 开头。");
            }

            var rest = prefix.Substring("http://".Length);
            if (rest.StartsWith("+", StringComparison.Ordinal))
            {
                rest = "127.0.0.1" + rest.Substring(1);
            }

            return "http://" + rest.TrimEnd('/');
        }

        private void AppendLog(string text)
        {
            _logBox.AppendText(text + Environment.NewLine);
        }

        private void UpdateButtons()
        {
            _startButton.Enabled = _host == null;
            _stopButton.Enabled = _host != null;
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (_host != null)
            {
                try
                {
                    _host.Dispose();
                }
                finally
                {
                    _host = null;
                }
            }
            base.OnFormClosing(e);
        }
    }
}
