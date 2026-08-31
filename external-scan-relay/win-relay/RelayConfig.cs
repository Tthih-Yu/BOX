using System;
using System.IO;
using System.Net;
using System.Web.Script.Serialization;

namespace AliyunApiRelay
{
    internal sealed class RelayConfig
    {
        public string ListenPrefix { get; set; }
        public string AllowedSourceIp { get; set; }
        public string RelayToken { get; set; }
        public string ApiBaseUrl { get; set; }
        public string ApiToken { get; set; }
        public int MaxBodyBytes { get; set; }
        public int TimeoutSeconds { get; set; }

        public static RelayConfig Load(string path)
        {
            var serializer = new JavaScriptSerializer();
            var config = serializer.Deserialize<RelayConfig>(File.ReadAllText(path));
            config.Validate();
            return config;
        }

        public void Validate()
        {
            if (string.IsNullOrWhiteSpace(ListenPrefix) ||
                !ListenPrefix.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                !ListenPrefix.EndsWith("/"))
                throw new InvalidOperationException("监听地址必须以 http:// 开头并以 / 结尾。");

            IPAddress sourceIp;
            if (!IPAddress.TryParse(AllowedSourceIp, out sourceIp))
                throw new InvalidOperationException("公司 Ubuntu 来源 IP 格式不正确。");

            if (string.IsNullOrWhiteSpace(RelayToken) || RelayToken.Length < 32)
                throw new InvalidOperationException("RelayToken 至少需要 32 个字符。");
            if (string.IsNullOrWhiteSpace(ApiToken) || ApiToken.Length < 32)
                throw new InvalidOperationException("ApiToken 至少需要 32 个字符。");

            Uri api;
            if (!Uri.TryCreate(ApiBaseUrl, UriKind.Absolute, out api) || api.Scheme != Uri.UriSchemeHttps)
                throw new InvalidOperationException("阿里云 API 地址必须是有效的 HTTPS URL。");
            ApiBaseUrl = ApiBaseUrl.TrimEnd('/');

            if (MaxBodyBytes < 1024 || MaxBodyBytes > 1048576) MaxBodyBytes = 65536;
            if (TimeoutSeconds < 1 || TimeoutSeconds > 120) TimeoutSeconds = 20;
        }

        public void Save(string path)
        {
            var serializer = new JavaScriptSerializer();
            File.WriteAllText(path, serializer.Serialize(this));
        }
    }
}
