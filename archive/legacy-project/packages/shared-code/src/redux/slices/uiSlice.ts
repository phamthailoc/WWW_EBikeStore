import { createSlice } from "@reduxjs/toolkit";

interface UiState {
  isDarkMode: boolean;
  sidebarOpen: boolean;
}

const initialState: UiState = {
  isDarkMode: false,
  sidebarOpen: false
};

const uiSlice = createSlice({
  name: "ui",
  initialState,
  reducers: {
    toggleDarkMode: (state) => {
      state.isDarkMode = !state.isDarkMode;
    },
    toggleSidebar: (state) => {
      state.sidebarOpen = !state.sidebarOpen;
    }
  }
});

export const { toggleDarkMode, toggleSidebar } = uiSlice.actions;
export default uiSlice.reducer;
