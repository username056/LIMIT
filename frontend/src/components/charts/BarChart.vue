<script setup>
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  BarElement,
  CategoryScale,
  LinearScale,
} from 'chart.js'

ChartJS.register(Title, Tooltip, BarElement, CategoryScale, LinearScale)

const props = defineProps({
  labels: { type: Array, required: true }, // ['05.24', '05.25', ...]
  values: { type: Array, required: true }, // [12, 18, 25, ...]
})

// 데이터가 비동기로 채워질 수 있어 computed로 두어야 갱신이 반영됩니다.
const chartData = computed(() => ({
  labels: props.labels,
  datasets: [
    {
      data: props.values,
      backgroundColor: 'rgba(108, 141, 255, 0.35)',
      hoverBackgroundColor: '#6C8DFF',
      borderRadius: 6,
      maxBarThickness: 28,
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: {
    x: { grid: { display: false } },
    y: { grid: { color: '#E2E8F5' }, beginAtZero: true },
  },
}
</script>

<template>
  <div class="h-56">
    <Bar
      :data="chartData"
      :options="chartOptions"
    />
  </div>
</template>
