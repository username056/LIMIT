<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
  version: { type: String, required: true },
  sections: { type: Array, required: true },
})
</script>

<template>
  <div class="mx-auto grid max-w-[1200px] gap-8 px-5 py-10 lg:grid-cols-[220px_minmax(0,1fr)] lg:px-10 lg:py-14">
    <aside class="h-fit rounded-lg border border-border bg-white p-5 lg:sticky lg:top-6">
      <p class="text-xs font-bold uppercase tracking-[0.16em] text-primary">
        Policy
      </p>
      <nav
        class="mt-4 flex gap-2 overflow-x-auto lg:flex-col"
        aria-label="문서 목차"
      >
        <a
          v-for="(section, index) in sections"
          :key="section.id"
          :href="`#${section.id}`"
          class="whitespace-nowrap rounded-md px-3 py-2 text-sm text-text-sub hover:bg-accent hover:text-primary"
        >
          {{ index + 1 }}. {{ section.shortTitle || section.title }}
        </a>
      </nav>
    </aside>

    <article class="min-w-0 rounded-lg border border-border bg-white p-6 shadow-card sm:p-10">
      <div class="border-b border-border pb-8">
        <p class="text-sm font-semibold text-primary">
          L1MIT 정책
        </p>
        <h1 class="mt-3 text-3xl font-bold tracking-[-0.03em] text-text-main sm:text-4xl">
          {{ title }}
        </h1>
        <p class="mt-4 max-w-3xl text-sm leading-7 text-text-sub">
          {{ description }}
        </p>
        <p class="mt-4 text-xs font-medium text-text-sub">
          시행일 및 버전: {{ version }}
        </p>
      </div>

      <div class="divide-y divide-border">
        <section
          v-for="(section, index) in sections"
          :id="section.id"
          :key="section.id"
          class="scroll-mt-8 py-8"
        >
          <h2 class="text-xl font-bold text-text-main">
            제{{ index + 1 }}조 {{ section.title }}
          </h2>
          <p
            v-if="section.intro"
            class="mt-4 whitespace-pre-line text-sm leading-7 text-text-sub"
          >
            {{ section.intro }}
          </p>
          <ul
            v-if="section.items"
            class="mt-4 space-y-3 text-sm leading-7 text-text-sub"
          >
            <li
              v-for="(item, itemIndex) in section.items"
              :key="itemIndex"
              class="flex gap-3"
            >
              <span class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
              <span>{{ item }}</span>
            </li>
          </ul>
          <div
            v-if="section.table"
            class="mt-5 overflow-x-auto rounded-md border border-border"
          >
            <table class="w-full min-w-[620px] border-collapse text-left text-sm">
              <thead class="bg-bg text-text-main">
                <tr>
                  <th
                    v-for="heading in section.table.headings"
                    :key="heading"
                    class="px-4 py-3 font-semibold"
                  >
                    {{ heading }}
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border text-text-sub">
                <tr
                  v-for="(row, rowIndex) in section.table.rows"
                  :key="rowIndex"
                >
                  <td
                    v-for="(cell, cellIndex) in row"
                    :key="cellIndex"
                    class="whitespace-pre-line px-4 py-3 align-top leading-6"
                  >
                    {{ cell }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </article>
  </div>
</template>
