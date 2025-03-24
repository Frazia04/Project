<script lang="ts">
/* eslint-disable vue/no-v-text-v-html-on-component */

export default {
  inheritAttrs: false,
};
</script>

<script setup lang="ts">
import type { Component } from 'vue';

import { htmlErrorMessagePrefix } from './errors';

defineProps<{ tag: string | Component; msg?: string | null }>();
</script>

<template>
  <template v-if="msg">
    <component
      :is="tag"
      v-bind="$attrs"
      v-if="msg.startsWith(htmlErrorMessagePrefix)"
      v-html="msg.substring(htmlErrorMessagePrefix.length)"
    />
    <component :is="tag" v-bind="$attrs" v-else v-text="msg" />
  </template>
</template>
