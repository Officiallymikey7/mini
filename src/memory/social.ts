/**
 * memory/social.ts
 * Builds the [Social Horizon Block] – a filtered log of nearby chat messages
 * and events from other agents/players.
 */

export interface ChatMessage {
  sender: string;
  text: string;
  timestamp: number;
}

export interface SocialBlock {
  /** Plain-text summary appended to every planner prompt. */
  summary: string;
  messages: ChatMessage[];
}

const MAX_MESSAGES = 20;
/** Only include messages from the last N milliseconds in the summary. */
const HORIZON_MS = 120_000; // 2 minutes

export class SocialMemory {
  private messages: ChatMessage[] = [];

  /** Ingest new chat messages observed this tick. */
  ingest(newMessages: string[], senderName = 'unknown'): void {
    const now = Date.now();
    for (const text of newMessages) {
      // Parse "SenderName: message" format if present
      const colonIdx = text.indexOf(':');
      let sender = senderName;
      let body = text;
      if (colonIdx > 0 && colonIdx < 20) {
        sender = text.slice(0, colonIdx).trim();
        body = text.slice(colonIdx + 1).trim();
      }
      this.messages.push({ sender, text: body, timestamp: now });
    }

    // Keep only the most recent messages
    if (this.messages.length > MAX_MESSAGES) {
      this.messages = this.messages.slice(this.messages.length - MAX_MESSAGES);
    }
  }

  /** Build the [Social Horizon Block] summary string. */
  build(): SocialBlock {
    const now = Date.now();
    const recent = this.messages.filter((m) => now - m.timestamp <= HORIZON_MS);

    if (recent.length === 0) {
      return {
        summary: 'No recent nearby activity observed.',
        messages: [],
      };
    }

    const lines = recent.map((m) => `  ${m.sender}: "${m.text}"`);
    const summary = ['Nearby agent/player messages:'].concat(lines).join('\n');
    return { summary, messages: recent };
  }

  /** Returns a copy of all stored messages. */
  getAll(): ChatMessage[] {
    return [...this.messages];
  }

  /** Clears all stored messages. */
  clear(): void {
    this.messages = [];
  }
}
