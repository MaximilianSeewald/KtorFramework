import { Component, OnInit, OnDestroy } from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgForOf, NgIf} from '@angular/common';
import {ShoppingListItem, ShoppingListItemExtended} from '../shoppingList/shoppingList.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import { v4 as uuid } from 'uuid';

@Component({
  selector: 'app-dashboard',
  imports: [
    FormsModule,
    NgForOf,
    NgIf
  ],
  templateUrl: './dashboard.component.html',
  standalone: true,
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {

  apiUrl = environment.apiUrl;
  wsUrl = environment.wsUrl
  shoppingList: ShoppingListItemExtended[] = [];
  newItemName = '';
  socket: WebSocket | null = null

  constructor(private http: HttpClient) {}

  ngOnDestroy(): void {
    this.socket?.close()
  }


  ngOnInit() {
    this.getShoppingItems()
    this.subscribeSSE()
  }


  subscribeSSE() {
    const token = localStorage.getItem('token');
    this.socket = new WebSocket(`${this.wsUrl}/shoppingListWS?token=${token}`)
    this.socket.onmessage = (event) => this.shoppingList = JSON.parse(event.data)
    this.socket.onerror = (err) => console.log(err)
    this.socket.onclose = (close) => console.log(close.reason)
  }

  addItem() {
    const newItem: ShoppingListItem = { id: uuid(), name: this.newItemName, retrieved: false };
    this.http.post(`${this.apiUrl}/shoppingList`, newItem).subscribe()
  }

  getShoppingItems() {
    this.http.get<ShoppingListItem[]>(`${this.apiUrl}/shoppingList`).subscribe(
      (response) => {
        this.shoppingList = response
          .map((value) => {
            return { id: value.id, name: value.name, retrieved: value.retrieved, isEditing: false }
          })
          .sort((a, b) => Number(a.retrieved) - Number(b.retrieved));
      },
      () => {
        console.log("Error retrieving Shopping List Items")
      }
    );
  }

  deleteItem(id: string) {
    this.http.delete(`${this.apiUrl}/shoppingList`, { params: { id: id } }).subscribe()
  }

  toggleEdit(item: ShoppingListItemExtended) {
    if (item.isEditing) {
      const shoppingListItem: ShoppingListItem = { id: item.id, name: item.name, retrieved: item.retrieved}
      this.http.put(`${this.apiUrl}/shoppingList`,shoppingListItem).subscribe()
    }
    item.isEditing = !item.isEditing
  }

  toggleRetrieved(item: ShoppingListItem) {
    const shoppingListItem: ShoppingListItem = {id: item.id, name: item.name, retrieved: item.retrieved};
    this.http.put(`${this.apiUrl}/shoppingList`, shoppingListItem).subscribe()
  }
}
