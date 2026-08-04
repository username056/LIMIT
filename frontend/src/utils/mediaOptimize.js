const IMAGE_MAX_DIMENSION = 1600
const IMAGE_QUALITY = 0.82

export async function compressImage(file) {
  if (!file.type.startsWith('image/')) return file

  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, IMAGE_MAX_DIMENSION / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * scale)
  const height = Math.round(bitmap.height * scale)

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  canvas.getContext('2d').drawImage(bitmap, 0, 0, width, height)
  bitmap.close?.()

  const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', IMAGE_QUALITY))
  if (!blob || blob.size >= file.size) return file

  const name = `${file.name.replace(/\.[^.]+$/, '')}.jpg`
  return new File([blob], name, { type: 'image/jpeg' })
}

// ffmpeg.wasm은 무거워서(코어 파일 수십 MB) 촬영/업로드 화면에 실제로 진입했을 때만
// 동적 import로 불러옵니다. 다른 페이지의 번들 크기에는 영향을 주지 않습니다.
export async function compressVideo(file, handlers = {}) {
  const { compressVideo: run } = await import('./ffmpegVideoCompressor.js')
  return run(file, handlers)
}
