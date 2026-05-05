import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app.config';
import { AppComponent } from './app.component';
import { APP_BASE_HREF } from '@angular/common';

// Hash routing and document-relative assets keep this ingress-safe.
const baseHref = './';
const config = {
  ...appConfig,
  providers: [
    ...(appConfig.providers || []),
    { provide: APP_BASE_HREF, useValue: baseHref }
  ]
};

bootstrapApplication(AppComponent, config)
  .catch((err) => console.error(err));

