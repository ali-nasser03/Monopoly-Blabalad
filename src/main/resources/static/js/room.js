// نفس ترتيب Piece.java بالباك-إند
const PIECE_ORDER = ['SHIP', 'CAR', 'HAT', 'KEY', 'COFFEE', 'BALL', 'BOOK'];
const SESSION_KEY = 'balbalad-session';

let currentRoom = null; // { code, playerId }
let stompClient = null;

// ---------- حفظ هوية اللاعب، بدون أي حساب ----------
// sessionStorage خاص بكل تبويب لحاله (ما ينلخبط لو فتحت أكتر من
// تبويب من نفس المتصفح لتجربة أكتر من لاعب). localStorage احتياطي
// بس لو فتحت تبويب جديد كليًا (أو رجعت من نفس الجهاز) خلال مهلة
// إعادة الاتصال ومافي جلسة محفوظة بهاد التبويب تحديدًا.
function saveSession(code, name, piece) {
    const payload = JSON.stringify({ code, name, piece, savedAt: Date.now() });
    try { sessionStorage.setItem(SESSION_KEY, payload); } catch (e) { /* لا شي */ }
    try { localStorage.setItem(SESSION_KEY, payload); } catch (e) { /* لا شي */ }
}
function loadSession() {
    try {
        const own = sessionStorage.getItem(SESSION_KEY);
        if (own) return JSON.parse(own);
    } catch (e) { /* لا شي */ }
    try { return JSON.parse(localStorage.getItem(SESSION_KEY)); } catch (e) { return null; }
}
function clearSession() {
    try { sessionStorage.removeItem(SESSION_KEY); } catch (e) { /* لا شي */ }
    try { localStorage.removeItem(SESSION_KEY); } catch (e) { /* لا شي */ }
}

// ---------- التنقل بين الشاشات ----------
function showScreen(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
}

function setupNavigation() {
    document.querySelectorAll('[data-goto]').forEach(btn => {
        btn.addEventListener('click', () => showScreen(btn.dataset.goto));
    });
}

// ---------- نداءات REST ----------
async function apiPost(path, body) {
    const res = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    let data = {};
    try { data = await res.json(); } catch (e) { /* لا شي */ }
    if (!res.ok) throw new Error(data.message || 'صار في خطأ، جرب مرة ثانية');
    return data;
}

// ---------- اختيار القطعة ----------
function renderPiecePicker(containerId, defaultPiece) {
    const el = document.getElementById(containerId);
    el.innerHTML = '';
    el.dataset.selected = defaultPiece;
    PIECE_ORDER.forEach(piece => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'piece-option' + (piece === defaultPiece ? ' selected' : '');
        btn.textContent = PIECE_ICONS[piece];
        btn.dataset.piece = piece;
        btn.addEventListener('click', () => {
            el.querySelectorAll('.piece-option').forEach(b => b.classList.remove('selected'));
            btn.classList.add('selected');
            el.dataset.selected = piece;
        });
        el.appendChild(btn);
    });
}

// ---------- عدد اللاعبين (شاشة الإنشاء) ----------
function setupCountPicker() {
    const wrap = document.getElementById('create-count-picker');
    wrap.querySelectorAll('.count-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            wrap.querySelectorAll('.count-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        });
    });
}

// ---------- نوع اللعبة: ضد أصحاب أو ضد الكمبيوتر (شاشة الإنشاء) ----------
function setupModePicker() {
    const wrap = document.getElementById('create-mode-picker');
    const countLabel = document.getElementById('create-count-label');
    const countWrap = document.getElementById('create-count-picker');
    const hint = document.getElementById('create-hint');

    wrap.querySelectorAll('.mode-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            wrap.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const mode = btn.dataset.mode;
            if (mode === 'bots') {
                countLabel.textContent = 'كم بوت بدك تلعب ضده؟';
                countWrap.querySelectorAll('.count-btn').forEach(b => {
                    b.textContent = b.dataset.count === '2' ? '1' : (b.dataset.count === '3' ? '2' : '3');
                });
                hint.textContent = 'رح تلعب لحالك ضد البوتات مباشرة، بدون ما تحتاج تنتظر حدا';
            } else {
                countLabel.textContent = 'عدد اللاعبين';
                countWrap.querySelectorAll('.count-btn').forEach(b => {
                    b.textContent = b.dataset.count;
                });
                hint.textContent = 'سيظهر لك كود من 4 أرقام لدعوة أصحابك';
            }
        });
    });
}

function getSelectedMode() {
    const active = document.querySelector('#create-mode-picker .mode-btn.active');
    return active ? active.dataset.mode : 'friends';
}

// ---------- أربع خانات كود الغرفة (شاشة الدخول) ----------
function setupCodeBoxes() {
    const boxes = Array.from(document.querySelectorAll('#join-code-boxes .code-box'));
    boxes.forEach((box, i) => {
        box.addEventListener('input', () => {
            box.value = box.value.replace(/[^0-9]/g, '').slice(0, 1);
            if (box.value && boxes[i + 1]) boxes[i + 1].focus();
        });
        box.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !box.value && boxes[i - 1]) boxes[i - 1].focus();
        });
    });
}
function readCode() {
    return Array.from(document.querySelectorAll('#join-code-boxes .code-box')).map(b => b.value).join('');
}

