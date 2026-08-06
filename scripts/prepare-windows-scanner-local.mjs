import { copyFile, mkdir, open, stat } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDirectory, '..')
const scannerProject = resolve(projectRoot, 'windows-scanner/src/LimitScanner/LimitScanner.csproj')
const artifactDirectory = resolve(projectRoot, 'windows-scanner/artifacts')
const scannerExecutable = resolve(artifactDirectory, 'LimitScanner.exe')
const localDownloadDirectory = resolve(projectRoot, 'frontend/public/downloads')
const localDownloadExecutable = resolve(localDownloadDirectory, 'LimitScanner.exe')
const apiBaseUrl = process.env.LIMIT_API_BASE_URL || 'http://localhost:18080/'

if (!/^https?:\/\//.test(apiBaseUrl)) {
  throw new Error('LIMIT_API_BASE_URL must be an absolute HTTP(S) URL.')
}

await run('dotnet', [
  'publish', scannerProject,
  '-c', 'Release',
  '-r', 'win-x64',
  '--self-contained', 'true',
  '-p:PublishSingleFile=true',
  '-p:PublishTrimmed=false',
  `-p:LimitApiBaseUrl=${apiBaseUrl}`,
  '-o', artifactDirectory,
])

await verifyPortableExecutable(scannerExecutable)
await mkdir(localDownloadDirectory, { recursive: true })
await copyFile(scannerExecutable, localDownloadExecutable)
await verifyPortableExecutable(localDownloadExecutable)

console.log(`Prepared local scanner download: ${localDownloadExecutable}`)

function run(command, args) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: 'inherit' })
    child.on('error', (error) => {
      reject(new Error(`Failed to run ${command}. Install the .NET 8 SDK and try again.`, { cause: error }))
    })
    child.on('exit', (code) => {
      if (code === 0) resolvePromise()
      else reject(new Error(`${command} exited with code ${code}.`))
    })
  })
}

async function verifyPortableExecutable(filePath) {
  const file = await open(filePath, 'r')
  try {
    const header = Buffer.alloc(2)
    await file.read(header, 0, header.length, 0)
    if (header.toString('ascii') !== 'MZ') {
      throw new Error(`Scanner artifact is not a Windows executable: ${filePath}`)
    }
  } finally {
    await file.close()
  }

  const fileSize = (await stat(filePath)).size
  if (fileSize === 0) throw new Error(`Scanner artifact is empty: ${filePath}`)
}
