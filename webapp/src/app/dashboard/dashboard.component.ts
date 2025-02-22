import { Component, OnInit } from '@angular/core';
import {MatCard, MatCardHeader} from '@angular/material/card';
import {MatIcon} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {NgForOf, NgIf} from '@angular/common';
import {ShoppingListItem} from '../shoppingList/shoppingList.model';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import { v4 as uuid } from 'uuid';

@Component({
  selector: 'app-dashboard',
  imports: [
    MatCard,
    MatCardHeader,
    MatIcon,
    FormsModule,
    NgIf,
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
      (response) => {
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

      }
    );
  }



  deleteItem(id: string) {

  }
}
