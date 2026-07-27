<script setup>
defineProps({
  columns: { type: Array, required: true }, // ['드롭 명', '정산 예정일', ...]
})
</script>

<template>
  <div class="overflow-x-auto rounded-lg border border-border bg-surface">
    <table class="w-full text-left text-sm">
      <thead class="border-b border-border bg-bg">
        <tr>
          <th
            v-for="col in columns"
            :key="col"
            class="px-4 py-3 text-xs font-semibold text-text-sub"
          >
            {{ col }}
          </th>
        </tr>
      </thead>
      <tbody class="divide-y divide-border">
        <slot />
      </tbody>
    </table>
  </div>
</template>
