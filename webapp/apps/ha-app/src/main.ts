import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app.config';
import { AppComponent } from './app.component';
import { APP_BASE_HREF } from '@angular/common';

// Home Assistant specific base href handling
const baseHref = '/api/hassio_ingress/';
const config = {
  ...appConfig,
  providers: [
    ...(appConfig.providers || []),
    { provide: APP_BASE_HREF, useValue: baseHref }
  ]
};

bootstrapApplication(AppComponent, config)
  .catch((err) => console.error(err));

