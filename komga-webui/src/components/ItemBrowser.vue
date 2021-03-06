<template>
  <v-item-group multiple v-model="selectedItems">
    <v-row justify="start" ref="content" v-resize="onResize" v-if="hasItems">
      <v-item
        v-for="(item, index) in items"
        :key="index"
        class="my-3 mx-2"
        v-slot:default="{ toggle, active }" :value="$_.get(item, 'id', 0)"
      >
        <slot name="item"
              v-bind:data="{ toggle, active, item, index, itemWidth, preselect: shouldPreselect(), editItem }">
          <item-card
            :item="item"
            :width="itemWidth"
            :selected="active"
            :preselect="shouldPreselect()"
            :onEdit="editItem"
            :onSelected="toggle"
          ></item-card>
        </slot>
      </v-item>
    </v-row>
    <v-row v-else justify="center">
      <slot name="empty"></slot>
    </v-row>
  </v-item-group>
</template>

<script lang="ts">
import ItemCard from '@/components/ItemCard.vue'
import { computeCardWidth } from '@/functions/grid-utilities'
import Vue from 'vue'

export default Vue.extend({
  name: 'ItemBrowser',
  components: { ItemCard },
  props: {
    items: {
      type: Array,
      required: true,
    },
    selected: {
      type: Array,
      required: true,
    },
    editFunction: {
      type: Function,
    },
    resizeFunction: {
      type: Function,
    },
  },
  data: () => {
    return {
      selectedItems: [],
      width: 150,
    }
  },
  watch: {
    selectedItems: {
      handler () {
        this.$emit('update:selected', this.selectedItems)
      },
      immediate: true,
    },
    selected: {
      handler () {
        this.selectedItems = this.selected as []
      },
      immediate: true,
    },
  },
  computed: {
    hasItems (): boolean {
      return this.items.length > 0
    },
    itemWidth (): number {
      return this.width
    },
    itemHeight (): number {
      return this.width / 0.7071 + 116
    },
  },
  methods: {
    shouldPreselect (): boolean {
      return this.selectedItems.length > 0
    },
    editItem (item: any) {
      this.editFunction(item)
    },
    onResize () {
      const content = this.$refs.content as HTMLElement
      this.width = computeCardWidth(content.clientWidth, this.$vuetify.breakpoint.name)
    },
  },
})
</script>

<style scoped>

</style>
