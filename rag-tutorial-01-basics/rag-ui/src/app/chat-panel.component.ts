import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from './api.service';
import { ChatMessage } from './models';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel">
      <h2>2 · Ask questions</h2>

      <div class="chat-log">
        <p class="empty" *ngIf="messages.length === 0">
          Ingest a document on the left, then ask something about it.
        </p>
        <div *ngFor="let msg of messages" class="msg" [class.user]="msg.role === 'user'"
             [class.assistant]="msg.role === 'assistant'">
          {{ msg.text }}
          <details class="sources" *ngIf="msg.sources && msg.sources.length > 0">
            <summary>{{ msg.sources.length }} source chunk(s) used</summary>
            <ul>
              <li *ngFor="let src of msg.sources">
                <span class="src">{{ src.source }}</span>
                <span *ngIf="src.score !== null"> · score {{ src.score | number: '1.2-2' }}</span>
                <br />{{ src.snippet }}
              </li>
            </ul>
          </details>
        </div>
        <div class="msg assistant" *ngIf="busy">Thinking…</div>
      </div>

      <p class="error" *ngIf="error">{{ error }}</p>

      <div class="field-row">
        <input type="text" placeholder="Ask about your documents…" [(ngModel)]="question"
               (keyup.enter)="ask()" [disabled]="busy" />
        <button (click)="ask()" [disabled]="!question || busy">Ask</button>
      </div>
    </section>
  `,
})
export class ChatPanelComponent {
  private api = inject(ApiService);

  messages: ChatMessage[] = [];
  question = '';
  busy = false;
  error = '';

  ask(): void {
    const q = this.question.trim();
    if (!q || this.busy) return;
    this.messages.push({ role: 'user', text: q });
    this.question = '';
    this.busy = true;
    this.error = '';
    this.api.chat(q).subscribe({
      next: (res) => {
        this.messages.push({ role: 'assistant', text: res.answer, sources: res.sources });
        this.busy = false;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.error ?? 'Request failed — is the backend running?';
      },
    });
  }
}
