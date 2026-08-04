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

/*
  영상 압축은 한 번에 하나씩만 돌립니다.
  ---------------------------------------------------------------------------
  ffmpeg 인스턴스는 하나를 같이 쓰고, 그 안의 파일 이름도 하나뿐이었습니다. 그래서
  영상을 여러 개 골라 한꺼번에 올리면 세 호출이 같은 input에 겹쳐 쓰고, 먼저 끝난
  쪽이 그 파일을 지워 버려 나머지가 "업로드 실패"로 떨어졌습니다.

  호출마다 파일 이름을 다르게 주고(같은 자리를 겹쳐 쓰지 않도록), 변환은 줄을 세워
  차례로 돌립니다. wasm 변환은 CPU를 다 쓰기 때문에 동시에 돌린다고 빨라지지도
  않고, 메모리만 몇 배로 잡습니다.
*/
let queue = Promise.resolve()
let sequence = 0

export function compressVideo(file) {
  // 앞 작업이 실패해도 줄이 끊기지 않게 성공·실패 양쪽에서 이어 붙입니다.
  const start = () => transcode(file)
  const result = queue.then(start, start)
  queue = result.then(
    () => undefined,
    () => undefined,
  )
  return result
}

async function transcode(file) {
  const ffmpeg = await loadFFmpeg()
  sequence += 1
  const inputName = `input-${sequence}${extensionOf(file.name)}`
  const outputName = `output-${sequence}.mp4`

  try {
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

    const name = `${file.name.replace(/\.[^.]+$/, '')}-optimized.mp4`
    return new File([data], name, { type: 'video/mp4' })
  } finally {
    // 실패해도 치웁니다. 남겨 두면 다음 영상을 올릴 때까지 메모리에 그대로 있습니다.
    await ffmpeg.deleteFile(inputName).catch(() => {})
    await ffmpeg.deleteFile(outputName).catch(() => {})
  }
}
