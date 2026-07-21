<script setup>
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip } from 'chart.js'

ChartJS.register(ArcElement, Tooltip)

const props = defineProps({
  percent: { type: Number, required: true }, // 0~100
})

const chartData = computed(() => ({
  datasets: [
    {
      data: [props.percent, 100 - props.percent],
      backgroundColor: ['#FFFFFF', 'rgba(255,255,255,0.25)'],
      borderWidth: 0,
      cutout: '75%',
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false }, tooltip: { enabled: false } },
}
</script>

<template>
  <div class="relative h-32 w-32">
    <Doughnut
      :data="chartData"
      :options="chartOptions"
    />
    <div class="absolute inset-0 flex items-center justify-center text-xl font-bold text-white">
      {{ percent }}%
    </div>
  </div>
</template>
