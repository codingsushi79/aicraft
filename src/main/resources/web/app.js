const linkPanel = document.getElementById("link-panel");
const chatPanel = document.getElementById("chat-panel");
const linkUsername = document.getElementById("link-username");
const linkCode = document.getElementById("link-code");
const linkStartBtn = document.getElementById("link-start-btn");
const linkVerifyBtn = document.getElementById("link-verify-btn");
const linkStepStart = document.getElementById("link-step-start");
const linkStepCode = document.getElementById("link-step-code");
const linkError = document.getElementById("link-error");
const currentUser = document.getElementById("current-user");
const logoutBtn = document.getElementById("logout-btn");
const chatList = document.getElementById("chat-list");
const newChatBtn = document.getElementById("new-chat-btn");
const messagesEl = document.getElementById("messages");
const messageForm = document.getElementById("message-form");
const messageInput = document.getElementById("message-input");
const sendBtn = document.getElementById("send-btn");
const activeChatTitle = document.getElementById("active-chat-title");
const activeChatMeta = document.getElementById("active-chat-meta");
const chatError = document.getElementById("chat-error");

let activeChatId = null;
let pendingUsername = "";

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || "Request failed");
  }
  return data;
}

function showError(element, message) {
  element.textContent = message;
  element.classList.remove("hidden");
}

function clearError(element) {
  element.textContent = "";
  element.classList.add("hidden");
}

function showChatPanel(username) {
  linkPanel.classList.add("hidden");
  chatPanel.classList.remove("hidden");
  currentUser.textContent = username;
}

function showLinkPanel() {
  chatPanel.classList.add("hidden");
  linkPanel.classList.remove("hidden");
  activeChatId = null;
}

function renderMessages(messages) {
  messagesEl.innerHTML = "";
  for (const message of messages) {
    const div = document.createElement("div");
    div.className = `message ${message.role}`;
    div.textContent = message.content;
    messagesEl.appendChild(div);
  }
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

function renderChatList(chats) {
  chatList.innerHTML = "";
  for (const chat of chats) {
    const item = document.createElement("li");
    item.dataset.chatId = chat.id;
    if (chat.id === activeChatId) {
      item.classList.add("active");
    }
    item.innerHTML = `
      <div class="chat-number">Chat #${chat.chatNumber}</div>
      <div class="hint">${chat.source} · ${chat.ended ? "ended" : "active"}</div>
    `;
    item.addEventListener("click", () => selectChat(chat));
    chatList.appendChild(item);
  }
}

async function loadChats() {
  const chats = await api("/api/chats");
  renderChatList(chats);
  return chats;
}

async function selectChat(chat) {
  activeChatId = chat.id;
  activeChatTitle.textContent = `Chat #${chat.chatNumber}`;
  activeChatMeta.textContent = `${chat.source} · updated ${new Date(chat.updatedAt).toLocaleString()}`;
  messageInput.disabled = false;
  sendBtn.disabled = false;
  clearError(chatError);
  const messages = await api(`/api/chats/${chat.id}/messages`);
  renderMessages(messages);
  await loadChats();
}

linkStartBtn.addEventListener("click", async () => {
  clearError(linkError);
  pendingUsername = linkUsername.value.trim();
  if (!pendingUsername) {
    showError(linkError, "Enter your Minecraft username.");
    return;
  }
  try {
    await api("/api/link/start", {
      method: "POST",
      body: JSON.stringify({ username: pendingUsername }),
    });
    linkStepStart.classList.add("hidden");
    linkStepCode.classList.remove("hidden");
  } catch (error) {
    showError(linkError, error.message);
  }
});

linkVerifyBtn.addEventListener("click", async () => {
  clearError(linkError);
  try {
    const result = await api("/api/link/verify", {
      method: "POST",
      body: JSON.stringify({
        username: pendingUsername,
        code: linkCode.value.trim(),
      }),
    });
    showChatPanel(result.username);
    await loadChats();
  } catch (error) {
    showError(linkError, error.message);
  }
});

logoutBtn.addEventListener("click", async () => {
  await api("/api/logout", { method: "POST", body: "{}" });
  showLinkPanel();
});

newChatBtn.addEventListener("click", async () => {
  clearError(chatError);
  try {
    const chat = await api("/api/chats", { method: "POST", body: "{}" });
    const chats = await loadChats();
    const created = chats.find((item) => item.id === chat.id) || chat;
    await selectChat(created);
  } catch (error) {
    showError(chatError, error.message);
  }
});

messageForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!activeChatId) {
    return;
  }
  const content = messageInput.value.trim();
  if (!content) {
    return;
  }
  clearError(chatError);
  sendBtn.disabled = true;
  try {
    const result = await api(`/api/chats/${activeChatId}/messages`, {
      method: "POST",
      body: JSON.stringify({ content }),
    });
    messageInput.value = "";
    const messages = await api(`/api/chats/${activeChatId}/messages`);
    renderMessages(messages);
    if (!messages.some((message) => message.role === "assistant" && message.content === result.reply)) {
      renderMessages([...messages, { role: "assistant", content: result.reply }]);
    }
    await loadChats();
  } catch (error) {
    showError(chatError, error.message);
  } finally {
    sendBtn.disabled = false;
  }
});

async function bootstrap() {
  try {
    const me = await api("/api/me");
    showChatPanel(me.username);
    await loadChats();
  } catch {
    showLinkPanel();
  }
}

bootstrap();
