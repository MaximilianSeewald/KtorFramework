export interface IEnvironment {
  production: boolean;
  apiUrl: string;
  wsUrl: string;
}

// This will be replaced at build time
export const environment: IEnvironment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'ws://localhost:8080/api'
};

