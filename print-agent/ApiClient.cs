using System;
using System.Collections.Generic;
using System.Net;
using System.Text;
using System.Web.Script.Serialization;

namespace AutoPrintAgent
{
    public class PrintJob
    {
        public string printJobNo { get; set; }
        public string taskNo { get; set; }
        public string printerName { get; set; }
        public string printType { get; set; }
        public string zplContent { get; set; }
        public string status { get; set; }
    }

    /// <summary>
    /// 后端打印接口客户端。用 X-Api-Key 鉴权（对应后端 app.security.external-api-key，SYSTEM 角色）。
    /// </summary>
    public class ApiClient
    {
        private readonly AppConfig _cfg;
        private static readonly JavaScriptSerializer Serializer = new JavaScriptSerializer();

        public ApiClient(AppConfig cfg)
        {
            _cfg = cfg;
            ServicePointManager.SecurityProtocol =
                SecurityProtocolType.Tls12 | SecurityProtocolType.Tls11 | SecurityProtocolType.Tls;
            ServicePointManager.Expect100Continue = false;
        }

        /// <summary>领取一批待打印作业。</summary>
        public List<PrintJob> ClaimNext()
        {
            var url = $"{_cfg.ServerBaseUrl}/print-jobs/next?limit={_cfg.BatchLimit}";
            if (!string.IsNullOrWhiteSpace(_cfg.FilterPrinterName))
                url += "&printerName=" + Uri.EscapeDataString(_cfg.FilterPrinterName.Trim());

            var body = HttpGet(url);
            var resp = Serializer.Deserialize<ApiListResponse>(body);
            if (resp == null || !resp.success)
                throw new Exception(resp?.message ?? "领取打印任务失败");
            return resp.data ?? new List<PrintJob>();
        }

        /// <summary>回传打印结果：PRINTED / FAILED。</summary>
        public void Callback(string printJobNo, bool printed, string message)
        {
            var url = $"{_cfg.ServerBaseUrl}/print-jobs/callback";
            var payload = new Dictionary<string, object>
            {
                ["printJobNo"] = printJobNo,
                ["status"] = printed ? "PRINTED" : "FAILED",
                ["message"] = message ?? ""
            };
            HttpPost(url, Serializer.Serialize(payload));
        }

        public bool TestConnection(out string message)
        {
            try
            {
                ClaimNext();
                message = "连接成功，接口可访问";
                return true;
            }
            catch (Exception ex)
            {
                message = ex.Message;
                return false;
            }
        }

        private string HttpGet(string url)
        {
            var req = BuildRequest(url, "GET");
            return ReadResponse(req);
        }

        private string HttpPost(string url, string json)
        {
            var req = BuildRequest(url, "POST");
            req.ContentType = "application/json; charset=utf-8";
            var bytes = Encoding.UTF8.GetBytes(json ?? "{}");
            req.ContentLength = bytes.Length;
            using (var s = req.GetRequestStream()) s.Write(bytes, 0, bytes.Length);
            return ReadResponse(req);
        }

        private HttpWebRequest BuildRequest(string url, string method)
        {
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Method = method;
            req.Timeout = 15000;
            req.ReadWriteTimeout = 15000;
            req.Accept = "application/json";
            req.Headers["X-Api-Key"] = _cfg.ApiKey ?? "";
            req.UserAgent = "AutoPrintAgent/1.0";
            return req;
        }

        private string ReadResponse(HttpWebRequest req)
        {
            try
            {
                using (var resp = (HttpWebResponse)req.GetResponse())
                using (var reader = new System.IO.StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                {
                    return reader.ReadToEnd();
                }
            }
            catch (WebException wex) when (wex.Response is HttpWebResponse errResp)
            {
                using (var reader = new System.IO.StreamReader(errResp.GetResponseStream(), Encoding.UTF8))
                {
                    var errBody = reader.ReadToEnd();
                    throw new Exception($"HTTP {(int)errResp.StatusCode}：{Abbreviate(errBody, 300)}");
                }
            }
        }

        private static string Abbreviate(string v, int max)
        {
            if (string.IsNullOrEmpty(v)) return "";
            return v.Length <= max ? v : v.Substring(0, max) + "...";
        }

        private class ApiListResponse
        {
            public bool success { get; set; }
            public string code { get; set; }
            public string message { get; set; }
            public List<PrintJob> data { get; set; }
        }
    }
}
