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

export function compressVideo(file, handlers = {}) {
  // 앞 작업이 실패해도 줄이 끊기지 않게 성공·실패 양쪽에서 이어 붙입니다.
  const start = () => transcode(file, handlers)
  const result = queue.then(start, start)
  queue = result.then(
    () => undefined,
    () => undefined,
  )
  return result
}

async function transcode(file, { onStart, onProgress } = {}) {
  const ffmpeg = await loadFFmpeg()
  // 줄을 서 있다가 이제 자기 차례가 됐다는 것을 알립니다. 이게 없으면 앞 영상을
  // 줄이는 동안 뒤 영상은 '0%'로 멈춰 있어, 멈춘 것처럼 보입니다.
  onStart?.()
  sequence += 1
  const inputName = `input-${sequence}${extensionOf(file.name)}`
  const outputName = `output-${sequence}.mp4`

  /*
    ffmpeg이 알려 주는 진행률을 그대로 쓰지 않고 다듬습니다. 값이 1을 넘기거나 뒤로
    가는 경우가 있어, 그대로 보여 주면 숫자가 줄어들었다 늘어납니다. 한 번 올라간
    값은 내리지 않고 99에서 멈춰 두었다가, 끝났을 때 100으로 맞춥니다.
  */
  let reported = 0
  const relay = ({ progress }) => {
    if (!onProgress || !Number.isFinite(progress)) return
    const next = Math.min(99, Math.max(reported, Math.round(progress * 100)))
    if (next === reported) return
    reported = next
    onProgress(next)
  }
  if (onProgress) ffmpeg.on('progress', relay)

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
    onProgress?.(100)

    const name = `${file.name.replace(/\.[^.]+$/, '')}-optimized.mp4`
    return new File([data], name, { type: 'video/mp4' })
  } finally {
    if (onProgress) ffmpeg.off('progress', relay)
    // 실패해도 치웁니다. 남겨 두면 다음 영상을 올릴 때까지 메모리에 그대로 있습니다.
    await ffmpeg.deleteFile(inputName).catch(() => {})
    await ffmpeg.deleteFile(outputName).catch(() => {})
  }
}
