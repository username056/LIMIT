import lapExt002 from '../assets/checklist-guides/lap-ext-002.jpg'
import dsp002Touch from '../assets/checklist-guides/dsp-002-touch.jpg'
import lapScr013 from '../assets/checklist-guides/lap-scr-013.png'
import lapBat010 from '../assets/checklist-guides/lap-bat-010.png'
import lapSys011 from '../assets/checklist-guides/lap-sys-011.png'
import genericPhoto from '../assets/checklist-guides/generic-photo.svg'
import genericVideo from '../assets/checklist-guides/generic-video.svg'
import genericFile from '../assets/checklist-guides/generic-file.svg'

// 필수(isRequired) 항목에만 쓰는, 카테고리별 안내 사진·설명입니다.
// 비필수 항목은 이 파일을 아예 거치지 않고 서버 guide 문구 + 범용 이미지를 그대로 씁니다.
//
// 채워 넣는 방법:
//   1) 실사진을 frontend/src/assets/checklist-guides/에 추가하고 위쪽에 import
//   2) 아래 카테고리 객체 안에 실제 itemCode를 key로 { image, purpose, guide } 추가
// 카테고리 이름은 상품 등록 화면의 카테고리 드롭다운(getDeviceCategories 응답의 name)과
// 정확히 같은 문자열이어야 매칭됩니다.
const CHECKLIST_GUIDE_CONTENT = {
  // 일반형 스마트폰·폴더블 스마트폰·태블릿은 아직 실제 items 응답을 확인 못 해서
  // 비워 뒀습니다 - 지금은 항상 서버 guide 문구 + 범용 이미지(디폴트)로 표시됩니다.
  // 실제 응답을 주시면 Windows 노트북과 같은 방식으로 채워 넣으면 됩니다.

  // 2026-08 체크리스트 생성 응답(deviceModelId 13, Galaxy Book4)의 items 6개를 그대로
  // 반영했습니다. aiSuggestions(포트·스피커 등 선택 기능 항목)는 필수 항목이 아니라 제외했습니다.
  'Windows 노트북': {
    'EXT-001': {
      image: lapExt002,
      purpose: '외관 손상 여부 확인 (찍힘·균열·변색 및 힌지 유격)',
      guide: '기기의 전면·후면 및 사면 테두리가 모두 잘 보이도록 밝은 곳에서 촬영해 주세요. 특히 손상 여부가 사진에서 명확히 드러나도록 근접 촬영을 포함해 주시기 바랍니다.',
    },
    'DSP-002': {
      image: dsp002Touch,
      purpose: '디스플레이 화면, 키보드 및 터치 입력 정상 작동 확인',
      guide: '화면의 잔상 및 불량 화소 여부, 화면 전체 격자를 끊김 없이 드래그하는 터치 반응, 주요 키보드 입력 시 반응 상태가 한 화면에 잘 나타나도록 동영상으로 촬영해 주세요.',
    },
    'SYS-003': {
      image: lapScr013,
      purpose: '모델명·저장 용량·OS 버전·프로세서 확인',
      guide: '기기의 [설정 > 시스템 > 정보] 탭으로 이동하신 후, 해당 화면을 스크린샷하여 사진으로 업로드해 주세요.',
    },
    'BAT-005': {
      image: lapBat010,
      purpose: '배터리 설계 용량·완전 충전 용량·충전 사이클 수·제조사·용량 비율 확인',
      guide: 'CMD 창에 "powercfg /batteryreport" 입력 후 출력된 경로의 battery-report.html 파일을 찾아 업로드해 주세요.',
    },
    'DXD-006': {
      image: lapSys011,
      purpose: 'CPU·메모리·GPU·그래픽 드라이버 버전·사운드 장치 정보 확인',
      guide: '[Windows + R] 키를 눌러 "dxdiag"를 입력해 실행 후, 하단의 "모든 정보 저장" 버튼을 눌러 생성된 DxDiag.txt 파일을 업로드해 주세요.',
    },
  },
}

const GENERIC_IMAGE_BY_EVIDENCE_TYPE = {
  PHOTO: genericPhoto,
  VIDEO: genericVideo,
  DIAGNOSTIC_FILE: genericFile,
}

// 필수 항목이면서 카테고리+itemCode가 등록돼 있을 때만 값을 반환합니다.
// 비필수 항목이거나 아직 등록 안 된 조합이면 null - 호출부에서 서버 기본값/범용 이미지로 대체합니다.
//
// Lombok이 boolean 필드 isRequired에 isRequired() getter를 만들고 Jackson이 is 접두어를
// 벗겨내면서 실제 JSON 키가 isRequired가 아니라 required로 내려오는 응답도 있어 둘 다 봅니다.
export function guideContentFor(item, categoryName) {
  const required = item?.required ?? item?.isRequired ?? false
  if (!required) return null
  return CHECKLIST_GUIDE_CONTENT[categoryName]?.[item.itemCode] || null
}

export function guideImageFor(item, categoryName) {
  return guideContentFor(item, categoryName)?.image
    || GENERIC_IMAGE_BY_EVIDENCE_TYPE[item?.evidenceType]
    || null
}
