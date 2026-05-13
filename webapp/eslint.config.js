// @ts-check
const eslint = require("@eslint/js");
const angular = require("angular-eslint");
const tseslint = require("typescript-eslint");

module.exports = tseslint.config(
  {
    ignores: [
      ".angular/**",
      "coverage/**",
      "dist/**",
      "node_modules/**",
      "out-tsc/**",
      "playwright-report/**",
      "test-results/**",
    ],
  },
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": "off",
      "@angular-eslint/component-selector": [
        "error",
        {
          type: "element",
          prefix: "app",
          style: "kebab-case",
        },
      ],
      "@angular-eslint/directive-selector": [
        "error",
        {
          type: "attribute",
          prefix: "app",
          style: "camelCase",
        },
      ],
    },
  },
  {
    files: ["**/*.html"],
    extends: [...angular.configs.templateRecommended],
    rules: {},
  },
);
