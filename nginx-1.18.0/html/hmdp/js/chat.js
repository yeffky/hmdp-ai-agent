(function() {
  // Generate or retrieve session ID (persists across page navigation)
  var sessionId = localStorage.getItem('chat_session_id');
  if (!sessionId) {
    sessionId = 'sess_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    localStorage.setItem('chat_session_id', sessionId);
  }

  // Build chat HTML
  var chatHTML =
    '<div id="chat-app">' +
    '  <div class="chat-bubble-btn" @click="toggleWindow" v-show="!showWindow">' +
    '    <i class="el-icon-chat-dot-round"></i>' +
    '  </div>' +
    '  <div class="chat-window" v-show="showWindow">' +
    '    <div class="chat-header">' +
    '      <span>AI客服 · 小黑</span>' +
    '      <i class="el-icon-close" @click="toggleWindow"></i>' +
    '    </div>' +
    '    <div class="chat-messages" ref="msgBox">' +
    '      <div v-for="(m, i) in messages" :key="i" :class="[\'chat-msg\', m.role === \'user\' ? \'user\' : \'assistant\']">' +
    '        <div class="msg-content">{{ m.content }}</div>' +
    '      </div>' +
    '      <div v-if="loading" class="chat-msg assistant">' +
    '        <div class="msg-content typing">正在输入...</div>' +
    '      </div>' +
    '    </div>' +
    '    <div class="chat-input">' +
    '      <input v-model="inputText" placeholder="输入您的问题..." ' +
    '        @keyup.enter="send" :disabled="loading" ' +
    '        style="flex:1;height:32px;padding:0 10px;border:1px solid #ddd;border-radius:16px;font-size:13px;outline:none;">' +
    '      <button @click="send" :disabled="loading" ' +
    '        style="width:56px;height:32px;background:#ff6633;color:#fff;border:none;border-radius:16px;font-size:12px;cursor:pointer;">发送</button>' +
    '    </div>' +
    '  </div>' +
    '</div>';

  var container = document.createElement('div');
  container.innerHTML = chatHTML;
  document.body.appendChild(container.firstElementChild);

  // Create Vue instance
  new Vue({
    el: '#chat-app',
    data: {
      messages: [],
      inputText: '',
      sessionId: sessionId,
      showWindow: false,
      loading: false
    },
    methods: {
      toggleWindow: function() {
        this.showWindow = !this.showWindow;
        if (this.showWindow && this.messages.length === 0) {
          this.loadHistory();
        }
        if (this.showWindow) {
          var self = this;
          this.$nextTick(function() { self.scrollToBottom(); });
        }
      },
      loadHistory: function() {
        var self = this;
        axios.get('/chat/history', {
          params: { sessionId: this.sessionId, limit: 20 },
          timeout: 10000
        })
        .then(function(res) {
          if (res.data) {
            self.messages = res.data;
          }
          self.$nextTick(function() { self.scrollToBottom(); });
        })
        .catch(function() {});
      },
      send: function() {
        var text = this.inputText.trim();
        if (!text || this.loading) return;
        this.messages.push({ role: 'user', content: text });
        this.inputText = '';
        this.loading = true;
        var self = this;
        this.$nextTick(function() { self.scrollToBottom(); });

        axios.post('/chat/rag', {
          sessionId: this.sessionId,
          message: text
        }, { timeout: 60000 })
        .then(function(res) {
          self.messages.push({ role: 'assistant', content: res.data });
        })
        .catch(function(err) {
          var msg = typeof err === 'string' ? err : '服务器异常，请稍后再试';
          self.messages.push({ role: 'assistant', content: msg });
        })
        .finally(function() {
          self.loading = false;
          self.$nextTick(function() { self.scrollToBottom(); });
        });
      },
      scrollToBottom: function() {
        var box = this.$refs.msgBox;
        if (box) box.scrollTop = box.scrollHeight;
      }
    }
  });
})();
