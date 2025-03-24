module.exports = {
  root: true,
  extends: ['eslint:recommended', 'plugin:import/recommended', 'prettier'],
  plugins: ['simple-import-sort'],
  rules: {
    // Cyclic value imports are not supported by vite/esbuild
    'import/no-cycle': 'error',

    // Allow `let` for destructuring when any variable is mutated
    'prefer-const': ['error', { destructuring: 'all' }],

    // Enforce consistent order of imports
    'simple-import-sort/imports': 'warn',
  },
  overrides: [
    {
      // Additional configuration for TypeScript files
      files: ['*.ts', '*.d.ts', '*.vue'],
      extends: [
        'plugin:@typescript-eslint/recommended',
        'plugin:@typescript-eslint/recommended-requiring-type-checking',
        'plugin:@typescript-eslint/strict',
        'plugin:import/typescript',
        'plugin:vue/vue3-recommended',

        // Prettier needs to override some plugin rules added here, so repeat it for higher precedence
        'prettier',
      ],
      parserOptions: {
        parser: '@typescript-eslint/parser',
        extraFileExtensions: ['.vue'],
        tsconfigRootDir: __dirname,
        project: ['./tsconfig.json', './tsconfig.app.json', './tsconfig.node.json'],
      },
      rules: {
        // Disable rules already covered by TypeScript
        'no-undef': 'off',
        'import/default': 'off',
        'import/named': 'off',
        'import/namespace': 'off',
        'import/no-named-as-default-member': 'off',

        // The no-unnecessary-* rules have false positives (thereby removing required type arguments / assertions!)
        '@typescript-eslint/no-unnecessary-type-arguments': 'off',
        '@typescript-eslint/no-unnecessary-type-assertion': 'off',

        // The no-unsafe-* rules have false positives
        '@typescript-eslint/no-unsafe-argument': 'off',
        '@typescript-eslint/no-unsafe-assignment': 'off',
        '@typescript-eslint/no-unsafe-call': 'off',
        '@typescript-eslint/no-unsafe-member-access': 'off',
        '@typescript-eslint/no-unsafe-return': 'off',

        // Custom code style
        '@typescript-eslint/explicit-function-return-type': ['warn', { allowExpressions: true }],
        '@typescript-eslint/explicit-member-accessibility': [
          'error',
          { accessibility: 'explicit', overrides: { constructors: 'no-public' } },
        ],
        '@typescript-eslint/no-unused-vars': ['warn', { varsIgnorePattern: '^_' }],

        // Sometimes we need any or non-null assertions
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/no-non-null-assertion': 'off',

        // Additional rules
        'no-param-reassign': 'error',
      },
    },
    {
      // Additional configuration for Node.js files in root folder
      files: ['./*.cjs', './*.js'],
      parserOptions: { ecmaVersion: 'latest' },
      env: { node: true },
    },
  ],
};
