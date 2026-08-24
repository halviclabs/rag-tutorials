export interface IngestedDocument {
  id: string;
  name: string;
  sourceType: string;
  source: string;
  chunkCount: number;
  ingestedAt: string;
}

export interface SourceChunk {
  source: string;
  snippet: string;
  score: number | null;
}

export interface RagAnswer {
  answer: string;
  sources: SourceChunk[];
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  sources?: SourceChunk[];
}
