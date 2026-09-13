// ---------- أصوات بسيطة مركّبة برمجيًا (بدون أي ملف صوتي خارجي) ----------
let audioCtx = null;
function getAudioCtx() {
    if (!audioCtx) {
        const Ctor = window.AudioContext || window.webkitAudioContext;
        if (!Ctor) return null;
        audioCtx = new Ctor();
    }
    return audioCtx;
}

function playTone(freq, duration, type, delay) {
    try {
        const ctx = getAudioCtx();
        if (!ctx) return;
        delay = delay || 0;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = type || 'sine';
        osc.frequency.value = freq;
        const start = ctx.currentTime + delay;
        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.exponentialRampToValueAtTime(0.16, start + 0.012);
        gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(start);
        osc.stop(start + duration + 0.02);
    } catch (e) { /* الصوت مش أساسي للعبة، نتجاهل أي خطأ */ }
}

function playDiceSound() {
    // نحاكي صوت النرد الحقيقي: كم "طقة" متتالية (تشويش مصفّى، مش نغمة
    // نقية) بأزمنة وترددات عشوائية شوي، تماشيًا مع طريقة النرد الحقيقي
    // يلي بيرتطم ويترنح على السطح كذا مرة قبل ما يستقر.
    try {
        const ctx = getAudioCtx();
        if (!ctx) return;
        const knocks = 6 + Math.floor(Math.random() * 3);
        let t = 0;
        for (let i = 0; i < knocks; i++) {
            playDiceKnock(t, 1 - i / (knocks + 2));
            t += 0.035 + Math.random() * 0.05;
        }
    } catch (e) { /* الصوت مش أساسي للعبة، نتجاهل أي خطأ */ }
}

function playDiceKnock(delay, loudness) {
    try {
        const ctx = getAudioCtx();
        if (!ctx) return;
        const duration = 0.045 + Math.random() * 0.03;

        // تشويش أبيض قصير = صوت طقّة/ارتطام، مش نغمة نقية
        const bufferSize = Math.floor(ctx.sampleRate * duration);
        const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < bufferSize; i++) data[i] = Math.random() * 2 - 1;

        const source = ctx.createBufferSource();
        source.buffer = buffer;

        const bandpass = ctx.createBiquadFilter();
        bandpass.type = 'bandpass';
        bandpass.frequency.value = 900 + Math.random() * 1400;
        bandpass.Q.value = 0.7;

        const gain = ctx.createGain();
        const start = ctx.currentTime + delay;
        const peak = 0.3 * (loudness == null ? 1 : loudness);
        gain.gain.setValueAtTime(peak, start);
        gain.gain.exponentialRampToValueAtTime(0.001, start + duration);

        source.connect(bandpass);
        bandpass.connect(gain);
        gain.connect(ctx.destination);
        source.start(start);
        source.stop(start + duration + 0.01);
    } catch (e) { /* الصوت مش أساسي للعبة، نتجاهل أي خطأ */ }
}

function playCashSound() {
    playTone(660, 0.09, 'sine', 0);
    playTone(880, 0.13, 'sine', 0.08);
}
function playPaySound() {
    playTone(320, 0.14, 'sine', 0);
    playTone(220, 0.18, 'sine', 0.09);
}
function playErrorSound() {
    playTone(150, 0.18, 'sawtooth', 0);
}

// ---------- تنبيهات منبثقة (toast) بدل alert() المزعجة ----------
function showToast(message, isError) {
    const container = document.getElementById('toast-container');
    if (!container) { alert(message); return; }
    if (isError) playErrorSound();
    const el = document.createElement('div');
    el.className = 'toast';
    el.textContent = message;
    container.appendChild(el);
    setTimeout(() => el.remove(), 3000);
}

// ألوان مجموعات الأراضي على الشاشة فقط - هوية "مونوبولي بالبلد"
// (خشب/حجر/زيتي/عنابي/ذهبي)، منفصلة تمامًا عن أسماء الـ enum بالباك-إند.
const GROUP_COLORS = {
    NONE: null,
    BROWN: '#5c3a21',
    LIGHT_BLUE: '#4f8fa3',
    PINK: '#a85073',
    ORANGE: '#c17a34',
    RED: '#8f3331',
    YELLOW: '#b8922b',
    GREEN: '#4c5f31',
    DARK_BLUE: '#243759'
};

const TYPE_ICONS = {
    GO: '🏁',
    CHANCE: '❓',
    COMMUNITY_CHEST: '🎁',
    JERUSALEM_GATE: '🕌',
    TAX: '🧾',
    FREE_PARKING: '☕',
    JAIL: '🔒',
    GO_TO_JAIL: '🚔',
    UTILITY: '⚡'
};

const RENT_LABELS = ['عادي', 'دار واحدة', 'داران', '3 دور', '4 دور', 'عمارة'];

// نفس ColorGroup.getHouseCost() بالباك-إند
const GROUP_HOUSE_COST = {
    BROWN: 50, LIGHT_BLUE: 50, PINK: 100, ORANGE: 100,
    RED: 150, YELLOW: 150, GREEN: 200, DARK_BLUE: 200
};

// نفس أسماء enum الـ Piece بالباك-إند (Piece.java)
const PIECE_ICONS = {
    SHIP: '⛵', CAR: '🚗', HAT: '🎩', KEY: '🔑',
    COFFEE: '☕', BALL: '⚽', BOOK: '📖'
};

const CARD_DECK_TITLES = {
    CHANCE: '❓ فرصة',
    COMMUNITY_CHEST: '🎁 صندوق الجماعة',
    GATE: '🕌 بوابة القدس'
};

// ---------- حالة الجلسة الحالية (تتعبى من startBoardGame) ----------
let myPlayers = [];      // آخر قائمة لاعبين معروفة (من الغرفة)
let myPlayerId = null;
let roomCode = null;
let gameState = null;    // آخر حالة لعبة معروفة (من /api/games/{code})
let tilesByPosition = {}; // رقم الخانة -> عنصر DOM تبعها (لرسم القطع فوقها)
let squaresByPosition = {}; // رقم الخانة -> بيانات الأرض (اسم/سعر/نوع) من /api/board
let myBalance = 1500;
let lastKnownBalance = null; // لمقارنة الرصيد الجديد بالقديم وعرض +/- مؤقتًا
let previousPositions = null; // آخر مواقع معروفة لكل اللاعبين، لكشف مين تحرك أخيرًا
let lastSeenCard = null; // نص آخر بطاقة عرضناها، حتى ما نكرر نفس الإعلان
let cardHideTimeout = null;
let countdownInterval = null;
let auctionCountdownInterval = null;
let negotiationCountdownInterval = null;

/**
 * يحوّل رقم الخانة (0-39) لموقعها بشبكة 11×11.
 * خانة 0 = أسفل يمين، وأبو ديس (خانة 1) عشمالها مباشرة، والحركة
 * كلها يمين-لشمال ثم تلف حول اللوح.
 */
function positionToCell(pos) {
    if (pos <= 10) return { row: 11, col: 1 + pos };
    if (pos <= 20) return { row: 11 - (pos - 10), col: 11 };
    if (pos <= 30) return { row: 1, col: 11 - (pos - 20) };
    return { row: 1 + (pos - 30), col: 1 };
}

async function loadBoard() {
    const boardEl = document.getElementById('board-grid');
    try {
        const res = await fetch('/api/board');
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const squares = await res.json();
        renderBoard(squares);
    } catch (err) {
        boardEl.innerHTML = '<p class="board-error">تعذّر تحميل بيانات اللوح من الخادم.<br>شغّل مشروع Spring Boot (mvn spring-boot:run) وافتح الصفحة من على السيرفر.</p>';
        console.error(err);
    }
}

