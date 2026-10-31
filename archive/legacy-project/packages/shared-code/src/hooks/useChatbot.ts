import { sendMessageThunk } from "../redux/slices/chatbotSlice";
import { useAppDispatch, useAppSelector } from "../redux";

export const useChatbot = () => {
  const dispatch = useAppDispatch();
  const chatbot = useAppSelector((state) => state.chatbot);

  return {
    ...chatbot,
    sendMessage: (content: string) => dispatch(sendMessageThunk(content))
  };
};
