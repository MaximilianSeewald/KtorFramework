import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgForOf, NgIf } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ShoppingListService } from './shopping-list.service';
import { ErrorService } from '@core/services/error.service';
import { ShoppingListItemExtended } from '@core/models/shopping-list.model';

@Component({
  selector: 'app-shopping-list',
  imports: [
    FormsModule,
    NgForOf,
    NgIf,
    MatIconModule
  ],
  templateUrl: './shopping-list.component.html',
  standalone: true,
  styleUrl: './shopping-list.component.css'
})
export class ShoppingListComponent implements OnInit, OnDestroy {

  shoppingList: ShoppingListItemExtended[] = [];
  newItemName = '';

  constructor(
    private shoppingListService: ShoppingListService,
    public errorService: ErrorService
  ) {}

  ngOnInit() {
    this.shoppingListService.initWebSocket();
    this.shoppingListService.shoppingList$.subscribe((list) => {
      this.shoppingList = list;
    });
  }

  ngOnDestroy(): void {
    this.shoppingListService.closeWebSocket();
  }

  addItem() {
    if (!this.newItemName.trim()) {
      this.errorService.setError('Please enter an item name.');
      return;
    }
    this.shoppingListService.addItem(this.newItemName).subscribe(
      () => {
        this.errorService.clearError();
        this.newItemName = '';
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to add item.');
      }
    );
  }

  deleteItem(id: string) {
    this.shoppingListService.deleteItem(id).subscribe(
      () => {
        this.errorService.clearError();
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to delete item.');
      }
    );
  }

  toggleEdit(item: ShoppingListItemExtended) {
    if (item.isEditing) {
      if (!item.name.trim()) {
        this.errorService.setError('Item name cannot be empty.');
        return;
      }
      this.shoppingListService.updateItem({
        id: item.id,
        name: item.name,
        retrieved: item.retrieved
      }).subscribe(
        () => {
          this.errorService.clearError();
        },
        (error) => {
          this.errorService.setError(error.error?.message || 'Failed to update item.');
        }
      );
    }
    item.isEditing = !item.isEditing;
  }

  toggleRetrieved(item: ShoppingListItemExtended) {
    this.shoppingListService.toggleRetrieved(item).subscribe(
      () => {
        this.errorService.clearError();
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to update item status.');
      }
    );
  }
}

