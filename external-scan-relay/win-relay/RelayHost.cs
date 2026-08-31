using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

namespace AliyunApiRelay
{
    internal sealed class RelayHost : IDisposable
    {
        private const long MaxUpstreamResponseBytes = 8 * 1024 * 1024;

        private readonly RelayConfig _config;
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();
        private readonly SemaphoreSlim _slots = new SemaphoreSlim(8, 8);
        private HttpListener _listener;
        private Thread _thread;
        private volatile bool _running;

        public RelayHost(RelayConfig config) { _config = config; }

        public void Start()
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            ServicePointManager.Expect100Continue = false;
            _listener = new HttpListener();
            _listener.Prefixes.Add(_config.ListenPrefix);
            _listener.Start();
            _running = true;
            _thread = new Thread(ListenLoop) { IsBackground = true, Name = "AliyunApiRelay" };
            _thread.Start();
        }

        private void ListenLoop()
        {
            while (_running)
            {
                try
                {
                    var context = _listener.GetContext();
                    if (!_slots.Wait(0))
                    {
                        WriteJson(context.Response, 503, new { success = false, error = "server_busy" });
                        continue;
                    }
                    ThreadPool.QueueUserWorkItem(Process, context);
                }
                catch (HttpListenerException) { if (_running) throw; }
                catch (ObjectDisposedException) { }
            }
        }

        private void Process(object state)
        {
            var context = (HttpListenerContext)state;
            try { Handle(context); }
            catch (Exception ex)
            {
                try { WriteJson(context.Response, 502, new { success = false, error = "relay_failed", detail = ex.Message }); }
                catch { }
            }
            finally { _slots.Release(); }
        }

        private void Handle(HttpListenerContext context)
        {
            var request = context.Request;
            string remoteIp = NormalizeIp(request.RemoteEndPoint.Address.ToString());
            if (remoteIp != _config.AllowedSourceIp && remoteIp != "127.0.0.1" && remoteIp != "::1")
            {
                WriteJson(context.Response, 403, new { success = false, error = "source_forbidden" });
                return;
            }
            if (!FixedTimeEquals(request.Headers["X-Relay-Token"], _config.RelayToken))
            {
                WriteJson(context.Response, 401, new { success = false, error = "invalid_token" });
                return;
            }

            string path = request.Url.AbsolutePath.TrimEnd('/');
            string upstreamPath;
            if (request.HttpMethod == "GET" && path == "/aliyun-relay/health") upstreamPath = "/health";
            else if (request.HttpMethod == "POST" && path == "/aliyun-relay/data") upstreamPath = "/api/data";
            else if (request.HttpMethod == "GET" && path == "/aliyun-relay/tasks") upstreamPath = "/api/tasks" + request.Url.Query;
            else if (request.HttpMethod == "POST" && path.StartsWith("/aliyun-relay/tasks/") && path.EndsWith("/result"))
                upstreamPath = MapParameterizedPath(path, "/aliyun-relay/tasks/", "/result", "/api/tasks/") + "/result";
            else if (request.HttpMethod == "POST" && path == "/aliyun-relay/worker/commands/claim")
                upstreamPath = "/worker/v1/commands/claim" + request.Url.Query;
            else if (request.HttpMethod == "POST" && path.StartsWith("/aliyun-relay/worker/commands/") && path.EndsWith("/renew"))
                upstreamPath = MapParameterizedPath(path, "/aliyun-relay/worker/commands/", "/renew", "/worker/v1/commands/") + "/renew";
            else if (request.HttpMethod == "POST" && path.StartsWith("/aliyun-relay/worker/commands/") && path.EndsWith("/complete"))
                upstreamPath = MapParameterizedPath(path, "/aliyun-relay/worker/commands/", "/complete", "/worker/v1/commands/") + "/complete";
            else if (request.HttpMethod == "POST" && path.StartsWith("/aliyun-relay/worker/commands/") && path.EndsWith("/fail"))
                upstreamPath = MapParameterizedPath(path, "/aliyun-relay/worker/commands/", "/fail", "/worker/v1/commands/") + "/fail";
            else
            {
                WriteJson(context.Response, 404, new { success = false, error = "not_found" });
                return;
            }

            if (request.HttpMethod != "GET")
            {
                if (request.ContentLength64 < 0)
                {
                    WriteJson(context.Response, 411, new { success = false, error = "content_length_required" });
                    return;
                }
                if (request.ContentLength64 > _config.MaxBodyBytes)
                {
                    WriteJson(context.Response, 413, new { success = false, error = "body_too_large" });
                    return;
                }
            }

            byte[] body = ReadBody(request);
            Forward(context.Response, request.HttpMethod, upstreamPath, body, request.Headers["X-Request-Id"], request.Headers["X-Worker-Id"]);
        }

