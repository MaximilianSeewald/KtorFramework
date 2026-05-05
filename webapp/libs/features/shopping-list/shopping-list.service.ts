import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '@core/environments/environment';
import { ShoppingListItem, ShoppingListItemExtended } from '@core/models/shopping-list.model';

@Injectable({
  providedIn: 'root',
})
export class ShoppingListService {
  private apiUrl = environment.apiUrl;
  private wsUrl = environment.wsUrl;
  private shoppingListSubject = new BehaviorSubject<ShoppingListItemExtended[]>([]);

  public shoppingList$ = this.shoppingListSubject.asObservable();
  private socket: WebSocket | null = null;

  constructor(private http: HttpClient) {}

  /**
   * Initialize WebSocket connection for real-time shopping list updates
   */
  initWebSocket(): void {
    const token = localStorage.getItem('token');
    this.socket = new WebSocket(`${this.wsUrl}/shoppingListWS?token=${token}`);

    this.socket.onmessage = (event) => {
      const data: ShoppingListItemExtended[] = JSON.parse(event.data);
      const sorted = data.sort(
        (a, b) => Number(a.retrieved) - Number(b.retrieved)
      );
      this.shoppingListSubject.next(sorted);
    };

    this.socket.onerror = (err) => console.error('WebSocket error:', err);
    this.socket.onclose = (close) => console.log('WebSocket closed:', close.reason);
  }

  /**
   * Close WebSocket connection
   */
  closeWebSocket(): void {
    this.socket?.close();
    this.socket = null;
  }

  /**
   * Get current shopping list value
   */
  getShoppingList(): ShoppingListItemExtended[] {
    return this.shoppingListSubject.value;
  }

  /**
   * Add a new item to the shopping list
   */
  addItem(name: string): Observable<any> {
    const newItem: ShoppingListItem = {
      id: this.generateId(),
      name,
      retrieved: false
    };
    return this.http.post(`${this.apiUrl}/shoppingList`, newItem);
  }

  /**
   * Delete an item from the shopping list
   */
  deleteItem(id: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/shoppingList`, { params: { id } });
  }

  /**
   * Update an item in the shopping list
   */
  updateItem(item: ShoppingListItem): Observable<any> {
    return this.http.put(`${this.apiUrl}/shoppingList`, item);
  }

  /**
   * Toggle the retrieved status of an item
   */
  toggleRetrieved(item: ShoppingListItem): Observable<any> {
    return this.updateItem({
      id: item.id,
      name: item.name,
      retrieved: !item.retrieved
    });
  }

  private generateId(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }
}