function renderBoard(squares) {
    const boardEl = document.getElementById('board-grid');
    boardEl.innerHTML = '';
    tilesByPosition = {};
    squaresByPosition = {};

    squares.forEach(sq => {
        const cell = positionToCell(sq.position);
        const tile = document.createElement('div');
        tile.className = 'tile type-' + sq.type.toLowerCase();
        tile.style.gridRow = String(cell.row);
        tile.style.gridColumn = String(cell.col);
        tile.dataset.position = sq.position;

        if (sq.colorGroup && sq.colorGroup !== 'NONE') {
            const stripe = document.createElement('div');
            stripe.className = 'color-stripe';
            stripe.style.backgroundColor = GROUP_COLORS[sq.colorGroup];
            tile.appendChild(stripe);
        }

        const nameEl = document.createElement('div');
        nameEl.className = 'tile-name';
        nameEl.textContent = sq.name;
        tile.appendChild(nameEl);

        if (sq.price != null) {
            const priceEl = document.createElement('div');
            priceEl.className = 'tile-price';
            priceEl.textContent = '₪' + sq.price;
            tile.appendChild(priceEl);
        } else if (TYPE_ICONS[sq.type]) {
            const iconEl = document.createElement('div');
            iconEl.className = 'tile-icon';
            iconEl.textContent = TYPE_ICONS[sq.type];
            tile.appendChild(iconEl);
        }

        const tokensEl = document.createElement('div');
        tokensEl.className = 'tile-tokens';
        tile.appendChild(tokensEl);

        attachPressHandlers(tile, sq);
        boardEl.appendChild(tile);
        tilesByPosition[sq.position] = tile;
        squaresByPosition[sq.position] = sq;
    });

    renderCenter(boardEl);
    renderTokens();
}

function renderCenter(boardEl) {
    const center = document.createElement('div');
    center.className = 'board-center';
    center.style.gridRow = '2 / 11';
    center.style.gridColumn = '2 / 11';
    center.innerHTML = '<div class="board-title">مونوبولي بالبلد</div>';
    boardEl.appendChild(center);
}

// ---- أختام الملكية فوق الأراضي المملوكة ----
function renderOwnershipStamps() {
    Object.values(tilesByPosition).forEach(tile => {
        const stamp = tile.querySelector('.ownership-stamp');
        if (stamp) stamp.remove();
        const houseBadge = tile.querySelector('.house-badge');
        if (houseBadge) houseBadge.remove();
        tile.classList.remove('owned', 'mortgaged');
    });
    if (!gameState || !gameState.ownership) return;
    Object.entries(gameState.ownership).forEach(([pos, ownerId]) => {
        const tile = tilesByPosition[pos];
        if (!tile) return;
        const owner = myPlayers.find(p => p.id === ownerId);
        tile.classList.add('owned');
        if (gameState.mortgaged && gameState.mortgaged[pos]) tile.classList.add('mortgaged');

        const stamp = document.createElement('div');
        stamp.className = 'ownership-stamp';
        stamp.textContent = owner ? (PIECE_ICONS[owner.piece] || '●') : '●';
        stamp.title = owner ? owner.name : '';
        tile.appendChild(stamp);

        const houseLevel = gameState.houses ? (gameState.houses[pos] || 0) : 0;
        if (houseLevel > 0) {
            const badge = document.createElement('div');
            badge.className = 'house-badge';
            badge.textContent = houseLevel === 5 ? '🏨' : '🏠'.repeat(houseLevel);
            tile.appendChild(badge);
        }
    });
}

// ---- قطع اللاعبين فوق خاناتهم الحالية ----
let animatingPlayers = new Set(); // لاعبين قطعتهم عم تتحرك هلق - renderTokens ما يلمسهم

function renderTokens() {
    Object.values(tilesByPosition).forEach(tile => {
        const container = tile.querySelector('.tile-tokens');
        if (!container) return;
        Array.from(container.children).forEach(child => {
            if (!animatingPlayers.has(child.dataset.player)) child.remove();
        });
    });
    if (!gameState) return;
    myPlayers.forEach(p => {
        if (animatingPlayers.has(p.id)) return; // الأنيميشن بتدير قطعته لحالها
        const pos = gameState.positions ? gameState.positions[p.id] : undefined;
        if (pos == null) return;
        const tile = tilesByPosition[pos];
        const container = tile && tile.querySelector('.tile-tokens');
        if (!container) return;
        const span = document.createElement('span');
        span.className = 'token';
        span.textContent = PIECE_ICONS[p.piece] || '●';
        span.title = p.name;
        span.dataset.player = p.id;
        container.appendChild(span);
    });
}

/** يحط قطعة لاعب واحد بخانة محددة، ويشيلها من أي مكان قديم كانت فيه. */
function placeTokenAt(playerId, position) {
    document.querySelectorAll('.token[data-player="' + playerId + '"]').forEach(el => el.remove());
    const tile = tilesByPosition[position];
    const container = tile && tile.querySelector('.tile-tokens');
    const player = myPlayers.find(p => p.id === playerId);
    if (!container || !player) return;
    const span = document.createElement('span');
    span.className = 'token';
    span.textContent = PIECE_ICONS[player.piece] || '●';
    span.title = player.name;
    span.dataset.player = playerId;
    container.appendChild(span);
}

const TOKEN_STEP_MS = 140;

/** يحرك قطعة اللاعب مربع-مربع من fromPos لـ toPos، بدل ما تختفي وتطلع مباشرة. */
function animateTokenMovement(playerId, fromPos, toPos, teleport) {
    if (teleport || fromPos == null || fromPos === toPos) {
        placeTokenAt(playerId, toPos);
        return 0;
    }
    const path = [];
    let cur = fromPos;
    for (let i = 0; i < 40; i++) {
        cur = (cur + 1) % 40;
        path.push(cur);
        if (cur === toPos) break;
    }

    animatingPlayers.add(playerId);
    let stepIndex = 0;
    function step() {
        if (stepIndex >= path.length) {
            animatingPlayers.delete(playerId);
            placeTokenAt(playerId, toPos);
            return;
        }
        placeTokenAt(playerId, path[stepIndex]);
        stepIndex++;
        setTimeout(step, TOKEN_STEP_MS);
    }
    step();
    return path.length * TOKEN_STEP_MS; // مدة الأنيميشن الكلية، حتى نعرف قد إيش ننتظر
}

// ---- ضغطة طويلة (موبايل/تابلت) أو كبسة (ديسكتوب) لفتح بطاقة الأرض ----
function attachPressHandlers(tile, sq) {
    let pressTimer = null;
    const LONG_PRESS_MS = 420;

    const start = () => { pressTimer = setTimeout(() => openPropertyCard(sq), LONG_PRESS_MS); };
    const cancel = () => { if (pressTimer) clearTimeout(pressTimer); };

    tile.addEventListener('touchstart', start, { passive: true });
    tile.addEventListener('touchend', cancel);
    tile.addEventListener('touchmove', cancel);
    tile.addEventListener('mousedown', start);
    tile.addEventListener('mouseup', cancel);
    tile.addEventListener('mouseleave', cancel);
    tile.addEventListener('click', () => openPropertyCard(sq));
}

