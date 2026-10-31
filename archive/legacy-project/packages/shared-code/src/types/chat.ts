export interface Entity {
  type: string;
  value: string;
}

export interface Intent {
  name: string;
  confidence: number;
}

export interface Message {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  createdAt: string;
}

export interface Conversation {
  id: string;
  messages: Message[];
}

export interface DialogueContext {
  sessionId: string;
  lastIntent?: Intent;
}

export interface ChatResponse {
  message: Message;
  suggestions?: string[];
}

export interface ChatbotRecommendation {
  productId: number;
  name: string;
  slug: string;
  price: number;
  reason: string;
}

export interface ChatbotAdvisorResponse {
  answer: string;
  matchedIntent: string;
  recommendations: ChatbotRecommendation[];
}
