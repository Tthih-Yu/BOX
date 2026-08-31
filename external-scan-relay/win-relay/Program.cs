using System;
using System.IO;
using System.ServiceProcess;
using System.Windows.Forms;

namespace AliyunApiRelay
{
    internal static class Program
    {
        private static readonly string ConfigPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "relay-config.json");

        [STAThread]
        private static int Main(string[] args)
        {
            try
            {
                if (!Environment.UserInteractive)
                {
                    ServiceBase.Run(new RelayWindowsService(ConfigPath));
                    return 0;
                }

                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new MainForm(ConfigPath));
                return 0;
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message, "阿里云 API 中转服务", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
        }
    }

    internal sealed class RelayWindowsService : ServiceBase
    {
        private readonly string _configPath;
        private RelayHost _host;

        public RelayWindowsService(string configPath)
        {
            _configPath = configPath;
            ServiceName = "AliyunApiRelay";
            CanStop = true;
            AutoLog = true;
        }

        protected override void OnStart(string[] args)
        {
            _host = new RelayHost(RelayConfig.Load(_configPath));
            _host.Start();
        }

        protected override void OnStop()
        {
            if (_host != null) _host.Dispose();
            _host = null;
        }
    }
}
