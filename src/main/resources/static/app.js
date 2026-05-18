document.addEventListener('DOMContentLoaded', () => {
    loadSessions();
    addTagRow("35", "D"); // Add default MsgType=NewOrderSingle to the builder
    startMessagePolling();
});

let activeSessions = [];
let messagePollInterval;
let lastMessageId = 0;
let sessionColorMap = {};
let colorIndex = 0;

const sessionColors = [
    { in: 'table-primary', out: 'table-info' },
    { in: 'table-success', out: 'table-warning' },
    { in: 'table-danger', out: 'table-secondary' },
    { in: 'table-light', out: 'table-active' }
];

function startMessagePolling() {
    if (messagePollInterval) clearInterval(messagePollInterval);
    messagePollInterval = setInterval(fetchMessages, 1000);
}

async function fetchMessages() {
    try {
        const res = await fetch('/api/sessions/messages?since=' + lastMessageId);
        const messages = await res.json();
        
        if (messages.length === 0) return;
        
        const tbody = document.querySelector('#messageLogTable tbody');
        let addedNew = false;

        messages.forEach(msg => {
            if (msg.id > lastMessageId) {
                lastMessageId = msg.id;
            }
            
            if (!sessionColorMap[msg.session]) {
                sessionColorMap[msg.session] = sessionColors[colorIndex % sessionColors.length];
                colorIndex++;
            }
            
            const colorClass = msg.direction === 'IN' 
                ? sessionColorMap[msg.session].in 
                : sessionColorMap[msg.session].out;

            const time = new Date(msg.timestamp).toLocaleTimeString();
            
            const tr = document.createElement('tr');
            tr.className = colorClass;
            tr.innerHTML = `
                <td class="text-nowrap">${time}</td>
                <td class="text-nowrap">${msg.session}</td>
                <td>${msg.direction}</td>
                <td style="word-break: break-all; font-family: monospace; font-size: 0.85em;">${msg.message}</td>
            `;
            tbody.appendChild(tr);
            addedNew = true;
        });
        
        if (addedNew) {
            const container = document.getElementById('messageLogContainer');
            container.scrollTop = container.scrollHeight;
        }
        
    } catch (e) {
        console.error("Failed to fetch messages", e);
    }
}

async function loadSessions() {
    try {
        const res = await fetch('/api/sessions');
        activeSessions = await res.json();
        
        const tbody = document.querySelector('#sessionTable tbody');
        const selects = [document.getElementById('sendSessionSelect'), document.getElementById('replaySessionSelect')];
        
        tbody.innerHTML = '';
        selects.forEach(sel => sel.innerHTML = '');

        activeSessions.forEach((session, i) => {
            // Populate Table
            const isLogged = session.isLoggedOn;
            const badge = isLogged ? '<span class="badge bg-success">Logged On</span>' : '<span class="badge bg-danger">Disconnected</span>';
            
            tbody.innerHTML += `
                <tr>
                    <td class="align-middle" style="font-family: monospace;">${session.sessionString}</td>
                    <td class="align-middle">${badge}</td>
                    <td class="align-middle">
                        <input type="number" class="form-control form-control-sm px-1" id="inSeqInput-${i}" value="${session.inSeq}" style="width: 80px;" ${isLogged ? 'disabled' : ''}>
                    </td>
                    <td class="align-middle">
                        <input type="number" class="form-control form-control-sm px-1" id="outSeqInput-${i}" value="${session.outSeq}" style="width: 80px;" ${isLogged ? 'disabled' : ''}>
                    </td>
                    <td class="align-middle">
                        <div class="btn-group btn-group-sm">
                            <button class="btn btn-outline-secondary" onclick="setSequence('${session.sessionString}', ${i})" title="Set sequence numbers" ${isLogged ? 'disabled' : ''}>Set</button>
                            <button class="btn btn-outline-primary" onclick="sessionAction('${session.sessionString}', 'logon')" ${isLogged ? 'disabled' : ''}>Logon</button>
                            <button class="btn btn-outline-warning" onclick="sessionAction('${session.sessionString}', 'logout')" ${!isLogged ? 'disabled' : ''}>Logout</button>
                            <button class="btn btn-outline-danger" onclick="sessionAction('${session.sessionString}', 'reset')" title="Reset sequence numbers to 1" ${isLogged ? 'disabled' : ''}>Reset</button>
                        </div>
                    </td>
                </tr>
            `;

            // Populate Dropdowns
            const opt = new Option(session.sessionString, session.sessionString);
            selects.forEach(sel => sel.add(opt.cloneNode(true)));
        });
    } catch (err) {
        console.error('Failed to load sessions', err);
    }
}

