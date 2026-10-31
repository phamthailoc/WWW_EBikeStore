import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { ChatbotAdvisorResponse, ChatResponse, Message } from "../types";

export const chatbotAPI = {
  sendMessage: async (content: string, chatId?: string) => {
    const response = await apiClient.post<ChatbotAdvisorResponse>(API_ENDPOINTS.chat.message, { message: content, chatId });
    return {
      message: {
        id: crypto.randomUUID(),
        role: "assistant",
        content: response.data.answer,
        createdAt: new Date().toISOString()
      },
      suggestions: response.data.recommendations.map((recommendation) => recommendation.name)
    } satisfies ChatResponse;
  },
  askAdvisor: async (message: string, chatId?: string) => {
    const response = await apiClient.post<ChatbotAdvisorResponse>(API_ENDPOINTS.chat.message, { message, chatId });
    return response.data;
  },
  getHistory: async () => {
    const response = await apiClient.get<Message[]>(API_ENDPOINTS.chat.history);
    return response.data;
  }
};
