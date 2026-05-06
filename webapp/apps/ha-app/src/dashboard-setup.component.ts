import {Component} from '@angular/core';
import {NgForOf, NgIf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';

type CardSetupConfig = {
  title: string;
  icon: string;
  yaml: string;
  url: string;
  actionLabel: string;
};

const LOVELACE_CARD_VERSION = '1.1.2';

@Component({
  selector: 'app-dashboard-setup',
  standalone: true,
  imports: [NgForOf, NgIf, MatIconModule],
  templateUrl: './dashboard-setup.component.html',
  styleUrl: './dashboard-setup.component.css'
})
export class DashboardSetupComponent {
  copiedTitle = '';
  resourceInstallStatus = '';
  installingResource = false;
  readonly nativeCards: CardSetupConfig[];

  constructor() {
    const basePath = window.location.pathname.replace(/\/?$/, '/');
    const lovelaceResourceUrl = `${basePath}ktor-lovelace-cards.js?v=${LOVELACE_CARD_VERSION}`;
    this.nativeCards = [
      {
        title: 'Lovelace resource',
        icon: 'extension',
        url: lovelaceResourceUrl,
        actionLabel: 'Copy resource YAML',
        yaml: [
          'url: ' + lovelaceResourceUrl,
          'type: module'
        ].join('\n')
      },
      this.createNativeCard('Shopping List Card', 'shopping_cart', 'ktor-shopping-list-card', [
        'title: Shopping List',
        'max_items: 12',
        'show_completed: true'
      ])
    ];
  }

  async copy(card: CardSetupConfig) {
    await navigator.clipboard.writeText(card.yaml);
    this.copiedTitle = card.title;
    window.setTimeout(() => {
      if (this.copiedTitle === card.title) {
        this.copiedTitle = '';
      }
    }, 1800);
  }

  async installLovelaceResource() {
    const resource = this.nativeCards[0];
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
        body: JSON.stringify({url: resource.url})
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

  private createNativeCard(title: string, icon: string, type: string, configLines: string[]): CardSetupConfig {
    return {
      title,
      icon,
      url: '',
      actionLabel: 'Copy card YAML',
      yaml: [
        `type: custom:${type}`,
        ...configLines
      ].join('\n')
    };
  }
}