function openPropertyCard(sq) {
    const modal = document.getElementById('property-modal');
    const content = document.getElementById('property-card-content');

    const ownerId = gameState && gameState.ownership ? gameState.ownership[sq.position] : null;
    const houses = gameState && gameState.houses ? (gameState.houses[sq.position] || 0) : 0;
    const isMortgaged = gameState && gameState.mortgaged ? !!gameState.mortgaged[sq.position] : false;

    let html = '<h2>' + escapeHtml(sq.name) + '</h2>';

    if (ownerId) {
        const owner = myPlayers.find(p => p.id === ownerId);
        html += '<p class="card-price">' + (PIECE_ICONS[owner ? owner.piece : ''] || '●') +
            ' مملوكة لـ ' + escapeHtml(owner ? owner.name : '') +
            (isMortgaged ? ' — مرهونة' : '') +
            (houses === 5 ? ' — فيها عمارة' : houses > 0 ? ' — فيها ' + houses + ' دار' : '') +
            '</p>';
    }

    if (sq.type === 'PROPERTY') {
        html += '<p class="card-price">سعر الأرض: ₪' + sq.price + ' — الرهن: ₪' + Math.floor(sq.price / 2) + '</p>';
        html += '<table class="rent-table"><tbody>';
        sq.rentTable.forEach((rent, i) => {
            html += '<tr' + (houses === i ? ' class="current-rent"' : '') + '><td>' + RENT_LABELS[i] + '</td><td>₪' + rent + '</td></tr>';
        });
        html += '</tbody></table>';
    } else if (sq.type === 'UTILITY') {
        html += '<p class="card-price">سعر الشراء: ₪' + sq.price + '</p>';
        html += '<p>الإيجار: مجموع النرد ×4 لمرفق واحد، أو ×10 للمرفقين معًا. لا بناء عليه.</p>';
    } else if (sq.type === 'TAX') {
        html += '<p class="card-price">تدفع ₪' + sq.price + ' للبنك</p>';
    } else if (sq.type === 'JERUSALEM_GATE') {
        html += '<p>بوابة قدسية — تسحب بطاقة عشوائية من مجموعة بوابات القدس عند الوقوف هون. غير قابلة للشراء.</p>';
    } else if (sq.type === 'JAIL') {
        html += '<p>المسكوبية: زيارة فقط لمن يمر عادي، أو سجن فعلي لمن يُرسل إليها.</p>';
    } else if (sq.type === 'GO_TO_JAIL') {
        html += '<p>الانتقال المباشر إلى المسكوبية كسجين.</p>';
    } else if (sq.type === 'FREE_PARKING') {
        html += '<p>استراحة مجانية — لا إجراء.</p>';
    } else if (sq.type === 'GO') {
        html += '<p>عند المرور من هون: ₪200.</p>';
    } else {
        html += '<p>اسحب بطاقة من المجموعة المناسبة.</p>';
    }

    // أزرار الإدارة - لمالك الأرض بس
    if (ownerId === myPlayerId) {
        if (sq.type === 'PROPERTY' && !isMortgaged) {
            const cost = GROUP_HOUSE_COST[sq.colorGroup];
            html += '<div class="endvote-actions">';
            html += '<button id="pc-build-btn" class="wood-btn">ابنِ دار (₪' + cost + ')</button>';
            if (houses > 0) {
                html += '<button id="pc-demolish-btn" class="wood-btn secondary">اهدم دار (+₪' + Math.floor(cost / 2) + ')</button>';
            }
            html += '</div>';
        }
        if ((sq.type === 'PROPERTY' || sq.type === 'UTILITY') && houses === 0) {
            if (isMortgaged) {
                const unmortCost = Math.ceil(sq.price / 2 * 1.1);
                html += '<button id="pc-unmortgage-btn" class="wood-btn">افك الرهن (₪' + unmortCost + ')</button>';
            } else if (!groupHasAnyHousesInGroup(sq.colorGroup)) {
                html += '<button id="pc-mortgage-btn" class="wood-btn danger">ارهن (+₪' + Math.floor(sq.price / 2) + ')</button>';
            } else {
                html += '<p class="hint-text">ما تقدر ترهنها - في بناء على أرض تانية بنفس المجموعة، اهدمه أول.</p>';
            }
        }
    }

    content.innerHTML = html;
    modal.classList.remove('hidden');

    bindIfExists('pc-build-btn', () => propertyAction(sq.position, 'build'));
    bindIfExists('pc-demolish-btn', () => propertyAction(sq.position, 'demolish'));
    bindIfExists('pc-mortgage-btn', () => propertyAction(sq.position, 'mortgage'));
    bindIfExists('pc-unmortgage-btn', () => propertyAction(sq.position, 'unmortgage'));
}

function bindIfExists(id, handler) {
    const el = document.getElementById(id);
    if (el) el.addEventListener('click', handler);
}

function groupHasAnyHousesInGroup(colorGroup) {
    if (!colorGroup || colorGroup === 'NONE') return false;
    return Object.values(squaresByPosition)
        .filter(s => s.colorGroup === colorGroup)
        .some(s => (gameState.houses[s.position] || 0) > 0);
}

async function propertyAction(position, action) {
    if (action === 'demolish' && !confirm('متأكد بدك تهدم دار من هاي الأرض؟ ما فيها رجوع.')) return;
    const result = await postAction('/properties/' + position + '/' + action, { playerId: myPlayerId });
    if (result) {
        gameState = result;
        renderTurnUI();
        refreshMyBalance();
        const sq = squaresByPosition[position];
        if (sq) openPropertyCard(sq); // نفتح نفس البطاقة محدثة حتى يشوف النتيجة فورًا
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function setupModalClose() {
    document.getElementById('close-modal').addEventListener('click', () => {
        document.getElementById('property-modal').classList.add('hidden');
    });
    document.getElementById('property-modal').addEventListener('click', (e) => {
        if (e.target.id === 'property-modal') e.currentTarget.classList.add('hidden');
    });
}

// ---------- النرد الحقيقي ----------
async function performRoll() {
    const rollBtn = document.getElementById('roll-btn');
    const jailRollBtn = document.getElementById('jail-roll-btn');
    rollBtn.disabled = true;
    jailRollBtn.disabled = true;
    try {
        const res = await fetch('/api/games/' + roomCode + '/roll', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId: myPlayerId })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showToast(data.message || 'ما قدرت ترمي هلأ', true);
            rollBtn.disabled = false;
            jailRollBtn.disabled = false;
        }
        // التحديث الفعلي (موقعك الجديد، الدور الجاي...) بيوصل للكل عبر
        // WebSocket، وrenderTurnUI بترجع تظبط حالة الأزرار لحالها.
    } catch (e) {
        console.error(e);
        rollBtn.disabled = false;
        jailRollBtn.disabled = false;
    }
}

function setupDiceRoll() {
    document.getElementById('roll-btn').addEventListener('click', performRoll);
}

function animateDiceRoll(d1, d2) {
    const el1 = document.getElementById('die-1');
    const el2 = document.getElementById('die-2');
    el1.classList.add('rolling');
    el2.classList.add('rolling');
    setTimeout(() => {
        el1.textContent = String(d1);
        el2.textContent = String(d2);
        el1.classList.remove('rolling');
        el2.classList.remove('rolling');
    }, 300);
}

// ---------- لوحة اللاعبين ----------
function renderRealPlayers(players, myId) {
    myPlayers = players;
    const list = document.getElementById('players-list');
    list.innerHTML = '';
    players.forEach(p => {
        const li = document.createElement('li');
        li.className = 'player-item' + (p.connected ? '' : ' disconnected');
        li.dataset.playerId = p.id;
        const suffix = p.id === myId ? ' (أنت)' : '';
        const jailed = gameState && gameState.inJail && gameState.inJail[p.id];
        li.innerHTML = '<span class="player-piece">' + (PIECE_ICONS[p.piece] || '●') + '</span>' +
            '<span class="player-name">' + (jailed ? '<span class="jail-icon">🔒</span>' : '') +
            escapeHtml(p.name) + suffix + '</span>' +
            (!p.connected ? '<span class="disconnected-badge">غير متصل</span>' : '');
        list.appendChild(li);
    });
    highlightCurrentTurn();
}