// ---------- إنشاء غرفة ----------
function setupCreateForm() {
    document.getElementById('create-submit').addEventListener('click', async () => {
        const errorEl = document.getElementById('create-error');
        errorEl.textContent = '';
        const name = document.getElementById('create-name').value.trim();
        const piece = document.getElementById('create-piece-picker').dataset.selected;
        const mode = getSelectedMode();
        const countValue = Number(document.querySelector('#create-count-picker .count-btn.active').dataset.count);

        if (!name) { errorEl.textContent = 'لازم تكتب اسمك'; return; }

        // countValue دايمًا = إجمالي عدد اللاعبين بالغرفة (2/3/4)، بغض النظر
        // عن الوضع - بوضع "ضد الكمبيوتر" بس تغيّر النص المعروض ليبين عدد
        // البوتات (= الإجمالي ناقص واحد، وهو إنت).
        const maxPlayers = countValue;

        try {
            const data = await apiPost('/api/rooms', { name, piece, maxPlayers });
            enterLobby(data, name, piece);

            if (mode === 'bots') {
                const botCount = countValue - 1;
                for (let i = 0; i < botCount; i++) {
                    const room = await apiPost('/api/rooms/' + currentRoom.code + '/add-bot', { playerId: currentRoom.playerId });
                    renderLobby(room);
                }
            }
        } catch (e) {
            errorEl.textContent = e.message;
        }
    });
}

// ---------- دخول غرفة ----------
function setupJoinForm() {
    document.getElementById('join-submit').addEventListener('click', async () => {
        const errorEl = document.getElementById('join-error');
        errorEl.textContent = '';
        const name = document.getElementById('join-name').value.trim();
        const piece = document.getElementById('join-piece-picker').dataset.selected;
        const code = readCode();

        if (!name) { errorEl.textContent = 'لازم تكتب اسمك'; return; }
        if (code.length !== 4) { errorEl.textContent = 'كود الغرفة أربع أرقام'; return; }

        try {
            const data = await apiPost('/api/rooms/' + code + '/join', { name, piece });
            enterLobby(data, name, piece);
        } catch (e) {
            errorEl.textContent = e.message;
        }
    });
}

// ---------- الدخول لغرفة الانتظار ----------
function enterLobby(data, name, piece) {
    currentRoom = { code: data.room.code, playerId: data.playerId };
    if (name && piece) saveSession(currentRoom.code, name, piece);
    connectWebSocket(currentRoom.code);
    renderLobby(data.room); // هاي القرار الوحيد: لوبي عادي، أو دخول مباشر للعبة لو بلشت أصلًا
}

function renderLobby(room) {
    if (room.started) { enterGame(room); return; }

    showScreen('screen-lobby');
    document.getElementById('lobby-code').textContent = room.code;

    const list = document.getElementById('lobby-players');
    list.innerHTML = '';
    room.players.forEach(p => {
        const li = document.createElement('li');
        li.className = 'player-item' + (p.connected ? '' : ' disconnected');
        li.innerHTML =
            '<span class="player-piece">' + PIECE_ICONS[p.piece] + '</span>' +
            '<span class="player-name">' + escapePlain(p.name) + '</span>' +
            (p.bot ? '<span class="host-badge">🤖 بوت</span>' : '') +
            (p.host ? '<span class="host-badge">المضيف</span>' : '') +
            (!p.connected && !p.bot ? '<span class="disconnected-badge">غير متصل</span>' : '');
        list.appendChild(li);
    });
    for (let i = room.players.length; i < room.maxPlayers; i++) {
        const li = document.createElement('li');
        li.className = 'player-item empty';
        li.textContent = 'بانتظار لاعب';
        list.appendChild(li);
    }

    const me = room.players.find(p => p.id === currentRoom.playerId);
    const startBtn = document.getElementById('start-game-btn');
    const addBotBtn = document.getElementById('add-bot-btn');
    const hint = document.getElementById('lobby-hint');

    if (me && me.host) {
        startBtn.style.display = '';
        startBtn.disabled = room.players.length < 2;
        hint.textContent = room.players.length < 2
            ? 'بانتظار لاعب واحد ع الأقل زيادة حتى تقدر تبلش (أو ضيف بوت)'
            : 'أنت فقط تستطيع بدء اللعبة';

        addBotBtn.classList.toggle('hidden', room.players.length >= room.maxPlayers);
    } else {
        startBtn.style.display = 'none';
        addBotBtn.classList.add('hidden');
        hint.textContent = 'بانتظار صاحب الغرفة يبدأ اللعبة...';
    }
}

