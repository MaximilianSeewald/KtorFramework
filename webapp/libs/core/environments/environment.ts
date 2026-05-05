export interface IEnvironment {
  production: boolean;
  apiUrl: string;
  wsUrl: string;
  haAutoLogin?: boolean;
}

// This will be replaced at build time
export const environment: IEnvironment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'ws://localhost:8080/api',
  haAutoLogin: false
};