function highlightCurrentTurn() {
    const currentId = gameState ? gameState.currentTurnPlayerId : null;
    document.querySelectorAll('#players-list .player-item').forEach(li => {
        li.classList.toggle('active', li.dataset.playerId === currentId);
    });
}

// ---------- مؤشر الدور والمؤقت والنرد ----------
let lastPlayedDiceSignature = null;

function renderTurnUI(actionDelayMs) {
    if (!gameState) return;
    actionDelayMs = actionDelayMs || 0;

    if (gameState.lastDie1) {
        // نستخدم توقيت مهلة الدور كجزء من "بصمة" الرمية، حتى نميّز رميتين
        // متتاليتين طلع فيهم نفس الرقمين بالصدفة (وما نعيد الاهتزاز/الصوت
        // على أي تحديث حالة تاني مالوش علاقة بالنرد أصلًا).
        const diceSig = gameState.lastDie1 + '-' + gameState.lastDie2 + '-' + gameState.turnDeadlineEpochMs;
        if (diceSig !== lastPlayedDiceSignature) {
            lastPlayedDiceSignature = diceSig;
            animateDiceRoll(gameState.lastDie1, gameState.lastDie2);
            playDiceSound();
        }
    }

    renderEventLog();

    const busy = !!gameState.pendingPurchase || !!gameState.auction || !!gameState.pendingDebt;
    const iAmJailed = !!(gameState.inJail && gameState.inJail[myPlayerId]);
    const isMyTurn = gameState.currentTurnPlayerId === myPlayerId && !gameState.ended && !busy && !iAmJailed;
    const rollBtn = document.getElementById('roll-btn');
    rollBtn.disabled = !isMyTurn;

    const statusEl = document.getElementById('turn-status');
    if (gameState.ended) {
        statusEl.textContent = 'اللعبة خلصت';
    } else if (gameState.pendingDebt) {
        const debtor = myPlayers.find(p => p.id === gameState.pendingDebt.playerId);
        statusEl.textContent = gameState.pendingDebt.playerId === myPlayerId
            ? 'رصيدك ما يكفي! ارهن أو اهدم حتى تسدد'
            : (debtor ? debtor.name : 'لاعب') + ' عم يسدد دين...';
    } else if (gameState.pendingPurchase) {
        const decider = myPlayers.find(p => p.id === gameState.pendingPurchase.playerId);
        statusEl.textContent = gameState.pendingPurchase.playerId === myPlayerId
            ? 'قرر: تشتري ولا تفتح مزاد؟'
            : (decider ? decider.name : 'لاعب') + ' عم يقرر...';
    } else if (gameState.auction) {
        statusEl.textContent = 'في مزاد جاري';
    } else if (gameState.currentTurnPlayerId === myPlayerId && iAmJailed) {
        statusEl.textContent = 'إنت بالمسكوبية';
    } else if (isMyTurn) {
        statusEl.textContent = 'دورك! ارمِ النرد';
    } else {
        const currentPlayer = myPlayers.find(p => p.id === gameState.currentTurnPlayerId);
        const jailedNote = gameState.inJail && gameState.inJail[gameState.currentTurnPlayerId] ? ' (بالمسكوبية)' : '';
        statusEl.textContent = currentPlayer ? 'دور ' + currentPlayer.name + jailedNote : '...';
    }

    highlightCurrentTurn();
    renderTokens();
    renderOwnershipStamps();
    renderAuctionModal();

    // نتيجة الوقوف على الخانة (بطاقة/شراء/دين/مسكوبية) بتستنى لحد ما توصل
    // القطعة فعليًا + نص ثانية زيادة، حتى ما تطلع النافذة قبل ما اللاعب
    // يشوف القطعة نفسها وصلت. أي استدعاء تاني (رد فعل مباشر على ضغطة
    // المستخدم، مش نتيجة حركة) بيوصل actionDelayMs=0 فبيترسم فورًا زي العادة.
    const showLandingActions = () => {
        renderPurchaseModal();
        renderJailModal();
        renderDebtModal();
        renderCardAnnouncement();
    };
    if (actionDelayMs > 0) {
        setTimeout(showLandingActions, actionDelayMs);
    } else {
        showLandingActions();
    }

    renderNegotiateButton();
    renderNegotiationStatus();
    renderExtendVoteModal();
    startTurnCountdown();
    startMatchTimer();

    if (gameState.ended) {
        showGameOver();
    }
    renderEndVoteModal();
}

// ---------- قرار الشراء ----------
function renderPurchaseModal() {
    const modal = document.getElementById('buy-decision-modal');
    const pp = gameState.pendingPurchase;
    if (!pp || pp.playerId !== myPlayerId) {
        modal.classList.add('hidden');
        return;
    }
    const sq = squaresByPosition[pp.position];
    document.getElementById('buy-decision-title').textContent = sq ? sq.name : '';
    document.getElementById('buy-decision-price').textContent = sq ? ('السعر: ₪' + sq.price) : '';
    document.getElementById('buy-now-btn').disabled = !sq || myBalance < sq.price;
    modal.classList.remove('hidden');
}

function setupPurchaseActions() {
    document.getElementById('buy-now-btn').addEventListener('click', async () => {
        await postAction('/purchase/buy', { playerId: myPlayerId });
    });
    document.getElementById('open-auction-btn').addEventListener('click', async () => {
        await postAction('/purchase/auction', { playerId: myPlayerId });
    });
}

// ---------- المزاد ----------
function renderAuctionModal() {
    const modal = document.getElementById('auction-modal');
    const a = gameState.auction;
    if (!a) {
        modal.classList.add('hidden');
        clearInterval(auctionCountdownInterval);
        return;
    }
    modal.classList.remove('hidden');
    const sq = squaresByPosition[a.position];
    document.getElementById('auction-title').textContent = 'مزاد: ' + (sq ? sq.name : '');
    const currentPrice = a.currentBid != null ? a.currentBid : a.openingPrice;
    document.getElementById('auction-current-price').textContent = 'السعر الحالي: ₪' + currentPrice;

    const nextBid = (a.currentBid != null ? a.currentBid : a.openingPrice) + 10;
    document.getElementById('auction-next-bid').textContent = nextBid;

    const bidder = a.currentBidderId ? myPlayers.find(p => p.id === a.currentBidderId) : null;
    document.getElementById('auction-status').textContent = bidder ? ('أعلى مزايدة: ' + bidder.name) : 'ما زاود حدا لسا';

    const isOpener = a.openerPlayerId === myPlayerId;
    const bidBtn = document.getElementById('auction-bid-btn');
    bidBtn.style.display = isOpener ? 'none' : '';
    bidBtn.disabled = myBalance < nextBid;

    startAuctionCountdown(a.deadlineEpochMs);
}

function startAuctionCountdown(deadlineEpochMs) {
    clearInterval(auctionCountdownInterval);
    const timerEl = document.getElementById('auction-timer');
    const tick = () => {
        const remaining = Math.max(0, Math.ceil((deadlineEpochMs - Date.now()) / 1000));
        timerEl.textContent = remaining + 'ث';
        timerEl.classList.toggle('urgent', remaining <= 3);
        if (remaining <= 0) clearInterval(auctionCountdownInterval);
    };
    tick();
    auctionCountdownInterval = setInterval(tick, 250);
}