        private static string MapParameterizedPath(string path, string prefix, string suffix, string upstreamPrefix)
        {
            int valueLength = path.Length - prefix.Length - suffix.Length;
            if (valueLength <= 0) throw new InvalidOperationException("路径参数不能为空。");
            string value = Uri.UnescapeDataString(path.Substring(prefix.Length, valueLength));
            if (value.IndexOf('/') >= 0 || value.IndexOf('\\') >= 0 || value.Length > 128)
                throw new InvalidOperationException("路径参数不合法。");
            return upstreamPrefix + Uri.EscapeDataString(value);
        }

        private byte[] ReadBody(HttpListenerRequest request)
        {
            if (request.HttpMethod == "GET") return new byte[0];
            using (var memory = new MemoryStream())
            {
                var buffer = new byte[8192];
                int total = 0, read;
                while ((read = request.InputStream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    total += read;
                    if (total > _config.MaxBodyBytes) throw new InvalidOperationException("请求体过大。");
                    memory.Write(buffer, 0, read);
                }
                return memory.ToArray();
            }
        }

        private void Forward(HttpListenerResponse clientResponse, string method, string path, byte[] body, string requestId, string workerId)
        {
            requestId = string.IsNullOrWhiteSpace(requestId) ? Guid.NewGuid().ToString("D") : requestId;
            var upstream = (HttpWebRequest)WebRequest.Create(_config.ApiBaseUrl + path);
            upstream.Method = method;
            upstream.Proxy = null;
            upstream.Timeout = _config.TimeoutSeconds * 1000;
            upstream.ReadWriteTimeout = _config.TimeoutSeconds * 1000;
            upstream.Headers[HttpRequestHeader.Authorization] = "Bearer " + _config.ApiToken;
            upstream.Headers["X-Request-Id"] = requestId;
            if (!string.IsNullOrWhiteSpace(workerId)) upstream.Headers["X-Worker-Id"] = workerId.Trim();
            if (body.Length > 0)
            {
                upstream.ContentType = "application/json; charset=utf-8";
                upstream.ContentLength = body.Length;
                using (var stream = upstream.GetRequestStream()) stream.Write(body, 0, body.Length);
            }

            try
            {
                using (var response = (HttpWebResponse)upstream.GetResponse()) CopyResponse(clientResponse, response, requestId);
            }
            catch (WebException ex)
            {
                var response = ex.Response as HttpWebResponse;
                if (response == null) throw;
                using (response) CopyResponse(clientResponse, response, requestId);
            }
        }

        private static void CopyResponse(HttpListenerResponse target, HttpWebResponse source, string requestId)
        {
            using (var memory = new MemoryStream())
            using (var stream = source.GetResponseStream())
            {
                if (stream != null)
                {
                    var buffer = new byte[8192];
                    int read;
                    while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        memory.Write(buffer, 0, read);
                        if (memory.Length > MaxUpstreamResponseBytes)
                            throw new InvalidOperationException("上游响应体过大。");
                    }
                }
                byte[] bytes = memory.ToArray();
                target.StatusCode = (int)source.StatusCode;
                target.ContentType = source.ContentType ?? "application/json; charset=utf-8";
                target.Headers["X-Request-Id"] = requestId;
                target.ContentLength64 = bytes.Length;
                target.OutputStream.Write(bytes, 0, bytes.Length);
                target.Close();
            }
        }

        private void WriteJson(HttpListenerResponse response, int status, object value)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(_json.Serialize(value));
            response.StatusCode = status;
            response.ContentType = "application/json; charset=utf-8";
            response.ContentLength64 = bytes.Length;
            response.OutputStream.Write(bytes, 0, bytes.Length);
            response.Close();
        }

        private static bool FixedTimeEquals(string supplied, string expected)
        {
            if (supplied == null || expected == null) return false;
            byte[] a = Encoding.UTF8.GetBytes(supplied), b = Encoding.UTF8.GetBytes(expected);
            int diff = a.Length ^ b.Length;
            int length = Math.Min(a.Length, b.Length);
            for (int i = 0; i < length; i++) diff |= a[i] ^ b[i];
            return diff == 0;
        }

        private static string NormalizeIp(string ip) { return ip.StartsWith("::ffff:", StringComparison.OrdinalIgnoreCase) ? ip.Substring(7) : ip; }

        public void Dispose()
        {
            _running = false;
            if (_listener != null) _listener.Close();
            if (_thread != null && _thread.IsAlive) _thread.Join(5000);
            _slots.Dispose();
        }
    }
}
