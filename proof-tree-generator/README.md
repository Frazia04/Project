# Proof Tree Generator

## Recommended IDE Setup

[Visual Studio Code](https://code.visualstudio.com/) with the following extensions:

- [Vue Language Features (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (`Vue.volar`)

  If you have the deprecated [Vetur](https://marketplace.visualstudio.com/items?itemName=octref.vetur) extension installed, you need to disable it for this workspace or uninstall it entirely as Volar is the modern replacement for Vetur.

- [TypeScript Vue Plugin (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.vscode-typescript-vue-plugin) (`Vue.vscode-typescript-vue-plugin`)

  **Marketplace:** As of 2023-02-14, this extension is not available on [open-vsx.org](https://open-vsx.org/), an alternative marketplace that is used by many non-Microsoft builds of VS Code. You need to get it from Microsoft's official marketplace (e.g. by [updating your installation's `product.json`](https://stackoverflow.com/a/64537579)). Do not use the outdated `johnsoncodehk.vscode-typescript-vue-plugin` extension available from the Open VSX Registry.

  **Takeover Mode:** For improved performance, enable Volar's [Takeover Mode](https://vuejs.org/guide/typescript/overview.html#volar-takeover-mode) by disabling the built-in `TypeScript and JavaScript Language Features` extension for this workspace.

- [EditorConfig for VS Code](https://marketplace.visualstudio.com/items?itemName=EditorConfig.EditorConfig) (`EditorConfig.EditorConfig`)

- [Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) (`esbenp.prettier-vscode`)

  With the following configuration in `.vscode/settings.json`:

  ```json
  {
    "editor.formatOnSave": true,
    "editor.defaultFormatter": "esbenp.prettier-vscode"
  }
  ```

- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) (`dbaeumer.vscode-eslint`)

- [Stylelint](https://marketplace.visualstudio.com/items?itemName=stylelint.vscode-stylelint) (`stylelint.vscode-stylelint`)

  With the following configuration in `.vscode/settings.json`:

  ```json
  {
    "stylelint.validate": ["css", "scss"]
  }
  ```

## Project Setup

Before starting and every time `package.json` has been changed, you need to run:

```sh
npm install
```

## Development

To start the development web server, run:

```sh
npm run dev
```

To access the application, open the URL that is printed to the console.

**Note:** The typescript code is compiled on demand.
You can therefore get compile errors at runtime.
To type-check the whole code, run:

```sh
npm run check:typecheck
```

### Formatting and Linting

We use [Prettier](https://prettier.io/) for code formatting and [Stylelint](https://stylelint.io/) + [ESLint](https://eslint.org/) for linting.
For the best development experience, install the plugins [recommended above](#recommended-ide-setup).

To format your code, resolve auto-fixable linting issues and display remaining linting issues, run:

```sh
npm run lint
```

To check your code (type-check, formatting, linting) without applying any fixes, run:

```sh
npm run check
```

You can execute the individual tasks as follows:

```sh
npm run lint:prettier
npm run lint:stylelint
npm run lint:eslint

npm run check:typecheck
npm run check:prettier
npm run check:stylelint
npm run check:eslint
```

## Deployment

To compile the application in production mode, run:

```sh
npm run check
npm run build
```

The compiled code is in the `dist` folder and can be deployed.
To view it locally, run:

```sh
npm run preview
```

To access the preview server, open the URL that is printed to the console.