function setupAuctionActions() {
    document.getElementById('auction-bid-btn').addEventListener('click', async () => {
        const a = gameState.auction;
        if (!a) return;
        const nextBid = (a.currentBid != null ? a.currentBid : a.openingPrice) + 10;
        await postAction('/auction/bid', { playerId: myPlayerId, amount: nextBid });
    });
}

async function postAction(path, body) {
    try {
        const res = await fetch('/api/games/' + roomCode + path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showToast(data.message || 'ما قدرت أكمل الإجراء', true);
            // ممكن يكون صار حل تلقائي (متل انتهاء مهلة الدين) بنفس لحظة
            // ما ضغطت الزر - نتأكد إننا شايفين الحالة الحقيقية الحالية.
            refreshGameState();
            return null;
        }
        return data;
    } catch (e) {
        console.error(e);
        return null;
    }
}

// ---------- المسكوبية ----------
function renderJailModal() {
    const modal = document.getElementById('jail-modal');
    const iAmJailed = !!(gameState.inJail && gameState.inJail[myPlayerId]);
    const isMyTurn = gameState.currentTurnPlayerId === myPlayerId;
    if (!iAmJailed || !isMyTurn || gameState.ended) {
        modal.classList.add('hidden');
        return;
    }
    modal.classList.remove('hidden');

    const attempts = (gameState.jailAttempts && gameState.jailAttempts[myPlayerId]) || 0;
    document.getElementById('jail-attempts-text').textContent =
        'حاولت ' + attempts + ' من 3 محاولات. بعد 3 محاولات فاشلة بتدفع ₪50 وتطلع تلقائيًا.';

    const cards = (gameState.jailFreeCards && gameState.jailFreeCards[myPlayerId]) || 0;
    const cardBtn = document.getElementById('jail-card-btn');
    if (cards > 0) {
        cardBtn.classList.remove('hidden');
        cardBtn.textContent = 'استخدم بطاقة شحرور (عندك ' + cards + ')';
    } else {
        cardBtn.classList.add('hidden');
    }

    document.getElementById('jail-pay-btn').disabled = myBalance < 50;
    document.getElementById('jail-roll-btn').disabled = false;
}

function setupJailActions() {
    document.getElementById('jail-pay-btn').addEventListener('click', async () => {
        const result = await postAction('/jail/pay', { playerId: myPlayerId });
        if (result) { gameState = result; renderTurnUI(); refreshMyBalance(); }
    });
    document.getElementById('jail-card-btn').addEventListener('click', async () => {
        const result = await postAction('/jail/use-card', { playerId: myPlayerId });
        if (result) { gameState = result; renderTurnUI(); refreshMyBalance(); }
    });
    document.getElementById('jail-roll-btn').addEventListener('click', performRoll);
}

// ---------- الدين المعلق (رصيد غير كافٍ) ----------
let debtCountdownInterval = null;

function renderDebtModal() {
    const modal = document.getElementById('debt-modal');
    const debt = gameState.pendingDebt;
    if (!debt || debt.playerId !== myPlayerId) {
        modal.classList.add('hidden');
        clearInterval(debtCountdownInterval);
        return;
    }
    modal.classList.remove('hidden');

    const remaining = Math.max(0, debt.amountOwed - myBalance);
    document.getElementById('debt-amount').textContent = '₪' + debt.amountOwed;
    document.getElementById('debt-balance').textContent = '₪' + myBalance;
    document.getElementById('debt-remaining').textContent = '₪' + remaining;
    document.getElementById('debt-pay-btn').disabled = myBalance < debt.amountOwed;

    renderDebtMortgageList();
    renderDebtDemolishList();

    clearInterval(debtCountdownInterval);
    const timerEl = document.getElementById('debt-timer');
    const tick = () => {
        const secs = Math.max(0, Math.ceil((debt.deadlineEpochMs - Date.now()) / 1000));
        timerEl.textContent = secs + 'ث';
        timerEl.classList.toggle('urgent', secs <= 10);
    };
    tick();
    debtCountdownInterval = setInterval(tick, 250);
}

function renderDebtMortgageList() {
    const box = document.getElementById('debt-mortgage-list');
    box.innerHTML = '';
    const eligible = Object.entries(gameState.ownership || {})
        .filter(([pos, owner]) => owner === myPlayerId
            && !(gameState.mortgaged && gameState.mortgaged[pos])
            && (gameState.houses[pos] || 0) === 0)
        .map(([pos]) => squaresByPosition[pos])
        .filter(Boolean)
        .filter(sq => !groupHasAnyHousesInGroup(sq.colorGroup));

    if (eligible.length === 0) {
        box.innerHTML = '<p class="hint-text">ما في أراضي قابلة للرهن هلق.</p>';
        return;
    }
    eligible.forEach(sq => {
        const row = document.createElement('div');
        row.className = 'debt-property-row';
        row.innerHTML = '<span>' + escapeHtml(sq.name) + ' (+₪' + Math.floor(sq.price / 2) + ')</span>' +
            '<button type="button" class="wood-btn small">ارهن</button>';
        row.querySelector('button').addEventListener('click', () => debtPropertyAction(sq.position, 'mortgage'));
        box.appendChild(row);
    });
}

function isDemolishEligibleNow(position) {
    const sq = squaresByPosition[position];
    if (!sq) return false;
    const houses = gameState.houses[position] || 0;
    if (houses <= 0) return false;
    const maxInGroup = Object.values(squaresByPosition)
        .filter(s => s.colorGroup === sq.colorGroup)
        .reduce((max, s) => Math.max(max, gameState.houses[s.position] || 0), 0);
    return houses >= maxInGroup;
}

function renderDebtDemolishList() {
    const box = document.getElementById('debt-demolish-list');
    box.innerHTML = '';
    const eligible = Object.entries(gameState.ownership || {})
        .filter(([pos, owner]) => owner === myPlayerId && isDemolishEligibleNow(Number(pos)))
        .map(([pos]) => squaresByPosition[pos])
        .filter(Boolean);

    if (eligible.length === 0) {
        box.innerHTML = '<p class="hint-text">ما في دور تقدر تهدمها هلق.</p>';
        return;
    }
    eligible.forEach(sq => {
        const cost = GROUP_HOUSE_COST[sq.colorGroup] || 0;
        const row = document.createElement('div');
        row.className = 'debt-property-row';
        row.innerHTML = '<span>' + escapeHtml(sq.name) + ' (+₪' + Math.floor(cost / 2) + ')</span>' +
            '<button type="button" class="wood-btn small">اهدم</button>';
        row.querySelector('button').addEventListener('click', () => debtPropertyAction(sq.position, 'demolish'));
        box.appendChild(row);
    });
}

async function debtPropertyAction(position, action) {
    if (action === 'demolish' && !confirm('متأكد بدك تهدم دار من هاي الأرض؟ ما فيها رجوع.')) return;
    const result = await postAction('/properties/' + position + '/' + action, { playerId: myPlayerId });
    if (result) {
        gameState = result;
        renderTurnUI();
        refreshMyBalance();
    }
}

function setupDebtActions() {
    document.getElementById('debt-pay-btn').addEventListener('click', async () => {
        const result = await postAction('/debt/pay', { playerId: myPlayerId });
        if (result) { gameState = result; renderTurnUI(); refreshMyBalance(); }
    });
    document.getElementById('debt-bankrupt-btn').addEventListener('click', async () => {
        if (!confirm('متأكد بدك تعلن إفلاسك؟ رح تخسر كل أملاكك وتطلع من اللعبة.')) return;
        const result = await postAction('/debt/bankrupt', { playerId: myPlayerId });
        if (result) { gameState = result; renderTurnUI(); refreshMyBalance(); }
    });
}

