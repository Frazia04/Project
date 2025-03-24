// vite.config.ts: build.define
declare const __DEV__: boolean;
declare const __APP_VERSION__: string;
declare const __BACKEND_SERVER__: string | undefined;
declare const __MIRAGE__: boolean;

// vite.config.ts: svg-to-component-loader
declare module '*.svg' {
  import { FunctionalComponent, SVGAttributes } from 'vue';
  const src: FunctionalComponent<SVGAttributes>;
  export default src;
}

// Extend the `Window` interface with properties that are defined in the html file delivered by the backend
interface Window {
  frontendConfiguration?: import('./src/api/types').Configuration;
  accountData?: import('./src/api/types').AccountData | null;
  csrfToken?: string | null;
}
