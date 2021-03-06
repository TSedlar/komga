<template>
  <div>
    <toolbar-sticky>
      <v-toolbar-title>
        <span>Search results for "{{ $route.query.q }}"</span>
      </v-toolbar-title>
    </toolbar-sticky>

    <v-container fluid class="px-6">
      <empty-state
        v-if="emptyResults"
        title="The search returned no results"
        sub-title="Try searching for something else"
        icon="mdi-magnify"
        icon-color="secondary"
        class="my-4"
      >
      </empty-state>

      <template v-else>
        <horizontal-scroller v-if="series.length !== 0" class="my-4">
          <template v-slot:prepend>
            <div class="title">Series</div>
          </template>
          <template v-slot:content>
            <div v-for="(s, i) in series"
                 :key="i">
              <item-card class="ma-2 card" :item="s"/>
            </div>
          </template>
        </horizontal-scroller>

        <horizontal-scroller v-if="books.length !== 0" class="my-4">
          <template v-slot:prepend>
            <div class="title">Books</div>
          </template>
          <template v-slot:content>
            <div v-for="(s, i) in books"
                 :key="i">
              <item-card class="ma-2 card" :item="s"/>
            </div>
          </template>
        </horizontal-scroller>

      </template>
    </v-container>

  </div>
</template>

<script lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import HorizontalScroller from '@/components/HorizontalScroller.vue'
import ItemCard from '@/components/ItemCard.vue'
import ToolbarSticky from '@/components/ToolbarSticky.vue'
import Vue from 'vue'

const cookiePageSize = 'pagesize'

export default Vue.extend({
  name: 'Search',
  components: {
    EmptyState,
    ToolbarSticky,
    HorizontalScroller,
    ItemCard,
  },
  data: () => {
    return {
      series: [] as SeriesDto[],
      books: [] as BookDto[],
      pageSize: 50,
      loading: false,
    }
  },
  watch: {
    '$route.query.q': {
      handler: function (val) {
        this.loadResults(val)
      },
      deep: true,
      immediate: true,
    },
  },
  computed: {
    emptyResults (): boolean {
      return !this.loading && this.series.length === 0 && this.books.length === 0
    },
  },
  methods: {
    async loadResults (search: string) {
      if (search) {
        this.loading = true

        this.series = (await this.$komgaSeries.getSeries(undefined, { size: this.pageSize }, search)).content
        this.books = (await this.$komgaBooks.getBooks(undefined, { size: this.pageSize }, search)).content

        this.loading = false
      } else {
        this.series = []
        this.books = []
      }
    },
  },
})
</script>
<style scoped>
</style>