// ---------- التفاوض ----------
function renderNegotiateButton() {
    const btn = document.getElementById('negotiate-btn');
    const canOpen = gameState.canNegotiate
        && gameState.currentTurnPlayerId === myPlayerId
        && !gameState.hasRolledThisTurn
        && !gameState.negotiationUsedThisTurn
        && !gameState.negotiation
        && !gameState.pendingPurchase
        && !gameState.auction
        && !gameState.ended;
    btn.classList.toggle('hidden', !gameState.canNegotiate);
    btn.disabled = !canOpen;
}

function openNegotiateCompose() {
    const select = document.getElementById('neg-counterpart');
    select.innerHTML = '';
    myPlayers.filter(p => p.id !== myPlayerId && p.connected).forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = (PIECE_ICONS[p.piece] || '') + ' ' + p.name;
        select.appendChild(opt);
    });

    renderMyOfferProperties();
    renderRequestProperties();

    const offerCashInput = document.getElementById('neg-offer-cash');
    offerCashInput.value = 0;
    offerCashInput.max = myBalance;
    document.getElementById('neg-offer-cash-hint').textContent = 'أقصى مبلغ: ₪' + myBalance;

    document.getElementById('neg-request-cash').value = 0;
    document.getElementById('negotiate-error').textContent = '';
    document.getElementById('negotiate-compose-modal').classList.remove('hidden');
}

function renderMyOfferProperties() {
    const box = document.getElementById('neg-offer-properties');
    box.innerHTML = '';
    myTradableProperties(myPlayerId).forEach(sq => {
        box.appendChild(propertyCheckbox('neg-offer-prop-' + sq.position, sq));
    });
}

function renderRequestProperties() {
    const counterpartId = document.getElementById('neg-counterpart').value;
    const counterpart = myPlayers.find(p => p.id === counterpartId);
    document.getElementById('neg-request-title').textContent = counterpart ? 'مقابل من ' + counterpart.name : 'مقابل';
    const box = document.getElementById('neg-request-properties');
    box.innerHTML = '';
    myTradableProperties(counterpartId).forEach(sq => {
        box.appendChild(propertyCheckbox('neg-request-prop-' + sq.position, sq));
    });
}

function myTradableProperties(ownerId) {
    if (!gameState.ownership) return [];
    return Object.entries(gameState.ownership)
        .filter(([pos, owner]) => owner === ownerId && (gameState.houses[pos] || 0) === 0)
        .map(([pos]) => squaresByPosition[pos])
        .filter(Boolean);
}

function propertyCheckbox(id, sq) {
    const label = document.createElement('label');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.id = id;
    checkbox.dataset.position = sq.position;
    label.appendChild(checkbox);
    label.append(' ' + sq.name + ' (₪' + sq.price + ')');
    return label;
}

function setupNegotiationActions() {
    document.getElementById('negotiate-btn').addEventListener('click', openNegotiateCompose);
    document.getElementById('close-negotiate-compose').addEventListener('click', () => {
        document.getElementById('negotiate-compose-modal').classList.add('hidden');
    });
    document.getElementById('neg-counterpart').addEventListener('change', renderRequestProperties);

    document.getElementById('neg-offer-cash').addEventListener('input', function () {
        if (Number(this.value) > myBalance) this.value = myBalance;
        if (Number(this.value) < 0) this.value = 0;
    });

    document.getElementById('neg-send-btn').addEventListener('click', async () => {
        const counterpartId = document.getElementById('neg-counterpart').value;
        if (!counterpartId) {
            document.getElementById('negotiate-error').textContent = 'ما في لاعب تاني متصل تتفاوض معه هلأ';
            return;
        }
        const offerProperties = checkedPositions('neg-offer-properties');
        const requestProperties = checkedPositions('neg-request-properties');
        const offerCash = Number(document.getElementById('neg-offer-cash').value) || 0;
        const requestCash = Number(document.getElementById('neg-request-cash').value) || 0;

        const result = await postAction('/negotiation/propose', {
            playerId: myPlayerId,
            counterpartId,
            offerCash, offerProperties,
            requestCash, requestProperties
        });
        if (result) {
            gameState = result;
            document.getElementById('negotiate-compose-modal').classList.add('hidden');
            renderTurnUI();
        }
    });

    document.getElementById('neg-accept-btn').addEventListener('click', () => respondNegotiation(true));
    document.getElementById('neg-reject-btn').addEventListener('click', () => respondNegotiation(false));
    document.getElementById('neg-cancel-btn').addEventListener('click', async () => {
        const result = await postAction('/negotiation/cancel', { playerId: myPlayerId });
        if (result) { gameState = result; renderTurnUI(); }
    });
}

function checkedPositions(containerId) {
    return Array.from(document.querySelectorAll('#' + containerId + ' input:checked'))
        .map(el => Number(el.dataset.position));
}

async function respondNegotiation(accept) {
    const result = await postAction('/negotiation/respond', { playerId: myPlayerId, accept });
    if (result) { gameState = result; renderTurnUI(); refreshMyBalance(); }
}

function describeBundle(cash, properties) {
    const parts = [];
    if (cash > 0) parts.push('₪' + cash);
    properties.forEach(pos => {
        const sq = squaresByPosition[pos];
        if (sq) parts.push(sq.name);
    });
    return parts.length ? parts.join('، ') : 'ولا شي';
}

function renderNegotiationStatus() {
    const modal = document.getElementById('negotiate-incoming-modal');
    const n = gameState.negotiation;
    if (!n || (n.initiatorId !== myPlayerId && n.counterpartId !== myPlayerId)) {
        modal.classList.add('hidden');
        clearInterval(negotiationCountdownInterval);
        return;
    }
    modal.classList.remove('hidden');

    const initiator = myPlayers.find(p => p.id === n.initiatorId);
    const counterpart = myPlayers.find(p => p.id === n.counterpartId);
    const initiatorName = initiator ? initiator.name : 'لاعب';
    const counterpartName = counterpart ? counterpart.name : 'لاعب';
    const isInitiator = n.initiatorId === myPlayerId;

    document.getElementById('neg-incoming-title').textContent =
        initiatorName + ' ⇄ ' + counterpartName + (isInitiator ? ' (بانتظار الرد)' : '');

    // صيغة مطلقة دايمًا من منظور المُبادر، بغض النظر مين شايف النافذة،
    // حتى ما يصير لبس بـ"بتاخد/بتدي" النسبي.
    document.getElementById('neg-incoming-get').textContent =
        initiatorName + ' بيدي: ' + describeBundle(n.offerCash, n.offerProperties);
    document.getElementById('neg-incoming-give').textContent =
        initiatorName + ' بيطلب من ' + counterpartName + ': ' + describeBundle(n.requestCash, n.requestProperties);

    document.getElementById('neg-respond-actions').classList.toggle('hidden', isInitiator);
    document.getElementById('neg-cancel-btn').classList.toggle('hidden', !isInitiator);

    const acceptBtn = document.getElementById('neg-accept-btn');
    if (!isInitiator) {
        const canAfford = myBalance >= n.requestCash;
        acceptBtn.disabled = !canAfford;
        acceptBtn.title = canAfford ? '' : 'رصيدك ما يكفي تقبل هاد العرض (لازم ₪' + n.requestCash + ')';
    }

    clearInterval(negotiationCountdownInterval);
    const timerEl = document.getElementById('neg-incoming-timer');
    const tick = () => {
        const remaining = Math.max(0, Math.ceil((n.deadlineEpochMs - Date.now()) / 1000));
        timerEl.textContent = remaining + 'ث';
        timerEl.classList.toggle('urgent', remaining <= 5);
    };
    tick();
    negotiationCountdownInterval = setInterval(tick, 250);
}
function renderCardAnnouncement() {
    if (!gameState.lastCardText || gameState.lastCardText === lastSeenCard) return;
    lastSeenCard = gameState.lastCardText;

    document.getElementById('card-deck-title').textContent = CARD_DECK_TITLES[gameState.lastCardDeck] || 'بطاقة';
    document.getElementById('card-text').textContent = gameState.lastCardText;

    const modal = document.getElementById('card-modal');
    modal.classList.remove('hidden');
    clearTimeout(cardHideTimeout);
    cardHideTimeout = setTimeout(() => modal.classList.add('hidden'), 4000);
}

