import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IngestedDocument, RagAnswer } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  chat(question: string): Observable<RagAnswer> {
    return this.http.post<RagAnswer>('/api/chat', { question });
  }

  ingestFile(file: File): Observable<IngestedDocument> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<IngestedDocument>('/api/ingest/file', form);
  }

  ingestUrl(url: string): Observable<IngestedDocument> {
    return this.http.post<IngestedDocument>('/api/ingest/url', { url });
  }

  documents(): Observable<IngestedDocument[]> {
    return this.http.get<IngestedDocument[]>('/api/documents');
  }
}
