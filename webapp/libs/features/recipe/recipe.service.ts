import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '@core/environments/environment';
import { Recipe, RecipeExtended, RecipeItem } from '@core/models/recipe.model';

@Injectable({
  providedIn: 'root',
})
export class RecipeService {
  private apiUrl = environment.apiUrl;
  private wsUrl = environment.wsUrl;
  private recipesSubject = new BehaviorSubject<RecipeExtended[]>([]);

  public recipes$ = this.recipesSubject.asObservable();
  private socket: WebSocket | null = null;

  constructor(private http: HttpClient) {}

  /**
   * Initialize WebSocket connection for real-time recipe updates
   */
  initWebSocket(): void {
    const token = localStorage.getItem('token');
    this.socket = new WebSocket(`${this.wsUrl}/recipeWS?token=${token}`);

    this.socket.onmessage = (event) => {
      const data: RecipeExtended[] = JSON.parse(event.data).map((recipe: Recipe) => ({
        ...recipe,
        isEditing: false,
        editingItemIndex: null
      }));
      this.recipesSubject.next(data);
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
   * Get current recipes value
   */
  getRecipes(): RecipeExtended[] {
    return this.recipesSubject.value;
  }

  /**
   * Add a new recipe
   */
  addRecipe(name: string): Observable<any> {
    const newRecipe: Recipe = {
      id: this.generateId(),
      name,
      items: []
    };
    return this.http.post(`${this.apiUrl}/recipe`, newRecipe);
  }

  /**
   * Update a recipe
   */
  updateRecipe(recipe: Recipe): Observable<any> {
    return this.http.put(`${this.apiUrl}/recipe`, recipe);
  }

  /**
   * Delete a recipe
   */
  deleteRecipe(id: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/recipe`, { params: { id } });
  }

  /**
   * Add an item to a recipe
   */
  addItem(recipeId: string, item: RecipeItem): Observable<any> {
    const recipe = this.recipesSubject.value.find(r => r.id === recipeId);
    if (!recipe) return new Observable(obs => obs.error('Recipe not found'));

    recipe.items.push(item);
    return this.updateRecipe({
      id: recipe.id,
      name: recipe.name,
      items: recipe.items
    });
  }

  /**
   * Remove an item from a recipe
   */
  removeItem(recipeId: string, itemIndex: number): Observable<any> {
    const recipe = this.recipesSubject.value.find(r => r.id === recipeId);
    if (!recipe) return new Observable(obs => obs.error('Recipe not found'));

    recipe.items.splice(itemIndex, 1);
    return this.updateRecipe({
      id: recipe.id,
      name: recipe.name,
      items: recipe.items
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