function showGameOver() {
    document.getElementById('gameover-modal').classList.remove('hidden');
    renderResults();
}

function renderResults() {
    const values = gameState.finalValues || {};
    const winnerId = gameState.winnerId;
    const banner = document.getElementById('winner-banner');

    if (winnerId) {
        const winner = myPlayers.find(p => p.id === winnerId);
        banner.innerHTML = '<span class="trophy">🏆</span>' +
            '<span class="winner-name">' + (winner ? escapeHtml(winner.name) : '') + '</span>' +
            '<span class="winner-value">القيمة الإجمالية: ₪' + (values[winnerId] || 0) + '</span>';
    } else {
        banner.innerHTML = '<span class="trophy">🤝</span><span class="winner-name">تعادل - بدون فائز</span>';
    }

    const sorted = myPlayers.slice().sort((a, b) => (values[b.id] || 0) - (values[a.id] || 0));
    const medals = ['🥇', '🥈', '🥉'];
    const list = document.getElementById('results-list');
    list.innerHTML = '';
    sorted.forEach((p, i) => {
        const li = document.createElement('li');
        li.innerHTML = '<span class="medal">' + (medals[i] || (i + 1)) + '</span>' +
            '<span class="player-piece">' + (PIECE_ICONS[p.piece] || '●') + '</span>' +
            '<span>' + escapeHtml(p.name) + (p.id === myPlayerId ? ' (أنت)' : '') + '</span>' +
            '<span class="res-value">₪' + (values[p.id] || 0) + '</span>';
        list.appendChild(li);
    });
}

function startTurnCountdown() {
    clearInterval(countdownInterval);
    if (!gameState || gameState.ended) return;
    const tick = () => {
        const remaining = Math.max(0, Math.round((gameState.turnDeadlineEpochMs - Date.now()) / 1000));
        const statusEl = document.getElementById('turn-status');
        if (gameState.currentTurnPlayerId === myPlayerId) {
            statusEl.textContent = 'دورك! ارمِ النرد (' + remaining + 'ث)';
        }
    };
    tick();
    countdownInterval = setInterval(tick, 1000);
}

let matchTimerInterval = null;

function startMatchTimer() {
    clearInterval(matchTimerInterval);
    const el = document.getElementById('match-timer');
    if (!gameState || !gameState.matchEndDeadlineEpochMs || !el) return;

    const tick = () => {
        if (gameState.ended) { clearInterval(matchTimerInterval); return; }
        const remaining = Math.max(0, Math.floor((gameState.matchEndDeadlineEpochMs - Date.now()) / 1000));
        const mm = String(Math.floor(remaining / 60)).padStart(2, '0');
        const ss = String(remaining % 60).padStart(2, '0');
        el.textContent = '⏱ ' + mm + ':' + ss;
        el.classList.toggle('urgent', remaining <= 60);
        if (remaining <= 0) clearInterval(matchTimerInterval);
    };
    tick();
    matchTimerInterval = setInterval(tick, 1000);
}

async function refreshGameState() {
    try {
        const res = await fetch('/api/games/' + roomCode);
        if (!res.ok) return;
        gameState = await res.json();
        if (lastSeenCard === null) {
            // أول تحميل/عودة: منثبّت آخر بطاقة معروفة بصمت، حتى ما تنعرض
            // كأنها بطاقة جديدة رغم إنها ممكن تكون قديمة من قبل ما رجعنا.
            lastSeenCard = gameState.lastCardText;
        }
        const actionDelayMs = detectAndRenderLastLanded(gameState.positions);
        renderTurnUI(actionDelayMs);
    } catch (e) { console.error(e); }
}

async function refreshMyBalance() {
    try {
        const res = await fetch('/api/games/' + roomCode + '/balance/' + myPlayerId);
        if (!res.ok) return;
        const data = await res.json();

        if (lastKnownBalance !== null && data.balance !== lastKnownBalance) {
            showBalanceChange(data.balance - lastKnownBalance);
        }
        lastKnownBalance = data.balance;

        myBalance = data.balance;
        document.getElementById('my-balance').textContent = '₪' + data.balance;
        if (gameState) {
            if (gameState.pendingPurchase) renderPurchaseModal();
            if (gameState.auction) renderAuctionModal();
            if (gameState.negotiation) renderNegotiationStatus();
            if (gameState.inJail && gameState.inJail[myPlayerId]) renderJailModal();
            if (gameState.pendingDebt) renderDebtModal();
        }
    } catch (e) { console.error(e); }
}

function showBalanceChange(delta) {
    if (!delta) return;
    if (delta > 0) playCashSound(); else playPaySound();
    const anchor = document.getElementById('my-balance-wrap');
    if (!anchor) return;
    const el = document.createElement('div');
    el.className = 'balance-change ' + (delta > 0 ? 'positive' : 'negative');
    el.textContent = (delta > 0 ? '+' : '') + delta;
    anchor.appendChild(el);
    setTimeout(() => el.remove(), 1800);
}

// ---------- تصويت إنهاء اللعبة ----------
function setupEndVote() {
    document.getElementById('end-game-btn').addEventListener('click', async () => {
        try {
            const res = await fetch('/api/games/' + roomCode + '/end-vote/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ playerId: myPlayerId })
            });
            if (res.ok) gameState = await res.json();
            renderEndVoteModal();
        } catch (e) { console.error(e); }
    });

    document.getElementById('endvote-approve').addEventListener('click', () => respondEndVote(true));
    document.getElementById('endvote-reject').addEventListener('click', () => respondEndVote(false));
}

async function respondEndVote(approve) {
    try {
        const res = await fetch('/api/games/' + roomCode + '/end-vote/respond', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId: myPlayerId, approve })
        });
        if (res.ok) gameState = await res.json();
        renderEndVoteModal();
    } catch (e) { console.error(e); }
}

function renderEndVoteModal() {
    const modal = document.getElementById('endvote-modal');
    if (!gameState || !gameState.voteActive) {
        modal.classList.add('hidden');
        return;
    }
    modal.classList.remove('hidden');

    const list = document.getElementById('endvote-list');
    list.innerHTML = '';
    myPlayers.filter(p => p.connected).forEach(p => {
        const approved = gameState.voteResponses[p.id] === true;
        const li = document.createElement('li');
        li.className = 'player-item';
        li.innerHTML = '<span class="player-piece">' + (PIECE_ICONS[p.piece] || '●') + '</span>' +
            '<span class="player-name">' + escapeHtml(p.name) + '</span>' +
            '<span class="host-badge">' + (approved ? '✅ موافق' : '⏳ بانتظار') + '</span>';
        list.appendChild(li);
    });

    const myVoteDone = gameState.voteResponses[myPlayerId] === true;
    document.getElementById('endvote-approve').style.display = myVoteDone ? 'none' : '';
    document.getElementById('endvote-reject').style.display = myVoteDone ? 'none' : '';
}

