import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '@core/environments/environment';
import { ShoppingListItem, ShoppingListItemExtended } from '@core/models/shopping-list.model';
import { resolveApiUrl, resolveWebSocketUrl } from '@core/utils/url.util';

@Injectable({
  providedIn: 'root',
})
export class ShoppingListService {
  private apiUrl = resolveApiUrl(environment.apiUrl);
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
    this.socket?.close();
    this.socket = new WebSocket(resolveWebSocketUrl(this.wsUrl, `shoppingListWS?token=${encodeURIComponent(token ?? '')}`));

    this.socket.onmessage = (event) => {
      const data: ShoppingListItemExtended[] = JSON.parse(event.data);
      this.setShoppingList(data);
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

  loadItems(): Observable<ShoppingListItem[]> {
    return this.http.get<ShoppingListItem[]>(`${this.apiUrl}/shoppingList`);
  }

  /**
   * Add a new item to the shopping list
   */
  addItem(name: string): Observable<any> {
    const newItem: ShoppingListItem = {
      id: this.generateId(),
      name,
      amount: '',
      retrieved: false
    };
    return this.http.post(`${this.apiUrl}/shoppingList`, newItem);
  }

  /**
   * Add every item from a recipe to the shopping list
   */
  addRecipe(recipeId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/recipe/${encodeURIComponent(recipeId)}/shoppingList`, {});
  }

  /**
   * Delete an item from the shopping list
   */
  deleteItem(id: string): Observable<any> {
    this.setShoppingList(this.shoppingListSubject.value.filter(item => item.id !== id));
    return this.http.delete(`${this.apiUrl}/shoppingList`, { params: { id } });
  }

  /**
   * Update an item in the shopping list
   */
  updateItem(item: ShoppingListItem): Observable<any> {
    this.mergeItem(item);
    return this.http.put(`${this.apiUrl}/shoppingList`, item);
  }

  setShoppingList(items: ShoppingListItem[]): void {
    const sorted = items
      .map(item => ({ ...item, isEditing: false }))
      .sort((a, b) => Number(a.retrieved) - Number(b.retrieved));

    this.shoppingListSubject.next(sorted);
  }

  private mergeItem(item: ShoppingListItem): void {
    this.setShoppingList(
      this.shoppingListSubject.value.map(existing =>
        existing.id === item.id ? { ...existing, ...item } : existing
      )
    );
  }

  private generateId(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }
}
