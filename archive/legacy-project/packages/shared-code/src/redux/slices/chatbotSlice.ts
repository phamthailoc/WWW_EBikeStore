import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { chatbotAPI } from "../../api";
import type { Message } from "../../types";

interface ChatbotState {
  messages: Message[];
  isStreaming: boolean;
  suggestions: string[];
}

const initialState: ChatbotState = {
  messages: [],
  isStreaming: false,
  suggestions: []
};

export const sendMessageThunk = createAsyncThunk("chatbot/sendMessage", async (content: string) =>
  chatbotAPI.sendMessage(content)
);

const chatbotSlice = createSlice({
  name: "chatbot",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(sendMessageThunk.pending, (state) => {
        state.isStreaming = true;
      })
      .addCase(sendMessageThunk.fulfilled, (state, action) => {
        state.isStreaming = false;
        state.messages.push(action.payload.message);
        state.suggestions = action.payload.suggestions ?? [];
      })
      .addCase(sendMessageThunk.rejected, (state) => {
        state.isStreaming = false;
      });
  }
});

export default chatbotSlice.reducer;
