import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { APP_BASE_HREF } from '@angular/common';

const pathParts = window.location.pathname.split('/').filter(p => p);
const baseHref = (pathParts[0] === 'app' && pathParts[1]) ? '/app/' + pathParts[1] + '/' : '/';
const config = { ...appConfig, providers: [...(appConfig.providers || []), { provide: APP_BASE_HREF, useValue: baseHref }] };

bootstrapApplication(AppComponent, config)
  .catch((err) => console.error(err));
