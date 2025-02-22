import { Component, OnInit } from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgForOf} from '@angular/common';
import {ShoppingListItem} from '../shoppingList/shoppingList.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import { v4 as uuid } from 'uuid';

@Component({
  selector: 'app-dashboard',
  imports: [
    FormsModule,
    NgForOf
  ],
  templateUrl: './dashboard.component.html',
  standalone: true,
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {

  apiUrl = environment.apiUrl;
  shoppingList: ShoppingListItem[] = [];
  newItemName = '';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.getShoppingItems()
  }

  addItem() {
    const newItem: ShoppingListItem = { id: uuid(), name: this.newItemName };
    this.http.put(`${this.apiUrl}/shoppingList`, newItem).subscribe(
      () => {
        this.getShoppingItems()
      }
    );
  }

  public getShoppingItems() {
    this.http.get<ShoppingListItem[]>(`${this.apiUrl}/shoppingList`).subscribe(
      (response) => {
        this.shoppingList = response
      },
      () => {
        console.log("Error retrieving Shopping List Items")
      }
    );
  }

  deleteItem(id: string) {
    this.http.delete(`${this.apiUrl}/shoppingList`, { params: { id: id } }).subscribe(
      () => {
        this.getShoppingItems()
      }
    );
  }
}