async function setSequence(sessionString, index) {
    const inSeqEl = document.getElementById(`inSeqInput-${index}`);
    const outSeqEl = document.getElementById(`outSeqInput-${index}`);

    const payload = {};
    if (inSeqEl.value) payload.inSeq = parseInt(inSeqEl.value, 10);
    if (outSeqEl.value) payload.outSeq = parseInt(outSeqEl.value, 10);

    if (Object.keys(payload).length === 0) {
        return alert("Please provide at least one sequence number to set.");
    }

    try {
        const res = await fetch(`/api/sessions/${encodeURIComponent(sessionString)}/setseq`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const responseText = await res.text();
        alert(responseText);
        if (res.ok) {
            setTimeout(loadSessions, 200);
        }
    } catch (e) {
        alert("An error occurred: " + e.message);
    }
}

async function sessionAction(sessionString, action) {
    await fetch(`/api/sessions/${encodeURIComponent(sessionString)}/${action}`, { method: 'POST' });
    setTimeout(loadSessions, 500); // give the engine time to connect/disconnect before reloading UI
}

function addTagRow(defaultTag = "", defaultValue = "") {
    const row = document.createElement('div');
    row.className = 'input-group mb-2 w-50 tag-row';
    row.innerHTML = `
        <span class="input-group-text">Tag</span>
        <input type="text" class="form-control fix-tag" placeholder="e.g. 35" value="${defaultTag}">
        <span class="input-group-text">Value</span>
        <input type="text" class="form-control fix-val" placeholder="e.g. D" value="${defaultValue}">
        <button class="btn btn-outline-danger" onclick="this.parentElement.remove()">X</button>
    `;
    document.getElementById('tagRows').appendChild(row);
}

async function sendMessage() {
    const targetSession = document.getElementById('sendSessionSelect').value;
    if (!targetSession) return alert("Select a target session first.");

    const tagMap = {};
    document.querySelectorAll('.tag-row').forEach(row => {
        const tag = row.querySelector('.fix-tag').value.trim();
        const val = row.querySelector('.fix-val').value.trim();
        if (tag && val) tagMap[tag] = val;
    });

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tagMap)
    });
    
    if (res.ok) alert("Message sent successfully.");
    else alert("Error sending message: " + await res.text());
}

async function replayMessages() {
    const targetSession = document.getElementById('replaySessionSelect').value;
    let payload;
    
    try {
        payload = JSON.parse(document.getElementById('replayJson').value);
    } catch (e) {
        return alert("Invalid JSON format in replay window.");
    }

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/replay`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    
    if (res.ok) alert(await res.text());
    else alert("Replay failed: " + await res.text());
}

async function runTests() {
    const pre = document.getElementById('testOutput');
    pre.innerText = "Running tests... Please wait.";
    
    try {
        const res = await fetch(`/api/tests/run`, { method: 'POST' });
        const output = await res.text();
        pre.innerText = output;
    } catch (e) {
        pre.innerText = "Error triggering tests: " + e;
    }
}

// Helper function to handle tab switching
function showTab(tabId, element) {
    // Hide all tab content panes and remove active class
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.style.display = 'none';
        pane.classList.remove('active');
    });
    // Remove active state from all navigation links
    document.querySelectorAll('.nav-link').forEach(link => link.classList.remove('active'));

    // Show target tab pane and set menu link as active
    const target = document.getElementById(tabId);
    if (target) {
        target.style.display = 'block';
        target.classList.add('active');
    }
    if (element) {
        element.classList.add('active');
    }
}
