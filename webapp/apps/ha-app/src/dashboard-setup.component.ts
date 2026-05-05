import {Component} from '@angular/core';
import {NgForOf} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';

type WidgetConfig = {
  title: string;
  icon: string;
  yaml: string;
  url: string;
};

@Component({
  selector: 'app-dashboard-setup',
  standalone: true,
  imports: [NgForOf, MatIconModule],
  templateUrl: './dashboard-setup.component.html',
  styleUrl: './dashboard-setup.component.css'
})
export class DashboardSetupComponent {
  copiedTitle = '';
  readonly widgets: WidgetConfig[];

  constructor() {
    const basePath = window.location.pathname.replace(/\/?$/, '/');
    this.widgets = [
      this.createWidget('Shopping List', 'shopping_cart', `${basePath}?widget=shoppingList`, '125%'),
      this.createWidget('Recipes', 'restaurant', `${basePath}?widget=recipeList`, '125%')
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
      yaml: [
        'type: iframe',
        `title: ${title}`,
        `url: ${url}`,
        `aspect_ratio: ${aspectRatio}`,
        'hide_background: true'
      ].join('\n')
    };
  }
}