function setupRulesButton() {
    const modal = document.getElementById('rules-modal');
    document.querySelectorAll('[data-open-rules]').forEach(btn => {
        btn.addEventListener('click', () => modal.classList.remove('hidden'));
    });
    document.getElementById('close-rules').addEventListener('click', () => modal.classList.add('hidden'));
    modal.addEventListener('click', (e) => {
        if (e.target.id === 'rules-modal') modal.classList.add('hidden');
    });
}

function setupGameOverButton() {
    document.getElementById('gameover-home-btn').addEventListener('click', () => {
        document.getElementById('gameover-modal').classList.add('hidden');
        if (window.returnToLandingAfterGame) window.returnToLandingAfterGame();
    });
}

// ---------- تصويت تمديد وقت المباراة (بعد 60 دقيقة) ----------
let extendVoteCountdownInterval = null;

function renderExtendVoteModal() {
    const modal = document.getElementById('extend-vote-modal');
    if (!gameState.extendVoteActive || gameState.ended) {
        modal.classList.add('hidden');
        clearInterval(extendVoteCountdownInterval);
        return;
    }
    modal.classList.remove('hidden');

    const list = document.getElementById('extend-vote-list');
    list.innerHTML = '';
    myPlayers.filter(p => p.connected).forEach(p => {
        const approved = gameState.extendVoteResponses[p.id] === true;
        const li = document.createElement('li');
        li.className = 'player-item';
        li.innerHTML = '<span class="player-piece">' + (PIECE_ICONS[p.piece] || '●') + '</span>' +
            '<span class="player-name">' + escapeHtml(p.name) + '</span>' +
            '<span class="host-badge">' + (approved ? '✅ موافق' : '⏳ بانتظار') + '</span>';
        list.appendChild(li);
    });

    const myVoteDone = gameState.extendVoteResponses[myPlayerId] === true;
    document.getElementById('extend-vote-actions').classList.toggle('hidden', myVoteDone);

    clearInterval(extendVoteCountdownInterval);
    const timerEl = document.getElementById('extend-vote-timer');
    const tick = () => {
        const remaining = Math.max(0, Math.ceil((gameState.extendVoteDeadlineEpochMs - Date.now()) / 1000));
        timerEl.textContent = remaining + 'ث';
        timerEl.classList.toggle('urgent', remaining <= 5);
    };
    tick();
    extendVoteCountdownInterval = setInterval(tick, 250);
}

function setupExtendVoteActions() {
    document.getElementById('extend-approve-btn').addEventListener('click', () => respondExtend(true));
    document.getElementById('extend-reject-btn').addEventListener('click', () => respondExtend(false));
}

async function respondExtend(approve) {
    const result = await postAction('/extend-vote/respond', { playerId: myPlayerId, approve });
    if (result) { gameState = result; renderTurnUI(); }
}

// ---------- التقييم بعد نهاية اللعبة ----------
let selectedStars = 0;
let selectedRatingType = 'NOTE';

function setupRatingModal() {
    document.getElementById('gameover-rate-btn').addEventListener('click', () => {
        document.getElementById('gameover-modal').classList.add('hidden');
        document.getElementById('rating-modal').classList.remove('hidden');
    });

    document.querySelectorAll('#rating-stars span').forEach(star => {
        star.addEventListener('click', () => {
            selectedStars = Number(star.dataset.star);
            document.querySelectorAll('#rating-stars span').forEach(s => {
                s.classList.toggle('selected', Number(s.dataset.star) <= selectedStars);
            });
        });
    });

    document.querySelectorAll('#rating-type-picker button').forEach(btn => {
        btn.addEventListener('click', () => {
            selectedRatingType = btn.dataset.type;
            document.querySelectorAll('#rating-type-picker button').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
        });
    });

    document.getElementById('rating-submit-btn').addEventListener('click', async () => {
        try {
            await fetch('/api/ratings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    roomCode: roomCode,
                    stars: selectedStars || 5,
                    type: selectedRatingType,
                    text: document.getElementById('rating-text').value
                })
            });
        } catch (e) { console.error(e); }
        finishRating();
    });

    document.getElementById('rating-skip-btn').addEventListener('click', finishRating);
}

function finishRating() {
    document.getElementById('rating-modal').classList.add('hidden');
    if (window.returnToLandingAfterGame) window.returnToLandingAfterGame();
}

setupModalClose();
setupDiceRoll();
setupPurchaseActions();
setupAuctionActions();
setupJailActions();
setupDebtActions();
setupNegotiationActions();
setupEndVote();
setupExtendVoteActions();
setupRulesButton();
setupGameOverButton();
setupRatingModal();

document.getElementById('card-modal').addEventListener('click', (e) => {
    if (e.target.id === 'card-modal') {
        e.currentTarget.classList.add('hidden');
        clearTimeout(cardHideTimeout);
    }
});

// ---------- كشف حركة اللاعبين (لتحريك القطع بالأنيميشن) ----------
const POST_ARRIVAL_BUFFER_MS = 500; // نص ثانية إضافية بعد ما توصل القطعة، قبل ما نظهر نتيجة الوقوف

function detectAndRenderLastLanded(positions) {
    if (!positions) return 0;
    let maxDuration = 0;
    if (previousPositions) {
        for (const pid in positions) {
            if (positions[pid] !== previousPositions[pid]) {
                const JAIL_POSITION = 10;
                const sentToJail = positions[pid] === JAIL_POSITION
                    && gameState.inJail && gameState.inJail[pid];
                const duration = animateTokenMovement(pid, previousPositions[pid], positions[pid], sentToJail);
                maxDuration = Math.max(maxDuration, duration);
            }
        }
    }
    previousPositions = Object.assign({}, positions);
    return maxDuration > 0 ? maxDuration + POST_ARRIVAL_BUFFER_MS : 0;
}

// ---------- سجل الأحداث (تحت لوحة اللاعبين) ----------
function renderEventLog() {
    const el = document.getElementById('event-log-content');
    if (!el || !gameState || !gameState.eventLog) return;
    const entries = gameState.eventLog.slice().reverse(); // الأحدث فوق
    if (entries.length === 0) {
        el.innerHTML = '<div class="log-entry">—</div>';
        return;
    }
    el.innerHTML = entries.map(e => '<div class="log-entry">' + escapeHtml(e) + '</div>').join('');
}

// room.js بينادي هاي الدالة لما تبلش اللعبة فعليًا (بعد ما المضيف يدوس "بدء اللعبة")
window.startBoardGame = function (players, playerId, code) {
    myPlayerId = playerId;
    roomCode = code;
    renderRealPlayers(players, playerId);
    loadBoard().then(() => {
        refreshGameState();
        refreshMyBalance();
    });
};

// room.js بينادي هاي كل ما توصل حالة لعبة جديدة عبر WebSocket
window.onGameStateUpdate = function (state) {
    gameState = state;
    const actionDelayMs = detectAndRenderLastLanded(state.positions);
    renderTurnUI(actionDelayMs);
    if (actionDelayMs > 0) {
        setTimeout(refreshMyBalance, actionDelayMs);
    } else {
        refreshMyBalance();
    }
};

// room.js بينادي هاي لما تتحدث حالة الغرفة (اتصال/انقطاع) أثناء اللعب
window.onRoomUpdate = function (room) {
    renderRealPlayers(room.players, myPlayerId);
};