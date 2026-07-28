import { FFmpeg } from '@ffmpeg/ffmpeg'
import { fetchFile, toBlobURL } from '@ffmpeg/util'
import coreURL from '@ffmpeg/core?url'
import wasmURL from '@ffmpeg/core/wasm?url'

const MAX_HEIGHT = 720

let ffmpegPromise = null

function loadFFmpeg() {
  if (!ffmpegPromise) {
    ffmpegPromise = (async () => {
      const ffmpeg = new FFmpeg()
      await ffmpeg.load({
        coreURL: await toBlobURL(coreURL, 'text/javascript'),
        wasmURL: await toBlobURL(wasmURL, 'application/wasm'),
      })
      return ffmpeg
    })()
  }
  return ffmpegPromise
}

function extensionOf(filename) {
  const match = /\.[^.]+$/.exec(filename)
  return match ? match[0] : '.mp4'
}

export async function compressVideo(file) {
  const ffmpeg = await loadFFmpeg()
  const inputName = `input${extensionOf(file.name)}`
  const outputName = 'output.mp4'

  await ffmpeg.writeFile(inputName, await fetchFile(file))
  await ffmpeg.exec([
    '-i', inputName,
    '-vf', `scale=-2:'min(${MAX_HEIGHT},ih)'`,
    '-c:v', 'libx264',
    '-crf', '28',
    '-preset', 'veryfast',
    '-c:a', 'aac',
    '-b:a', '96k',
    outputName,
  ])
  const data = await ffmpeg.readFile(outputName)
  await ffmpeg.deleteFile(inputName)
  await ffmpeg.deleteFile(outputName)

  const name = `${file.name.replace(/\.[^.]+$/, '')}-optimized.mp4`
  return new File([data], name, { type: 'video/mp4' })
}
