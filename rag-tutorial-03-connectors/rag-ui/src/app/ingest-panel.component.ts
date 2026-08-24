import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from './api.service';
import { IngestedDocument } from './models';

@Component({
  selector: 'app-ingest-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel">
      <h2>1 · Ingest documents</h2>

      <div class="field-row">
        <input type="file" #fileInput (change)="onFileSelected($event)" />
        <button (click)="uploadFile()" [disabled]="!selectedFile || busy">Upload</button>
      </div>
      <p class="hint">PDF, DOCX, TXT, … — text is extracted with Apache Tika.</p>

      <div class="field-row">
        <input type="url" placeholder="https://example.com/article" [(ngModel)]="url" />
        <button (click)="ingestUrl()" [disabled]="!url || busy">Ingest URL</button>
      </div>
      <p class="hint">Web pages are fetched and cleaned with Jsoup.</p>

      <p class="error" *ngIf="error">{{ error }}</p>

      <h2>Ingested so far</h2>
      <p class="empty" *ngIf="documents.length === 0">Nothing ingested yet.</p>
      <table class="docs" *ngIf="documents.length > 0">
        <thead>
          <tr><th>Name</th><th>Type</th><th>Chunks</th><th>When</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let doc of documents">
            <td [title]="doc.source">{{ doc.name }}</td>
            <td>{{ doc.sourceType }}</td>
            <td>{{ doc.chunkCount }}</td>
            <td>{{ doc.ingestedAt | date: 'HH:mm:ss' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  `,
})
export class IngestPanelComponent implements OnInit {
  private api = inject(ApiService);

  documents: IngestedDocument[] = [];
  selectedFile: File | null = null;
  url = '';
  busy = false;
  error = '';

  ngOnInit(): void {
    this.refresh();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  uploadFile(): void {
    if (!this.selectedFile) return;
    this.run(this.api.ingestFile(this.selectedFile), () => (this.selectedFile = null));
  }

  ingestUrl(): void {
    if (!this.url) return;
    this.run(this.api.ingestUrl(this.url), () => (this.url = ''));
  }

  private run(obs: { subscribe: Function }, onSuccess: () => void): void {
    this.busy = true;
    this.error = '';
    obs.subscribe({
      next: () => {
        onSuccess();
        this.busy = false;
        this.refresh();
      },
      error: (err: any) => {
        this.busy = false;
        this.error = err?.error?.error ?? 'Ingestion failed — is the backend running?';
      },
    });
  }

  private refresh(): void {
    this.api.documents().subscribe((docs) => (this.documents = docs));
  }
}
