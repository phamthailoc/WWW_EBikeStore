import type { FormEvent } from "react";
import { useEffect, useRef, useState } from "react";
import { Loader, MessageSquare, Send, Trash2 } from "lucide-react";
import { chatbotAPI } from "@ebike/shared-code/api";

type Sender = "bot" | "user";
type ConversationStage = "initial" | "understanding" | "recommending";

type ChatMessage = {
  id: string;
  sender: Sender;
  text: string;
  timestamp: Date;
};

const initialMessage: ChatMessage = {
  id: crypto.randomUUID(),
  sender: "bot",
  text:
    "Xin chào! Mình là cố vấn thông minh của Kinetic. Mình có thể tư vấn mẫu xe phù hợp theo nhu cầu, ngân sách, quãng đường đi hằng ngày, pin, bảo hành hoặc showroom nhận xe.\n\nBạn có thể bắt đầu bằng cách mô tả nhu cầu như: đi học, đi làm, muốn xe giá tốt, ưu tiên quãng đường xa hoặc cần tư vấn thanh toán.",
  timestamp: new Date()
};

type StoredChatMessage = Omit<ChatMessage, "timestamp"> & {
  timestamp: string;
};

type StoredChatSession = {
  chatId: string;
  messages: StoredChatMessage[];
  conversationStage?: ConversationStage;
};

const CHATBOT_STORAGE_KEY = "kinetic-chatbot-session-v1";
const MAX_STORED_MESSAGES = 80;

const serializeMessage = (message: ChatMessage): StoredChatMessage => ({
  ...message,
  timestamp: message.timestamp.toISOString()
});

const createInitialMessage = (): ChatMessage => ({
  ...initialMessage,
  id: crypto.randomUUID(),
  timestamp: new Date()
});

const createEmptySession = (): StoredChatSession => ({
  chatId: crypto.randomUUID(),
  messages: [serializeMessage(createInitialMessage())],
  conversationStage: "initial"
});

const parseStoredSession = (): StoredChatSession => {
  if (typeof window === "undefined") {
    return createEmptySession();
  }

  try {
    const rawSession = window.localStorage.getItem(CHATBOT_STORAGE_KEY);
    if (!rawSession) {
      return createEmptySession();
    }

    const session = JSON.parse(rawSession) as StoredChatSession;
    if (!session.chatId || !Array.isArray(session.messages) || !session.messages.length) {
      return createEmptySession();
    }

    return {
      chatId: session.chatId,
      messages: session.messages.slice(-MAX_STORED_MESSAGES),
      conversationStage: session.conversationStage || (session.messages.length > 1 ? "understanding" : "initial")
    };
  } catch {
    return createEmptySession();
  }
};

const parseMessages = (messages: StoredChatMessage[]): ChatMessage[] =>
  messages.map((message) => ({
    ...message,
    timestamp: new Date(message.timestamp)
  }));

const quickQuestions = [
  "Tôi cần xe điện để đi làm hằng ngày",
  "Gợi ý mẫu xe giá tốt cho sinh viên",
  "Xe nào đi được quãng đường xa?",
  "Tư vấn mẫu xe mạnh và tốc độ tốt"
];

const createMessage = (sender: Sender, text: string): ChatMessage => ({
  id: crypto.randomUUID(),
  sender,
  text,
  timestamp: new Date()
});

const formatRecommendationText = (
  recommendations: Array<{ name: string; reason: string }>
) => {
  if (!recommendations.length) {
    return "";
  }

  return `\n\nGợi ý sản phẩm:\n${recommendations
    .map((item) => `- ${item.name}: ${item.reason}`)
    .join("\n")}`;
};

