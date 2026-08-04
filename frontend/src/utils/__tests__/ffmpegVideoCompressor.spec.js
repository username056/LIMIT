import { beforeEach, describe, expect, it, vi } from 'vitest'

/*
  영상을 여러 개 한꺼번에 고르면 업로드가 실패하던 문제를 막는 테스트입니다.
  ffmpeg 인스턴스는 하나를 같이 쓰므로, 같은 파일 이름을 겹쳐 쓰거나 변환이 겹치면
  먼저 끝난 쪽이 남의 파일을 지워 버립니다.
*/
const state = vi.hoisted(() => ({
  // 가상 파일 시스템을 흉내 냅니다. 이름이 겹치면 여기서 바로 드러납니다.
  files: new Map(),
  running: 0,
  maxConcurrent: 0,
  execOrder: [],
}))

vi.mock('@ffmpeg/ffmpeg', () => ({
  FFmpeg: class {
    load() { return Promise.resolve() }

    writeFile(name, data) {
      state.files.set(name, data)
      return Promise.resolve()
    }

    async exec(args) {
      state.running += 1
      state.maxConcurrent = Math.max(state.maxConcurrent, state.running)
      const input = args[args.indexOf('-i') + 1]
      const output = args[args.length - 1]
      state.execOrder.push(input)
      // 변환에 시간이 걸리는 동안 다른 호출이 끼어드는지 봅니다.
      await new Promise((resolve) => setTimeout(resolve, 5))
      if (!state.files.has(input)) {
        state.running -= 1
        throw new Error(`입력 파일이 사라졌습니다: ${input}`)
      }
      state.files.set(output, state.files.get(input))
      state.running -= 1
    }

    readFile(name) {
      if (!state.files.has(name)) return Promise.reject(new Error(`없는 파일: ${name}`))
      return Promise.resolve(state.files.get(name))
    }

    deleteFile(name) {
      state.files.delete(name)
      return Promise.resolve()
    }
  },
}))

vi.mock('@ffmpeg/util', () => ({
  fetchFile: (file) => Promise.resolve(new Uint8Array([file.name.length])),
  toBlobURL: (url) => Promise.resolve(url),
}))

vi.mock('@ffmpeg/core?url', () => ({ default: 'core.js' }))
vi.mock('@ffmpeg/core/wasm?url', () => ({ default: 'core.wasm' }))

const { compressVideo } = await import('../ffmpegVideoCompressor.js')

describe('compressVideo', () => {
  beforeEach(() => {
    state.files.clear()
    state.running = 0
    state.maxConcurrent = 0
    state.execOrder = []
  })

  it('영상을 여러 개 한꺼번에 넘겨도 전부 변환된다', async () => {
    const files = ['a.mov', 'b.mp4', 'c.avi'].map((name) => new File(['x'], name, { type: 'video/mp4' }))

    const results = await Promise.all(files.map((file) => compressVideo(file)))

    expect(results.map((file) => file.name)).toEqual([
      'a-optimized.mp4', 'b-optimized.mp4', 'c-optimized.mp4',
    ])
    expect(results.every((file) => file.type === 'video/mp4')).toBe(true)
  })

  it('변환은 한 번에 하나씩만 돌린다', async () => {
    const files = ['a.mp4', 'b.mp4', 'c.mp4'].map((name) => new File(['x'], name, { type: 'video/mp4' }))

    await Promise.all(files.map((file) => compressVideo(file)))

    // wasm 변환은 CPU를 다 쓰므로 동시에 돌린다고 빨라지지 않고 메모리만 몇 배가 됩니다.
    expect(state.maxConcurrent).toBe(1)
  })

  it('호출마다 다른 파일 이름을 써서 서로 덮어쓰지 않는다', async () => {
    const files = ['a.mp4', 'b.mp4'].map((name) => new File(['x'], name, { type: 'video/mp4' }))

    await Promise.all(files.map((file) => compressVideo(file)))

    expect(new Set(state.execOrder).size).toBe(state.execOrder.length)
  })

  it('변환이 끝나면 가상 파일 시스템에 아무것도 남기지 않는다', async () => {
    await compressVideo(new File(['x'], 'a.mp4', { type: 'video/mp4' }))

    expect(state.files.size).toBe(0)
  })

  it('한 영상이 실패해도 다음 영상은 변환된다', async () => {
    // 이름 없는 파일로 변환을 깨뜨립니다.
    const broken = new File(['x'], 'a.mp4', { type: 'video/mp4' })
    const ok = new File(['x'], 'b.mp4', { type: 'video/mp4' })
    const original = state.files.set.bind(state.files)
    let first = true
    state.files.set = (name, data) => {
      if (first && name.startsWith('input-')) {
        first = false
        return state.files // 첫 영상만 입력이 쓰이지 않은 것처럼 만듭니다.
      }
      return original(name, data)
    }

    const results = await Promise.allSettled([compressVideo(broken), compressVideo(ok)])
    state.files.set = original

    expect(results[0].status).toBe('rejected')
    expect(results[1].status).toBe('fulfilled')
    expect(results[1].value.name).toBe('b-optimized.mp4')
  })
})
