import { Component } from '@angular/core';
import { IngestPanelComponent } from './ingest-panel.component';
import { ChatPanelComponent } from './chat-panel.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IngestPanelComponent, ChatPanelComponent],
  template: `
    <header class="app-header">
      <h1>RAG Tutorial — Dashboard</h1>
      <p>Ingest documents, then ask questions grounded in them.</p>
    </header>
    <main class="layout">
      <app-ingest-panel />
      <app-chat-panel />
    </main>
  `,
})
export class AppComponent {}
