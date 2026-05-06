import {Component} from '@angular/core';
import {NgIf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';

type LovelaceResourceStatus = {
  url: string;
};

@Component({
  selector: 'app-dashboard-setup',
  standalone: true,
  imports: [NgIf, MatIconModule],
  templateUrl: './dashboard-setup.component.html',
  styleUrl: './dashboard-setup.component.css'
})
export class DashboardSetupComponent {
  resourceInstallStatus = '';
  installingResource = false;
  checkingResource = false;

  async installLovelaceResource() {
    const token = localStorage.getItem('token');
    this.installingResource = true;
    this.resourceInstallStatus = '';

    try {
      const response = await fetch('api/ha/lovelace-resource', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token ?? ''}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          ingressBaseUrl: window.location.pathname.replace(/\/?$/, '/')
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.message || 'Could not install Lovelace resource');
      }
      this.resourceInstallStatus = body.message || 'Lovelace resource installed';
    } catch (error) {
      this.resourceInstallStatus = error instanceof Error ? error.message : 'Could not install Lovelace resource';
    } finally {
      this.installingResource = false;
    }
  }

  async checkLovelaceResource() {
    const token = localStorage.getItem('token');
    this.checkingResource = true;
    this.resourceInstallStatus = '';

    try {
      const response = await fetch('api/ha/lovelace-resource', {
        headers: {
          'Authorization': `Bearer ${token ?? ''}`
        }
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.message || 'Could not read Lovelace resource');
      }
      const resources = body.resources ?? [];
      const resourceMessage = resources.length
        ? `Resource: ${resources.map((resource: LovelaceResourceStatus) => resource.url).join(', ')}`
        : 'No Ktor Lovelace resource found';
      this.resourceInstallStatus = `${body.published ? 'Home Assistant www file exists' : 'Home Assistant www file missing'} - ${resourceMessage}`;
    } catch (error) {
      this.resourceInstallStatus = error instanceof Error ? error.message : 'Could not read Lovelace resource';
    } finally {
      this.checkingResource = false;
    }
  }

}
