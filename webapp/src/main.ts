import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { APP_BASE_HREF } from '@angular/common';

const baseHref = window.location.pathname.startsWith('/app/') ? '/app/' : '/';
const config = { ...appConfig, providers: [...(appConfig.providers || []), { provide: APP_BASE_HREF, useValue: baseHref }] };

bootstrapApplication(AppComponent, config)
  .catch((err) => console.error(err));
