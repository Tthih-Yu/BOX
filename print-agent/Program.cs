using System;
using System.Threading;
using System.Windows.Forms;

namespace AutoPrintAgent
{
    internal static class Program
    {
        private static Mutex _mutex;

        [STAThread]
        static void Main()
        {
            bool createdNew;
            _mutex = new Mutex(true, "AutoPrintAgent_SingleInstance_Mutex", out createdNew);
            if (!createdNew)
            {
                MessageBox.Show("自动打印代理已在运行（查看系统托盘图标）。", "提示",
                    MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            AppDomain.CurrentDomain.UnhandledException += (s, e) =>
                Logger.Error("未捕获异常：" + (e.ExceptionObject as Exception)?.Message);
            Application.ThreadException += (s, e) =>
                Logger.Error("界面线程异常：" + e.Exception?.Message);

            Application.Run(new MainForm());
        }
    }
}
