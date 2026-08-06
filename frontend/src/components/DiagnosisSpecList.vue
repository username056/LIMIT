<script setup>
import { ref } from 'vue'
import { diagnosisFieldLabel, diagnosisFieldValue } from '../utils/diagnosisFields'

defineProps({
  items: {
    type: Array,
    required: true,
  },
})

// 값이 길면 잘려(...) 보입니다. 커서를 올리면 원문 전체를 툴팁으로 보여 줍니다.
// 눌러서 펼치는 방식은 누를 수 있다는 걸 먼저 알아야 해서 올려놓기만 해도 보이게 했습니다.
const activeField = ref(null)
</script>

<template>
  <dl class="spec-list mt-2">
    <div
      v-for="item in items"
      :key="item.fieldName"
      class="spec-row relative"
    >
      <dt class="w-[130px] shrink-0 text-[13px] font-medium text-text-sub">
        {{ diagnosisFieldLabel(item.fieldName) }}
      </dt>
      <dd class="min-w-0 flex-1 text-right">
        <span
          v-if="item.status === 'AVAILABLE'"
          class="block cursor-default truncate text-sm font-semibold text-text-main"
          tabindex="0"
          @mouseenter="activeField = item.fieldName"
          @mouseleave="activeField = null"
          @focus="activeField = item.fieldName"
          @blur="activeField = null"
        >
          {{ diagnosisFieldValue(item.fieldName, item.value) }}
        </span>
        <!-- 인식하지 못한 값은 흐리게 둡니다. 읽을 게 없는 줄에 시선이 가면 안 됩니다. -->
        <span
          v-else
          class="block truncate text-sm font-normal text-slate-300"
        >
          인식 실패
        </span>
      </dd>
      <div
        v-if="activeField === item.fieldName"
        role="tooltip"
        class="diagnosis-tooltip pointer-events-none absolute right-0 top-full z-10 mt-1 max-w-[min(20rem,80vw)] whitespace-normal break-words rounded-md bg-slate-800 px-3 py-1.5 text-left text-xs font-normal text-white shadow-elevated"
      >
        {{ diagnosisFieldValue(item.fieldName, item.value) }}
      </div>
    </div>
  </dl>
</template>

<style scoped>
/* 자동 인식 사양: 옅은 카드 하나에 구분선으로만 행을 나눕니다. */
.spec-list {
  background-color: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 16px;
  padding: 0 16px;
}

.spec-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #e2e8f0;
}

.spec-row:last-child {
  border-bottom: none;
}

/* 툴팁이 툭 튀어나오지 않고 짧게 떠오르게 합니다. */
.diagnosis-tooltip {
  animation: tooltip-in 140ms ease-out;
}

@keyframes tooltip-in {
  from { opacity: 0; transform: translateY(-2px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .diagnosis-tooltip {
    animation: none;
  }
}
</style>
