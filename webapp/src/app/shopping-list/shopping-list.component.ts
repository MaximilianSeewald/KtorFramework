import { Component, OnInit, OnDestroy } from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgForOf, NgIf} from '@angular/common';
import {ShoppingListItem, ShoppingListItemExtended} from '../models/shoppingList.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import { v4 as uuid } from 'uuid';
import {MatIconModule} from '@angular/material/icon';
import {ErrorService} from '../error.service';

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

  apiUrl = environment.apiUrl;
  wsUrl = environment.wsUrl
  shoppingList: ShoppingListItemExtended[] = [];
  newItemName = '';
  socket: WebSocket | null = null

  constructor(private http: HttpClient, public errorService: ErrorService) {}

  ngOnDestroy(): void {
    this.socket?.close()
  }


  ngOnInit() {
    this.subscribeWebSocket()
  }


  subscribeWebSocket() {
    const token = localStorage.getItem('token');
    this.socket = new WebSocket(`${this.wsUrl}/shoppingListWS?token=${token}`)
    this.socket.onmessage = (event) => {
      const data: ShoppingListItemExtended[] = JSON.parse(event.data)
      this.shoppingList = data.sort(
        (a, b) => Number(a.retrieved) - Number(b.retrieved)
      );
    }
    this.socket.onerror = (err) => console.log(err)
    this.socket.onclose = (close) => console.log(close.reason)
  }

  addItem() {
    if (!this.newItemName.trim()) {
      this.errorService.setError('Please enter an item name.');
      return;
    }
    const newItem: ShoppingListItem = { id: uuid(), name: this.newItemName, retrieved: false };
    this.http.post(`${this.apiUrl}/shoppingList`, newItem).subscribe(
      () => {
        this.errorService.clearError();
        this.newItemName = "";
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to add item.');
      }
    );
  }

  deleteItem(id: string) {
    this.http.delete(`${this.apiUrl}/shoppingList`, { params: { id: id } }).subscribe(
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
      const shoppingListItem: ShoppingListItem = { id: item.id, name: item.name, retrieved: item.retrieved}
      this.http.put(`${this.apiUrl}/shoppingList`,shoppingListItem).subscribe(
        () => {
          this.errorService.clearError();
        },
        (error) => {
          this.errorService.setError(error.error?.message || 'Failed to update item.');
        }
      );
    }
    item.isEditing = !item.isEditing
  }

  toggleRetrieved(item: ShoppingListItem) {
    const shoppingListItem: ShoppingListItem = {id: item.id, name: item.name, retrieved: item.retrieved};
    this.http.put(`${this.apiUrl}/shoppingList`, shoppingListItem).subscribe(
      () => {
        this.errorService.clearError();
      },
      (error) => {
        this.errorService.setError(error.error?.message || 'Failed to update item status.');
      }
    );
  }
}
