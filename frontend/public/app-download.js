(function () {
  function fmtSize(bytes) {
    if (!bytes || isNaN(bytes)) return '-';
    return (bytes / 1024 / 1024).toFixed(2) + ' MB';
  }
  fetch('/updates/android/latest.json?_=' + Date.now())
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function (d) {
      var meta = document.getElementById('meta');
      meta.innerHTML =
        '版本：<b>' + (d.versionName || '-') + '</b>（versionCode ' + (d.versionCode != null ? d.versionCode : '-') + '）<br>' +
        '大小：<b>' + fmtSize(d.fileSize) + '</b><br>' +
        (d.publishedAt ? '发布：' + d.publishedAt + '<br>' : '') +
        (d.releaseNotes ? '<span class="notes">更新说明：' + d.releaseNotes + '</span>' : '');
      var btn = document.getElementById('dlBtn');
      var url = d.apkUrl || ('/updates/android/' + (d.fileName || ''));
      btn.href = url;
      if (d.fileName) btn.setAttribute('download', d.fileName);
      btn.classList.remove('disabled');
      btn.textContent = '下载 APK v' + (d.versionName || '');
      if (d.sha256) document.getElementById('shaLine').textContent = 'SHA-256: ' + d.sha256;
    })
    .catch(function (e) {
      document.getElementById('meta').innerHTML =
        '<span style="color:#dc2626">暂无可下载的版本或读取失败（' + e.message + '）。</span>';
    });
})();
