import { createApp } from 'vue'
import { createPinia } from 'pinia';
import App from './App.vue'
import './assets/app.css';
import './assets/diagram.css';
import './assets/vars.css';

createApp(App).use(createPinia()).mount('#app');