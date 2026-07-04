(function() {
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
    '      <div v-if="!hasMore && messages.length > 0" class="chat-no-more">没有更多历史消息</div>' +
    '      <div v-if="loadingHistory" class="chat-loading-top">加载中...</div>' +
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
      showWindow: false,
      loading: false,
      loadingHistory: false,
      hasMore: true,
      oldestId: null
    },
    mounted: function() {
      var self = this;
      this.$nextTick(function() {
        var box = self.$refs.msgBox;
        if (box) {
          box.addEventListener('scroll', function() {
            if (box.scrollTop <= 30 && !self.loadingHistory && self.hasMore) {
              self.loadHistory();
            }
          });
        }
      });
    },
    methods: {
      toggleWindow: function() {
        var token = null;
        try { token = sessionStorage.getItem("token"); } catch(e) {}
        console.log('[chat] toggleWindow: token=' + (token ? token.substring(0, 20) + '...' : 'null/empty'));
        if (!token) {
          console.log('[chat] no token, redirecting to login');
          window.location.href = "/login.html";
          return;
        }
        this.showWindow = !this.showWindow;
        if (this.showWindow && this.messages.length === 0) {
          this.oldestId = null;
          this.hasMore = true;
          this.loadHistory();
        }
        if (this.showWindow) {
          var self = this;
          this.$nextTick(function() { self.scrollToBottom(); });
        }
      },
      loadHistory: function() {
        var self = this;
        if (this.loadingHistory || !this.hasMore) return;

        this.loadingHistory = true;
        var params = { limit: 10 };
        if (this.oldestId !== null) {
          params.beforeId = this.oldestId;
        }

        axios.get('/chat/history', { params: params, timeout: 10000 })
          .then(function(res) {
            var payload = (res.data && res.data.data) ? res.data.data : res.data;
            var rounds = payload.rounds || [];
            var hasMore = payload.hasMore !== undefined ? payload.hasMore : rounds.length >= 10;

            if (rounds.length > 0) {
              // serverRounds is DESC (newest first), make a copy before reversing
              var serverRounds = rounds.slice();
              rounds.reverse();

              var newMessages = [];
              rounds.forEach(function(r) {
                newMessages.push({ role: 'user', content: r.userMessage });
                newMessages.push({ role: 'assistant', content: r.assistantMessage });
              });

              // serverRounds is DESC, so last item is the oldest in this batch
              self.oldestId = serverRounds[serverRounds.length - 1].id;

              var prevLen = self.messages.length;
              self.messages = newMessages.concat(self.messages);

              if (prevLen > 0) {
                // Loading more: stabilize scroll position
                self.$nextTick(function() {
                  var box = self.$refs.msgBox;
                  if (box) {
                    box.scrollTop = newMessages.length * 64;
                  }
                });
              } else {
                // First load: scroll to bottom
                self.$nextTick(function() { self.scrollToBottom(); });
              }
            }

            self.hasMore = hasMore;
            self.loadingHistory = false;
          })
          .catch(function(err) {
            self.loadingHistory = false;
            if (err && err.response && err.response.status === 401) {
              window.location.href = "/login.html";
            }
          });
      },
      send: function() {
        var text = this.inputText.trim();
        if (!text || this.loading) return;

        this.messages.push({ role: 'user', content: text });
        this.inputText = '';
        this.loading = true;
        var pendingStartIndex = this.messages.length;
        var self = this;
        this.$nextTick(function() { self.scrollToBottom(); });

        var token = sessionStorage.getItem("token") || '';

        fetch('/chat/react/stream', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'authorization': token
          },
          body: JSON.stringify({ message: text })
        }).then(function(response) {
          if (response.status === 401) {
            window.location.href = "/login.html";
            return;
          }
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
              buffer = lines.pop() || '';

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
        }).catch(function() {
          self.messages.push({ role: 'assistant', content: '网络异常，请稍后再试' });
          self.loading = false;
        });

        var tokenQueue = [];
        var tokenTimer = null;
        var TOKEN_DELAY = 50; // ms 间隔，越大打字效果越慢越明显

        function handleSSEEvent(data) {
          switch (data.type) {
            case 'thinking':
              var thinkMsg = { role: 'thinking', content: data.content || '小黑正在思考中...' };
              if (data.plan) thinkMsg.plan = data.plan;
              replaceOrPushThinking(self, thinkMsg, pendingStartIndex);
              break;
            case 'tool':
              var toolMsg = { role: 'tooling', content: data.content || '正在调用工具...',
                              toolName: data.toolName, toolResult: data.toolResult };
              replaceOrPushThinking(self, toolMsg, pendingStartIndex);
              break;
            case 'retry':
              var retryMsg = { role: 'tooling', content: data.content || '正在重试...',
                               toolName: data.tool };
              replaceOrPushThinking(self, retryMsg, pendingStartIndex);
              break;
            case 'answer_chunk':
              tokenQueue.push(data.content || '');
              if (!tokenTimer) {
                tokenTimer = setTimeout(drainTokenQueue, TOKEN_DELAY);
              }
              break;
            case 'answer':
              // 不替换逐字流式消息 — 把 assistant_streaming 就地转为 assistant（去掉光标即可）
              flushTokenQueue();
              for (var j = self.messages.length - 1; j >= pendingStartIndex; j--) {
                if (self.messages[j].role === 'assistant_streaming') {
                  var finalContent = self.messages[j].content;
                  self.messages.splice(j, 1, { role: 'assistant', content: finalContent });
                  break;
                }
              }
              break;
          }
          if (data.type !== 'answer_chunk') {
            self.$nextTick(function() { self.scrollToBottom(); });
          }
        }

        function drainTokenQueue() {
          if (tokenQueue.length === 0) {
            tokenTimer = null;
            return;
          }
          var token = tokenQueue.shift();
          var acIdx = self.messages.length - 1;
          if (acIdx >= pendingStartIndex && self.messages[acIdx].role === 'assistant_streaming') {
            // Direct property mutation — Vue 2 can't detect, need full replace
            var cur = self.messages[acIdx];
            self.messages.splice(acIdx, 1, { role: 'assistant_streaming', content: cur.content + token });
          } else {
            self.messages = self.messages.filter(function(m, idx) {
              return idx < pendingStartIndex || m.role === 'user';
            });
            self.messages.push({ role: 'assistant_streaming', content: token });
          }
          // Conditional scroll: only auto-follow if user is near bottom
          self.$nextTick(function() {
            var box = self.$refs.msgBox;
            if (box) {
              var distToBottom = box.scrollHeight - box.scrollTop - box.clientHeight;
              if (distToBottom < 80) {
                box.scrollTop = box.scrollHeight;
              }
            }
          });
          tokenTimer = setTimeout(drainTokenQueue, TOKEN_DELAY);
        }

        function flushTokenQueue() {
          if (tokenTimer) {
            clearTimeout(tokenTimer);
            tokenTimer = null;
          }
          if (tokenQueue.length === 0) return;
          var remaining = tokenQueue.join('');
          tokenQueue = [];
          var fi = self.messages.length - 1;
          if (fi >= pendingStartIndex && self.messages[fi].role === 'assistant_streaming') {
            var upd = { role: 'assistant_streaming',
                        content: self.messages[fi].content + remaining };
            self.messages.splice(fi, 1, upd);
          } else {
            self.messages = self.messages.filter(function(m, idx) {
              return idx < pendingStartIndex || m.role === 'user';
            });
            self.messages.push({ role: 'assistant_streaming', content: remaining });
          }
        }

        function replaceOrPushThinking(self, msg, pendingStartIndex) {
          // Remove any in-progress streaming assistant message
          self.messages = self.messages.filter(function(m) {
            return m.role !== 'assistant_streaming';
          });
          var lastIdx = self.messages.length - 1;
          if (lastIdx >= pendingStartIndex &&
              (self.messages[lastIdx].role === 'thinking' || self.messages[lastIdx].role === 'tooling')) {
            self.messages.splice(lastIdx, 1, msg);
          } else {
            self.messages.push(msg);
          }
        }
      },
      formatMsg: function(m) {
        if (m.role === 'thinking') {
          var text = '<span style="color:#909399;font-size:12px;">&#x1F4AD; ' + this.escapeHtml(m.content) + '</span>';
          if (m.plan) {
            var planPreview = m.plan.length > 120 ? m.plan.substring(0, 120) + '...' : m.plan;
            text += '<div style="font-size:11px;color:#c0c4cc;margin-top:2px;">' + this.escapeHtml(planPreview) + '</div>';
          }
          return text;
        }
        if (m.role === 'tooling') {
          var t = '<span style="color:#409eff;font-size:12px;">&#x2699; ' + this.escapeHtml(m.content) + '</span>';
          if (m.toolResult) {
            var resultPreview = m.toolResult.length > 200 ? m.toolResult.substring(0, 200) + '...' : m.toolResult;
            t += '<div style="font-size:11px;color:#c0c4cc;margin-top:2px;max-height:60px;overflow:hidden;">' + this.escapeHtml(resultPreview) + '</div>';
          }
          return t;
        }
        if (m.role === 'user') {
          return '<div style="text-align:right;">' + this.escapeHtml(m.content) + '</div>';
        }
        if (m.role === 'assistant_streaming') {
          return this.escapeHtml(m.content).replace(/\n/g, '<br>') + '<span class="cursor-blink">|</span>';
        }
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
