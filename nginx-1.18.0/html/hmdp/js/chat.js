(function() {
  var sessionId = localStorage.getItem('chat_session_id');
  if (!sessionId) {
    sessionId = 'sess_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    localStorage.setItem('chat_session_id', sessionId);
  }

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
    '      <div v-for="(m, i) in messages" :key="i" :class="[\'chat-msg\', m.role]">' +
    '        <div class="msg-content" v-html="formatMsg(m)"></div>' +
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
          if (res.data && Array.isArray(res.data)) {
            self.messages = res.data.map(function(m) {
              return { role: m.role, content: m.content };
            });
          } else if (res.data && res.data.data && Array.isArray(res.data.data)) {
            self.messages = res.data.data.map(function(m) {
              return { role: m.role, content: m.content };
            });
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
        // 用于追踪流式过程中动态追加的消息（thinking/tool/answer）
        var pendingStartIndex = this.messages.length;
        var self = this;
        this.$nextTick(function() { self.scrollToBottom(); });

        // SSE 流式请求
        fetch('/chat/react/stream', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sessionId: this.sessionId, message: text })
        }).then(function(response) {
          var reader = response.body.getReader();
          var decoder = new TextDecoder();
          var buffer = '';

          function read() {
            reader.read().then(function(result) {
              if (result.done) {
                self.loading = false;
                self.$nextTick(function() { self.scrollToBottom(); });
                return;
              }

              buffer += decoder.decode(result.value, { stream: true });
              var lines = buffer.split('\n');
              buffer = lines.pop() || ''; // 最后一个不完整行留在 buffer

              for (var i = 0; i < lines.length; i++) {
                var line = lines[i].trim();
                if (!line || !line.startsWith('data:')) continue;
                var jsonStr = line.substring(5).trim();
                if (jsonStr === '{}') continue;

                try {
                  var data = JSON.parse(jsonStr);
                  handleSSEEvent(data);
                } catch(e) {}
              }
              read();
            }).catch(function() {
              self.loading = false;
            });
          }
          read();
        }).catch(function(err) {
          self.messages.push({ role: 'assistant', content: '网络异常，请稍后再试' });
          self.loading = false;
        });

        function handleSSEEvent(data) {
          // 工具链内部状态只显示一个简洁的加载提示，不暴露 scratchpad/plan 细节
          var typingMsg = { role: 'thinking', content: '小黑正在思考中...' };

          switch (data.type) {
            case 'thinking':
            case 'tool':
              // 保持单一加载提示，不堆积多条 thinking/tool
              var lastIdx = self.messages.length - 1;
              if (lastIdx >= pendingStartIndex && self.messages[lastIdx].role === 'thinking') {
                self.messages[lastIdx] = typingMsg;
              } else {
                self.messages.push(typingMsg);
              }
              break;
            case 'answer':
              // 移除加载提示，输出最终对话
              self.messages = self.messages.filter(function(m, idx) {
                return idx < pendingStartIndex || m.role === 'user';
              });
              self.messages.push({ role: 'assistant', content: data.content || '' });
              break;
          }
          self.$nextTick(function() { self.scrollToBottom(); });
        }
      },
      formatMsg: function(m) {
        if (m.role === 'thinking') {
          return '<span style="color:#909399;font-size:12px;">&#x1F4AD; ' + this.escapeHtml(m.content) + '</span>';
        }
        if (m.role === 'user') {
          return '<div style="text-align:right;">' + this.escapeHtml(m.content) + '</div>';
        }
        // assistant: 作为对话消息输出，支持换行
        return this.escapeHtml(m.content).replace(/\n/g, '<br>');
      },
      escapeHtml: function(str) {
        if (!str) return '';
        return String(str)
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;');
      },
      scrollToBottom: function() {
        var box = this.$refs.msgBox;
        if (box) box.scrollTop = box.scrollHeight;
      }
    }
  });
})();