const ChatbotPage = () => {
  const initialSessionRef = useRef<StoredChatSession | null>(null);
  if (!initialSessionRef.current) {
    initialSessionRef.current = parseStoredSession();
  }

  const [messages, setMessages] = useState<ChatMessage[]>(() => parseMessages(initialSessionRef.current!.messages));
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [conversationStage, setConversationStage] = useState<ConversationStage>(
    initialSessionRef.current!.conversationStage || "initial"
  );
  const messagesContainerRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const chatIdRef = useRef<string>(initialSessionRef.current!.chatId);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const session: StoredChatSession = {
      chatId: chatIdRef.current,
      messages: messages.slice(-MAX_STORED_MESSAGES).map(serializeMessage),
      conversationStage
    };
    window.localStorage.setItem(CHATBOT_STORAGE_KEY, JSON.stringify(session));
  }, [messages, conversationStage]);

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      const container = messagesContainerRef.current;
      if (!container) {
        return;
      }

      container.scrollTo({
        top: container.scrollHeight,
        behavior: messages.length <= 1 && !isLoading ? "auto" : "smooth"
      });
    });

    return () => cancelAnimationFrame(frame);
  }, [messages, isLoading]);

  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!input.trim() || isLoading) {
      return;
    }

    const submittedInput = input.trim();
    setMessages((prev) => [...prev, createMessage("user", submittedInput)]);
    setInput("");
    setIsLoading(true);

    try {
      const response = await chatbotAPI.askAdvisor(submittedInput, chatIdRef.current);
      const recommendationText = formatRecommendationText(response.recommendations);

      setConversationStage(response.recommendations.length ? "recommending" : "understanding");
      setMessages((prev) => [
        ...prev,
        createMessage("bot", `${response.answer}${recommendationText}`)
      ]);
    } catch {
      setConversationStage("understanding");
      setMessages((prev) => [
        ...prev,
        createMessage(
          "bot",
          "Mình chưa kết nối được tới dịch vụ tư vấn lúc này. Bạn hãy thử lại sau ít phút hoặc đặt câu hỏi ngắn hơn về mẫu xe, giá, quãng đường, pin, bảo hành hay thanh toán."
        )
      ]);
    } finally {
      setIsLoading(false);
      inputRef.current?.focus();
    }
  };

  const handleClearConversation = () => {
    const freshMessage = createInitialMessage();
    chatIdRef.current = crypto.randomUUID();
    setMessages([freshMessage]);
    setConversationStage("initial");
    setInput("");
    if (typeof window !== "undefined") {
      window.localStorage.removeItem(CHATBOT_STORAGE_KEY);
    }
    inputRef.current?.focus();
  };

  return (
    <div className="pt-[88px]">
      <div className="flex h-[calc(100dvh-88px)] flex-col overflow-hidden bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
        <div className="border-b border-slate-700 bg-slate-800/60 backdrop-blur-sm">
          <div className="mx-auto max-w-4xl px-4 py-5 sm:px-6 lg:px-8">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-blue-600">
                  <MessageSquare className="h-6 w-6 text-white" />
                </div>
                <div>
                  <h1 className="text-2xl font-bold text-white">Cố vấn thông minh</h1>
                  <p className="text-sm text-slate-400">Tư vấn xe điện Kinetic bằng AI</p>
                </div>
              </div>
              <button
                type="button"
                onClick={handleClearConversation}
                title="Xóa hội thoại"
                aria-label="Xóa hội thoại"
                className="flex h-10 w-10 items-center justify-center rounded-full border border-slate-600 text-slate-300 transition hover:border-red-400 hover:bg-red-500/10 hover:text-red-200"
              >
                <Trash2 className="h-5 w-5" />
              </button>
            </div>
          </div>
        </div>

        <div ref={messagesContainerRef} className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
          <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
            <div className="space-y-6">
              {messages.map((message) => (
                <div key={message.id} className={`flex ${message.sender === "user" ? "justify-end" : "justify-start"}`}>
                  <div
                    className={`max-w-2xl px-6 py-4 shadow-lg ${
                      message.sender === "user"
                        ? "rounded-2xl rounded-br-none bg-blue-600 text-white"
                        : "rounded-2xl rounded-bl-none bg-slate-700 text-slate-100"
                    }`}
                  >
                    <p className="whitespace-pre-wrap text-sm leading-relaxed">{message.text}</p>
                    <span className="mt-2 block text-xs opacity-70">
                      {message.timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </span>
                  </div>
                </div>
              ))}

              {isLoading && (
                <div className="flex justify-start">
                  <div className="rounded-2xl rounded-bl-none bg-slate-700 px-6 py-4 text-slate-100 shadow-lg">
                    <div className="flex items-center gap-2">
                      <Loader className="h-4 w-4 animate-spin" />
                      <span className="text-sm">Đang soạn câu trả lời...</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="border-t border-slate-700 bg-slate-800/60 backdrop-blur-sm">
          <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 lg:px-8">
            {conversationStage === "initial" && (
              <div className="mb-6">
                <p className="mb-3 text-xs font-semibold uppercase text-slate-400">Câu hỏi nhanh</p>
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  {quickQuestions.map((question) => (
                    <button
                      key={question}
                      type="button"
                      onClick={() => {
                        setInput(question);
                        inputRef.current?.focus();
                      }}
                      className="rounded-lg bg-slate-700/50 px-4 py-2 text-left text-sm text-slate-300 transition duration-200 hover:bg-slate-600 hover:text-white"
                    >
                      {question}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <form onSubmit={handleSendMessage} className="flex gap-4">
              <input
                ref={inputRef}
                type="text"
                placeholder="Nhập câu hỏi hoặc nhu cầu của bạn..."
                className="flex-1 rounded-full border border-slate-600 bg-slate-700 px-6 py-3 text-white placeholder-slate-400 transition focus:border-transparent focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                disabled={isLoading}
              />
              <button
                type="submit"
                disabled={isLoading || !input.trim()}
                className="flex items-center gap-2 rounded-full bg-blue-600 px-6 py-3 font-semibold text-white transition duration-200 hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-600"
              >
                <Send className="h-5 w-5" />
                <span className="hidden sm:inline">Gửi</span>
              </button>
            </form>

            <p className="mt-4 text-center text-xs text-slate-500">
              Hỏi mình về thông số kỹ thuật, mẫu xe phù hợp, giá, thanh toán, showroom hoặc bảo hành.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChatbotPage;
