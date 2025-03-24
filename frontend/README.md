ExClaim Frontend Development Guide
==================================

General Notes
-------------

The ExClaim Gradle build produces a `jar` file that contains a webserver along with the compiled backend and frontend code.
To do so, we have integrated the frontend compilation process into the Gradle build.
The `build.gradle.kts` file (in the `frontend` folder) configures Gradle to download Node.js, install all the dependencies and finally run `npm run build`.

If you do not want to change any of the frontend code, then you can just ignore the whole `frontend` subproject along with this readme file and let the Gradle build manage everything for you.


Project Setup
-------------

You need to install [Node.js](https://nodejs.org/).
Use the same major version as the `nodejs_version` configured in `../gradle.properties`.

### IDE Setup

Install [Visual Studio Code](https://code.visualstudio.com/) with the following extensions:

- [Vue](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (`Vue.volar`)

  If you have the deprecated [Vetur](https://marketplace.visualstudio.com/items?itemName=octref.vetur) extension installed, you need to disable it for this workspace or uninstall it entirely as Volar is the modern replacement for Vetur.

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

### Install and Update Dependencies

Before starting and every time `package.json` has been changed, you need to run:

```sh
npm install
```

To update dependencies within the ranges defined in `package.json`, run:

```sh
npm update
```

To update the ranges defined in `package.json`, you can use a tool like [`npm-check-updates`](https://www.npmjs.com/package/npm-check-updates).


Used Technologies
-----------------

The ExClaim frontend is a [Vue.js](https://vuejs.org/) application using TypeScript.
Good references are:
- [Vue.js Guide](https://vuejs.org/guide/).
  Make sure that the *Composition API* is selected (opposed to the *Options API*).
- [TypeScript Documentation](https://www.typescriptlang.org/docs/)


Development Workflow
--------------------

### Development Web Server
For frontend development, you should start the development web server.
You can choose between two modes:

- [Without a backend (using Mirage JS)](#without-a-backend-using-mirage-js)
- [With a real backend (Proxy mode)](#with-a-real-backend-proxy-mode)

It compiles the TypeScript code on demand, resulting in a fast startup time.
When you update (save) a source file, then the changes are automatically reflected in the web page you already have open (*"Hot Module Replacement"*).

The downside is that the development web server has no automatic type checking.
See [Type-checking, Linting and Formatting](#type-checking-linting-and-formatting) on how to manually check your code.

#### Without a backend (using Mirage JS)
In this mode, all requests to the backend will be handled by a mock created with [Mirage JS](https://miragejs.com/).
All available routes need to be configured in `src/mirage/index.ts` (TypeScript code), in addition to the implementation in the real Java-based backend.

A pseudo database is setup in `src/mirage/db`.
It holds some example users and exercises, such that we have data to render in the frontend.
The personal data (names, email, ...) is generated with [Faker](https://fakerjs.dev/), it is not actual production data where you need to worry about data protection.
Note that the state of this database is not persistent, i.e. all changes done in the frontend are lost when reloading the page or logging out.

Run

```sh
npm run dev
```

and open the URL that is printed to the console.
You can login with username `u1` and the `expectedPassword` configured in `src/mirage/index.ts`.
That account (user id 1) is an admin and assistant for all exercises.
You can use another id (`u2`, `u3`, ...) for accounts with different permissions.
The password is always the same.

#### With a real backend (Proxy mode)
In this mode, we combine the development web server with a real Java-based backend server.
You therefore do not need to recompile the frontend and restart the backend web server after every change, saving you a lot of time.
To do so, you first need to start the backend (e.g. run task `bootRunH2` in the Gradle project) and then provide the URL to the development web server as follows:

```sh
BACKEND_SERVER="http://localhost:8080" npm run dev
```

Open the URL that is printed to the console of the development web server.
It now serves any frontend code directly and proxies all api requests to the provided backend server url.
To login, you therefore need credentials that are valid for the backend server in use.


### Type-checking, Linting and Formatting
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

npm run check:typecheck-node
npm run check:typecheck-app
npm run check:prettier
npm run check:stylelint
npm run check:eslint
```

Note that the `check:typecheck-node` task checks checks the files listed in the `include` property in `tsconfig.node.json` while `check:typecheck-app` checks the actual application code.


### Modifying the API
The api is the contract between backend and frontend.
To improve type safety between our Java-based backend and our TypeScript-based frontend, we automatically generate TypeScript type definitions from Java types.

To modify the api, e.g. adding a new endpoint or change an existing endpoint, you need to:

- In the `../api` subproject, add/extend the Java classes you need.
- Run the Gradle task `:frontend:generateTypescriptApi` to build TypeScript type definitions out of those Java classes into `src/api/types.ts`.
  **Do not  modify this file manually, it will be overwritten on the next Gradle build.**
- When adding a new endpoint to the backend, add code to `src/api` for type-safe access.
  If you just extend the type of an existing endpoint there should be no change necessary here.
- Create/update the mock [for Mirage](#without-a-backend-using-mirage-js) in `src/mirage/index.ts` for the new/updated endpoint.
- Add/modify backend and frontend code using the new/updated endpoint.
