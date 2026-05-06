import {Component} from '@angular/core';
import {NgForOf, NgIf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';

type WidgetConfig = {
  title: string;
  icon: string;
  yaml: string;
  url: string;
  actionLabel: string;
};

@Component({
  selector: 'app-dashboard-setup',
  standalone: true,
  imports: [NgForOf, NgIf, MatIconModule],
  templateUrl: './dashboard-setup.component.html',
  styleUrl: './dashboard-setup.component.css'
})
export class DashboardSetupComponent {
  copiedTitle = '';
  readonly widgets: WidgetConfig[];
  readonly nativeCards: WidgetConfig[];

  constructor() {
    const basePath = window.location.pathname.replace(/\/?$/, '/');
    this.widgets = [
      this.createWidget('Shopping List', 'shopping_cart', `${basePath}?widget=shoppingList`, '125%'),
      this.createWidget('Recipes', 'restaurant', `${basePath}?widget=recipeList`, '125%')
    ];
    this.nativeCards = [
      {
        title: 'Lovelace resource',
        icon: 'extension',
        url: `${basePath}ktor-lovelace-cards.js`,
        actionLabel: 'Copy resource YAML',
        yaml: [
          'url: ' + `${basePath}ktor-lovelace-cards.js`,
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

  async copy(widget: WidgetConfig) {
    await navigator.clipboard.writeText(widget.yaml);
    this.copiedTitle = widget.title;
    window.setTimeout(() => {
      if (this.copiedTitle === widget.title) {
        this.copiedTitle = '';
      }
    }, 1800);
  }

  private createWidget(title: string, icon: string, url: string, aspectRatio: string): WidgetConfig {
    return {
      title,
      icon,
      url,
      actionLabel: 'Copy card YAML',
      yaml: [
        'type: iframe',
        `title: ${title}`,
        `url: ${url}`,
        `aspect_ratio: ${aspectRatio}`,
        'hide_background: true'
      ].join('\n')
    };
  }

  private createNativeCard(title: string, icon: string, type: string, configLines: string[]): WidgetConfig {
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
