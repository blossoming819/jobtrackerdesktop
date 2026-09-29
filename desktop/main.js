const { app, BrowserWindow, dialog, ipcMain, session } = require('electron')
const { spawn } = require('child_process')
const http = require('http')
const path = require('path')
const fs = require('fs')

const PORT = process.env.JOB_TRACKER_PORT || '18080'
const BASE_URL = `http://127.0.0.1:${PORT}`
let backendProcess = null
let backendLogStream = null

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true })
}

function logPath() {
  const logDir = path.join(app.getPath('userData'), 'logs')
  ensureDir(logDir)
  return path.join(logDir, 'backend.log')
}

function writeBackendLog(message) {
  if (!backendLogStream) {
    backendLogStream = fs.createWriteStream(logPath(), { flags: 'a' })
  }
  backendLogStream.write(message)
}

function backendJarPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'backend', 'app.jar')
  }
  return path.resolve(__dirname, '..', 'backend', 'target', 'job-tracker-0.0.1-SNAPSHOT.jar')
}

function javaCommand() {
  const runtimeRoot = app.isPackaged
    ? path.join(process.resourcesPath, 'runtime')
    : path.join(__dirname, 'resources', 'runtime')
  const bundledJava = path.join(runtimeRoot, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')

  if (fs.existsSync(bundledJava)) {
    return bundledJava
  }

  return process.env.JOBTRACKER_JAVA || 'java'
}

function waitForBackend(timeoutMs = 90000) {
  const startedAt = Date.now()

  return new Promise((resolve, reject) => {
    let settled = false

    const fail = error => {
      if (settled) return
      settled = true
      reject(error)
    }

    const poll = () => {
      if (settled) return

      if (backendProcess && backendProcess.exitCode !== null) {
        fail(new Error(`后端服务已退出，退出码：${backendProcess.exitCode}`))
        return
      }

      const request = http.get(`${BASE_URL}/api/dashboard`, response => {
        response.resume()
        settled = true
        resolve()
      })

      request.on('error', () => {
        if (Date.now() - startedAt > timeoutMs) {
          fail(new Error('后端服务启动超时'))
          return
        }
        setTimeout(poll, 500)
      })

      request.setTimeout(1000, () => {
        request.destroy()
      })
    }

    poll()
  })
}

async function startBackend() {
  const jarPath = backendJarPath()
  const javaPath = javaCommand()
  const userData = app.getPath('userData')
  const currentLogPath = logPath()

  writeBackendLog(`\n\n[${new Date().toISOString()}] Starting backend\n`)
  writeBackendLog(`java=${javaPath}\n`)
  writeBackendLog(`jar=${jarPath}\n`)
  writeBackendLog(`port=${PORT}\n`)
  writeBackendLog(`data=${userData}\n`)

  if (!fs.existsSync(jarPath)) {
    throw new Error(`后端 jar 不存在：${jarPath}`)
  }

  backendProcess = spawn(javaPath, [
    '-jar',
    jarPath,
    '--spring.profiles.active=desktop',
    `--server.port=${PORT}`
  ], {
    cwd: path.dirname(jarPath),
    env: {
      ...process.env,
      JOB_TRACKER_PORT: PORT,
      JOB_TRACKER_DATA_DIR: userData
    },
    windowsHide: true
  })

  backendProcess.on('error', error => {
    writeBackendLog(`[spawn error] ${error.stack || error.message}\n`)
    dialog.showErrorBox('后端启动失败', `${error.message}\n\n日志位置：${currentLogPath}`)
  })

  backendProcess.on('exit', (code, signal) => {
    writeBackendLog(`[exit] code=${code} signal=${signal}\n`)
  })

  backendProcess.stdout.on('data', chunk => {
    const text = chunk.toString()
    writeBackendLog(text)
    console.log(text)
  })

  backendProcess.stderr.on('data', chunk => {
    const text = chunk.toString()
    writeBackendLog(text)
    console.error(text)
  })

  try {
    await waitForBackend()
  } catch (error) {
    if (backendProcess && !backendProcess.killed) {
      backendProcess.kill()
    }
    throw new Error(`${error.message}\n\n日志位置：${currentLogPath}`)
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 1100,
    minHeight: 720,
    title: '个人求职助手',
    icon: path.join(__dirname, 'resources', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  const jarStats = fs.statSync(backendJarPath())
  const buildFingerprint = `${app.getVersion()}-${Math.trunc(jarStats.mtimeMs)}-${jarStats.size}`
  win.loadURL(`${BASE_URL}/?desktopBuild=${encodeURIComponent(buildFingerprint)}`)
}

app.whenReady().then(async () => {
  ipcMain.handle('select-directory', async () => {
    const result = await dialog.showOpenDialog({
      title: '选择保存目录',
      properties: ['openDirectory', 'createDirectory']
    })
    return result.canceled ? null : result.filePaths[0]
  })

  try {
    await startBackend()
    // Electron keeps its HTTP cache across launches. Clear only cached web
    // resources so an upgraded backend cannot reopen an older bundled UI.
    // Cookies, local storage and all JobTracker data remain untouched.
    await session.defaultSession.clearCache()
    createWindow()
  } catch (error) {
    dialog.showErrorBox('启动失败', error.message)
    app.quit()
  }
})

app.on('window-all-closed', () => {
  app.quit()
})

app.on('before-quit', () => {
  if (backendProcess && !backendProcess.killed) {
    backendProcess.kill()
  }
  if (backendLogStream) {
    backendLogStream.end()
  }
})