function setupLobbyActions() {
    document.getElementById('start-game-btn').addEventListener('click', async () => {
        const errorEl = document.getElementById('lobby-error');
        errorEl.textContent = '';
        try {
            const data = await apiPost('/api/rooms/' + currentRoom.code + '/start', { playerId: currentRoom.playerId });
            renderLobby(data); // ينقلني أنا فورًا، بغض النظر عن توقيت وصول بث الـ WebSocket
        } catch (e) {
            errorEl.textContent = e.message;
        }
    });

    document.getElementById('add-bot-btn').addEventListener('click', async () => {
        const errorEl = document.getElementById('lobby-error');
        errorEl.textContent = '';
        try {
            const data = await apiPost('/api/rooms/' + currentRoom.code + '/add-bot', { playerId: currentRoom.playerId });
            renderLobby(data);
        } catch (e) {
            errorEl.textContent = e.message;
        }
    });

    document.getElementById('copy-code-btn').addEventListener('click', () => {
        navigator.clipboard?.writeText(currentRoom.code);
    });

    document.getElementById('lobby-back-btn').addEventListener('click', () => {
        window.returnToLandingAfterGame();
    });
}

// ---------- الاتصال اللحظي بالغرفة ----------
function connectWebSocket(code) {
    if (stompClient) {
        try { stompClient.disconnect(); } catch (e) { /* لا شي */ }
    }
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;
    stompClient.connect({}, () => {
        stompClient.subscribe('/topic/rooms/' + code, (msg) => {
            const room = JSON.parse(msg.body);
            const alreadyInGameScreen = document.getElementById('screen-game').classList.contains('active');
            if (alreadyInGameScreen && window.onRoomUpdate) {
                // أنا أصلًا بشاشة اللعب: هاد بس تحديث اتصال/انقطاع لاعب، حدّث القائمة بس
                window.onRoomUpdate(room);
            } else {
                // لسا باللوبي (أو شاشة سابقة): خلي renderLobby تقرر - تعرض اللوبي، أو تنقلني للعبة لو بلشت هلأ
                renderLobby(room);
            }
        });
        // مشترك من هلأ بقناة اللعبة كمان (حتى لو لسا ما بلشت)، حتى ما
        // نضيع أول تحديث لو اللعبة بلشت قبل ما نوصل نشترك.
        stompClient.subscribe('/topic/games/' + code, (msg) => {
            if (window.onGameStateUpdate) window.onGameStateUpdate(JSON.parse(msg.body));
        });
        stompClient.send('/app/rooms/' + code + '/register', {}, JSON.stringify({ playerId: currentRoom.playerId }));

        // تحقق فوري من الحالة الحقيقية (مش بس انتظار بث جاي): لو اللعبة
        // بلشت بالضبط بالفجوة الزمنية قبل ما نخلص نشترك، ما منضيع هالخبر.
        fetch('/api/rooms/' + code)
            .then(r => r.ok ? r.json() : null)
            .then(room => {
                if (!room) return;
                const alreadyInGameScreen = document.getElementById('screen-game').classList.contains('active');
                if (alreadyInGameScreen && window.onRoomUpdate) {
                    window.onRoomUpdate(room);
                } else {
                    renderLobby(room);
                }
            })
            .catch(() => { /* لا شي، البث العادي بيغطي باقي الحالات */ });
    });
}

// ---------- الانتقال لشاشة اللعب ----------
function enterGame(room) {
    showScreen('screen-game');
    if (window.startBoardGame) {
        window.startBoardGame(room.players, currentRoom.playerId, currentRoom.code);
    }
}

// board.js بينادي هاي لما تنتهي اللعبة فعليًا (تصويت إجماع) والمستخدم
// يدوس "الرجوع للصفحة الرئيسية"، أو تلقائيًا بعد مهلة قصيرة.
window.returnToLandingAfterGame = function () {
    clearSession();
    currentRoom = null;
    if (stompClient) { try { stompClient.disconnect(); } catch (e) { /* لا شي */ } }
    showScreen('screen-landing');
};

function escapePlain(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ---------- محاولة رجوع تلقائي عند فتح الصفحة ----------
// لو المستخدم دخل غرفة قبل هيك (بنفس المتصفح)، منعبّي شاشة "دخول
// لعبة" تلقائيًا بنفس اسمه وقطعته، ومنجرب ندخله عالغرفة مباشرة
// بدون ما يضغط أي شي. لو الغرفة خلصت أو ما عادت موجودة، منمسح
// الجلسة القديمة ومنسيبه عالشاشة الرئيسية عادي.
async function tryAutoReconnect() {
    const session = loadSession();
    if (!session || !session.code || !session.name || !session.piece) return;

    document.getElementById('join-name').value = session.name;
    renderPiecePicker('join-piece-picker', session.piece);
    const boxes = document.querySelectorAll('#join-code-boxes .code-box');
    String(session.code).split('').forEach((d, i) => { if (boxes[i]) boxes[i].value = d; });

    try {
        const data = await apiPost('/api/rooms/' + session.code + '/join', { name: session.name, piece: session.piece });
        enterLobby(data, session.name, session.piece);
    } catch (e) {
        clearSession();
    }
}

setupNavigation();
renderPiecePicker('create-piece-picker', 'CAR');
renderPiecePicker('join-piece-picker', 'SHIP');
setupCountPicker();
setupModePicker();
setupCodeBoxes();
setupCreateForm();
setupJoinForm();
setupLobbyActions();
tryAutoReconnect();