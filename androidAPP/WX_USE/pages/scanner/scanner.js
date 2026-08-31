Page({
  data: {
    windowWidth: 0,
    windowHeight: 0,
    frameLeft: 0,
    frameTop: 0,
    frameWidth: 0,
    frameHeight: 0,
    maskTopHeight: 0,
    maskLeftWidth: 0,
    maskRightLeft: 0,
    maskRightWidth: 0,
    maskBottomTop: 0,
    maskBottomHeight: 0,
    scanArea: [0, 0, 0, 0],
    cameraError: false,
    errorMessage: ''
  },

  onLoad() {
    this.scanLocked = false
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
    const windowWidth = info.windowWidth || 375
    const windowHeight = info.windowHeight || 667

    let frameWidth = Math.round(windowWidth * 0.78)
    let frameHeight = Math.round(frameWidth * 4 / 3)
    if (frameHeight > windowHeight * 0.72) {
      frameHeight = Math.round(windowHeight * 0.72)
      frameWidth = Math.round(frameHeight * 3 / 4)
    }
    const frameLeft = Math.round((windowWidth - frameWidth) / 2)
    const frameTop = Math.max(0, Math.round((windowHeight - frameHeight) * 0.42))
    const frameBottom = frameTop + frameHeight

    this.setData({
      windowWidth,
      windowHeight,
      frameLeft,
      frameTop,
      frameWidth,
      frameHeight,
      maskTopHeight: frameTop,
      maskLeftWidth: frameLeft,
      maskRightLeft: frameLeft + frameWidth,
      maskRightWidth: Math.max(0, windowWidth - frameLeft - frameWidth),
      maskBottomTop: frameBottom,
      maskBottomHeight: Math.max(0, windowHeight - frameBottom),
      scanArea: [frameLeft, frameTop, frameWidth, frameHeight]
    })
  },

  onScanCode(e) {
    if (this.scanLocked) return
    const result = e.detail && e.detail.result
    if (!result || !String(result).trim()) return

    this.scanLocked = true
    const rawFormat = (e.detail && (e.detail.type || e.detail.scanType)) || 'UNKNOWN'
    const eventChannel = this.getOpenerEventChannel && this.getOpenerEventChannel()
    if (eventChannel && eventChannel.emit) {
      eventChannel.emit('scanned', {
        value: String(result).trim(),
        format: String(rawFormat).toUpperCase()
      })
    }

    wx.navigateBack({
      delta: 1,
      fail() {
        wx.reLaunch({ url: '/pages/index/index' })
      }
    })
  },

  onClose() {
    const eventChannel = this.getOpenerEventChannel && this.getOpenerEventChannel()
    if (eventChannel && eventChannel.emit) {
      eventChannel.emit('cancel')
    }
    wx.navigateBack({
      delta: 1,
      fail() {
        wx.reLaunch({ url: '/pages/index/index' })
      }
    })
  },

  onCameraError(e) {
    const message = (e.detail && e.detail.errMsg) || '请允许使用摄像头后重试'
    this.setData({
      cameraError: true,
      errorMessage: message
    })
  },

  openSetting() {
    wx.openSetting({
      success: (res) => {
        if (res.authSetting && res.authSetting['scope.camera']) {
          this.setData({ cameraError: false })
        }
      }
    })
  }
})
