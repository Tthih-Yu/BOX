using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace AutoPrintAgent
{
    /// <summary>
    /// 通过 Windows 打印后台（winspool）以 RAW 方式把字节直接发给打印机。
    /// ZPL 指令走 RAW 通道，不经过打印驱动排版，不弹打印对话框，标签不会错位。
    /// </summary>
    public static class RawPrinterHelper
    {
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private class DOCINFOA
        {
            [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
            [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile;
            [MarshalAs(UnmanagedType.LPWStr)] public string pDataType;
        }

        [DllImport("winspool.Drv", EntryPoint = "OpenPrinterW", SetLastError = true, CharSet = CharSet.Unicode, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool OpenPrinter(string src, out IntPtr hPrinter, IntPtr pd);

        [DllImport("winspool.Drv", EntryPoint = "ClosePrinter", SetLastError = true, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool ClosePrinter(IntPtr hPrinter);

        [DllImport("winspool.Drv", EntryPoint = "StartDocPrinterW", SetLastError = true, CharSet = CharSet.Unicode, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool StartDocPrinter(IntPtr hPrinter, int level, [In, MarshalAs(UnmanagedType.LPStruct)] DOCINFOA di);

        [DllImport("winspool.Drv", EntryPoint = "EndDocPrinter", SetLastError = true, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool EndDocPrinter(IntPtr hPrinter);

        [DllImport("winspool.Drv", EntryPoint = "StartPagePrinter", SetLastError = true, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool StartPagePrinter(IntPtr hPrinter);

        [DllImport("winspool.Drv", EntryPoint = "EndPagePrinter", SetLastError = true, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool EndPagePrinter(IntPtr hPrinter);

        [DllImport("winspool.Drv", EntryPoint = "WritePrinter", SetLastError = true, ExactSpelling = true, CallingConvention = CallingConvention.StdCall)]
        private static extern bool WritePrinter(IntPtr hPrinter, IntPtr pBytes, int dwCount, out int dwWritten);

        /// <summary>把 ZPL 文本按字节 RAW 发送到指定打印机。失败抛异常。</summary>
        public static void SendZpl(string printerName, string zpl, string docName = "ZPL Label")
        {
            if (string.IsNullOrWhiteSpace(printerName))
                throw new ArgumentException("打印机名称为空");
            if (string.IsNullOrEmpty(zpl))
                throw new ArgumentException("ZPL 内容为空");

            // ZPL 用单字节编码即可（斑马机按 CP 处理，中文已在 ^CI28/UTF-8 场景由服务端渲染）。
            var bytes = Encoding.UTF8.GetBytes(zpl);
            SendBytes(printerName, bytes, docName);
        }

        public static void SendBytes(string printerName, byte[] bytes, string docName)
        {
            IntPtr hPrinter;
            if (!OpenPrinter(printerName.Trim(), out hPrinter, IntPtr.Zero))
                throw new IOException($"打开打印机失败（错误码 {Marshal.GetLastWin32Error()}）：{printerName}");

            IntPtr pUnmanaged = IntPtr.Zero;
            bool docStarted = false, pageStarted = false;
            try
            {
                var di = new DOCINFOA
                {
                    pDocName = docName,
                    pDataType = "RAW"
                };
                if (!StartDocPrinter(hPrinter, 1, di))
                    throw new IOException($"StartDocPrinter 失败（错误码 {Marshal.GetLastWin32Error()}）");
                docStarted = true;

                if (!StartPagePrinter(hPrinter))
                    throw new IOException($"StartPagePrinter 失败（错误码 {Marshal.GetLastWin32Error()}）");
                pageStarted = true;

                pUnmanaged = Marshal.AllocCoTaskMem(bytes.Length);
                Marshal.Copy(bytes, 0, pUnmanaged, bytes.Length);

                if (!WritePrinter(hPrinter, pUnmanaged, bytes.Length, out int written))
                    throw new IOException($"WritePrinter 失败（错误码 {Marshal.GetLastWin32Error()}）");
                if (written != bytes.Length)
                    throw new IOException($"写入不完整：应写 {bytes.Length} 字节，实际 {written} 字节");
            }
            finally
            {
                if (pUnmanaged != IntPtr.Zero) Marshal.FreeCoTaskMem(pUnmanaged);
                if (pageStarted) EndPagePrinter(hPrinter);
                if (docStarted) EndDocPrinter(hPrinter);
                ClosePrinter(hPrinter);
            }
        }
    }
}
