import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, BrainCircuit, Send, Sparkles, AlertTriangle } from 'lucide-react';
import { useSendChatMessageMutation } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import { useI18n } from '../i18n';
import type { DoctorSuggestion } from '../services/api';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  suggestions?: DoctorSuggestion[];
  emergency?: boolean;
}

let messageId = 0;
const nextId = () => `msg-${Date.now()}-${messageId++}`;

const QUICK_PROMPTS = [
  'What is my queue status?',
  'Which doctor should I see for a persistent headache?',
  'Which departments are available?',
];

/**
 * CareQ AI assistant — a chat front end over the queue-service intent engine
 * (/api/queue/chat). The assistant answers from live data (queue status,
 * doctor recommendations, department catalog); FIND_DOCTOR replies render the
 * ranked doctors as tappable chips that jump to the doctor browser.
 */
export default function ChatPage() {
  const { t } = useI18n();
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: nextId(),
      role: 'assistant',
      text: "Hi! I'm the CareQ assistant. Ask me about your queue position, which doctor to see for your symptoms, or our departments.",
    },
  ]);
  const [input, setInput] = useState('');
  const [sendChat, { isLoading }] = useSendChatMessageMutation();
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to the newest message.
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  async function handleSend(text: string) {
    const trimmed = text.trim();
    if (!trimmed || isLoading) return;
    setMessages((prev) => [...prev, { id: nextId(), role: 'user', text: trimmed }]);
    setInput('');
    try {
      const reply = await sendChat({ message: trimmed }).unwrap();
      setMessages((prev) => [
        ...prev,
        {
          id: nextId(),
          role: 'assistant',
          text: reply.reply,
          suggestions: reply.suggestions?.length ? reply.suggestions : undefined,
          emergency: reply.emergency,
        },
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { id: nextId(), role: 'assistant', text: getErrorMessage(err) },
      ]);
    }
  }

  return (
    <div className="mx-auto flex h-[calc(100dvh-4rem)] w-full max-w-3xl flex-col px-4 py-4">
      {/* Header */}
      <div className="mb-3 flex items-center gap-3">
        <Link
          to="/patient"
          className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-500 transition-colors hover:text-brand-600 dark:border-slate-700 dark:bg-night-800 dark:text-slate-400"
          aria-label={t('common.back')}
        >
          <ArrowLeft className="h-5 w-5" />
        </Link>
        <div className="flex items-center gap-2.5">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
            <BrainCircuit className="h-5 w-5 text-white" />
          </div>
          <div>
            <h1 className="font-display text-lg font-extrabold tracking-tight text-slate-800 dark:text-slate-100">
              CareQ Assistant
            </h1>
            <p className="text-xs font-medium text-slate-400 dark:text-slate-500">
              Answers from live queue &amp; doctor data
            </p>
          </div>
        </div>
      </div>

      {/* Messages */}
      <div
        ref={scrollRef}
        className="flex-1 space-y-3 overflow-y-auto rounded-3xl border border-slate-200 bg-white/70 p-4 dark:border-slate-700 dark:bg-night-800/60"
      >
        {messages.map((m) => (
          <div key={m.id} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm ${
                m.role === 'user'
                  ? 'rounded-br-md bg-gradient-to-br from-brand-600 to-brand-800 text-white'
                  : 'rounded-bl-md border border-slate-200 bg-white text-slate-700 dark:border-slate-600 dark:bg-night-700 dark:text-slate-200'
              }`}
            >
              {m.emergency && (
                <p className="mb-1.5 flex items-center gap-1.5 text-xs font-bold text-red-500">
                  <AlertTriangle className="h-3.5 w-3.5" /> Please seek immediate attention
                </p>
              )}
              <p className="whitespace-pre-line">{m.text}</p>
              {m.suggestions && m.suggestions.length > 0 && (
                <div className="mt-2.5 flex flex-wrap gap-1.5">
                  {m.suggestions.map((s) => (
                    <Link
                      key={s.doctorCatalogEntryId}
                      to="/patient/doctors"
                      className="inline-flex items-center gap-1.5 rounded-full border border-brand-200 bg-brand-50 px-3 py-1 text-xs font-bold text-brand-700 transition-colors hover:bg-brand-100 dark:border-brand-500/30 dark:bg-brand-500/10 dark:text-brand-300"
                    >
                      <Sparkles className="h-3 w-3" />
                      {s.name}
                      <span className="font-medium text-brand-500 dark:text-brand-400">
                        ≈{s.predictedWaitMinutes} min
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        {isLoading && (
          <div className="flex justify-start">
            <div className="flex items-center gap-1.5 rounded-2xl rounded-bl-md border border-slate-200 bg-white px-4 py-3 dark:border-slate-600 dark:bg-night-700">
              <span className="h-2 w-2 animate-bounce rounded-full bg-brand-400" />
              <span className="h-2 w-2 animate-bounce rounded-full bg-brand-400 [animation-delay:0.15s]" />
              <span className="h-2 w-2 animate-bounce rounded-full bg-brand-400 [animation-delay:0.3s]" />
            </div>
          </div>
        )}
      </div>

      {/* Quick prompts (hidden once the conversation starts) */}
      {messages.length <= 1 && (
        <div className="mt-3 flex flex-wrap justify-center gap-2">
          {QUICK_PROMPTS.map((p) => (
            <button
              key={p}
              onClick={() => handleSend(p)}
              className="rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-bold text-slate-600 transition-colors hover:border-brand-300 hover:text-brand-700 dark:border-slate-600 dark:bg-night-800 dark:text-slate-300 dark:hover:border-brand-500/50 dark:hover:text-brand-300"
            >
              {p}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSend(input);
        }}
        className="mt-3 flex items-center gap-2"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={t('chat.placeholder')}
          maxLength={500}
          className="h-12 flex-1 rounded-2xl border border-slate-200 bg-white px-4 text-sm text-slate-800 shadow-sm outline-none transition-colors placeholder:text-slate-400 focus:border-brand-400 dark:border-slate-600 dark:bg-night-800 dark:text-slate-100 dark:placeholder:text-slate-500"
        />
        <button
          type="submit"
          disabled={!input.trim() || isLoading}
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-800 text-white shadow-lift transition-opacity disabled:opacity-40"
          aria-label="Send"
        >
          <Send className="h-5 w-5" />
        </button>
      </form>
    </div>
  );
}
